package nl.hauntedmc.dungeons.runtime.staff;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import nl.hauntedmc.dungeons.content.dungeon.BranchingDungeon;
import nl.hauntedmc.dungeons.content.function.CommandFunction;
import nl.hauntedmc.dungeons.content.function.meta.DelayedFunction;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.support.*;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;
import java.util.*;

class StaffPolicyContractTest {
    @TempDir Path directory;

    @Test
    void capabilitiesRespectGlobalScopedNegativeGrants() {
        var actor = mock(CommandSender.class);
        when(actor.hasPermission("dungeons.staff.edit.crypt")).thenReturn(true);
        assertTrue(StaffPermissions.allows(actor, "edit", "crypt"));
        assertFalse(StaffPermissions.allows(actor, "edit", "other"));
        when(actor.isPermissionSet("dungeons.staff.edit")).thenReturn(true);
        assertFalse(StaffPermissions.allows(actor, "edit", "crypt"));
        when(actor.hasPermission("dungeons.staff.edit")).thenReturn(true);
        assertTrue(StaffPermissions.allows(actor, "edit", "other"));
        when(actor.isPermissionSet("dungeons.staff.edit.crypt")).thenReturn(true);
        when(actor.hasPermission("dungeons.staff.edit.crypt")).thenReturn(false);
        assertFalse(StaffPermissions.allows(actor, "edit", "crypt"));
        assertThrows(
                IllegalArgumentException.class,
                () -> StaffPermissions.require(actor, "edit", "crypt"));
        var retired = mock(CommandSender.class);
        when(retired.hasPermission("dungeons.edit.crypt")).thenReturn(true);
        when(retired.hasPermission("dungeons.admin")).thenReturn(true);
        assertFalse(StaffPermissions.allows(retired, "edit", "crypt"));
        assertFalse(StaffPermissions.allows(retired, "maintenance", "crypt"));
    }

    @Test
    void authoringRequiresMaintenanceDrainedWorldsCurrentOwnerAndSeparateScriptPower()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var state = f.player("Builder");
            var session = mock(nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession.class);
            assertFalse(StaffEditGuard.editor(state.player, session));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StaffEditGuard.configuration(f.actor, f.run.dungeon, "players.lives"));
            assertDoesNotThrow(
                    () ->
                            StaffEditGuard.configuration(
                                    f.actor, f.run.dungeon, "dungeon.display_name"));
            f.ops.maintenance(f.actor, "crypt", true, "Editing");
            f.runtime.activeInstances().registerActiveInstance(f.run.run);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StaffEditGuard.configuration(f.actor, f.run.dungeon, "players.lives"));
            f.runtime.activeInstances().unregisterActiveInstance(f.run.run);
            f.run.dungeon.setSaving(true);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StaffEditGuard.configuration(f.actor, f.run.dungeon, "players.lives"));
            f.run.dungeon.setSaving(false);
            f.denials.add("dungeons.staff.scripts");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            StaffEditGuard.configuration(
                                    f.actor, f.run.dungeon, "players.join_commands"));
            var editor = mock(EditableInstance.class);
            when(editor.getRuntime()).thenReturn(f.runtime);
            when(editor.getDungeon()).thenReturn(f.run.dungeon);
            when(editor.isEditInstance()).thenReturn(true);
            when(session.getInstance()).thenReturn(editor);
            when(session.isEditMode()).thenReturn(true);
            assertFalse(StaffEditGuard.editor(state.player, session));
            f.run.dungeon.setEditSession(editor);
            assertTrue(StaffEditGuard.editor(state.player, session));
            var delayed = new DelayedFunction();
            delayed.addFunction(new CommandFunction());
            when(session.getActiveFunction()).thenReturn(delayed);
            when(state.player.hasPermission("dungeons.staff.scripts")).thenReturn(false);
            when(state.player.isPermissionSet("dungeons.staff.scripts")).thenReturn(true);
            assertFalse(StaffEditGuard.editor(state.player, session));
            session.setInstance(null);
        }
    }

    @Test
    void persistedGraphTraversalHandlesWrappersMapsAndCyclesWithoutMissingScripts()
            throws Exception {
        try (var f = new StaffFixture(directory)) {
            var delayed = new DelayedFunction();
            var script = new CommandFunction();
            delayed.addFunction(script);
            assertTrue(ContentReferences.containsScript(delayed));
            var visited = new ArrayList<Object>();
            var cyclic = new ArrayList<Object>();
            cyclic.add(cyclic);
            cyclic.add(Map.of("function", delayed));
            cyclic.add(delayed);
            cyclic.add(null);
            ContentReferences.visit(cyclic, visited::add);
            assertEquals(1, Collections.frequency(visited, script));
            assertFalse(ContentReferences.containsScript(null));
            assertTrue(visited.contains(delayed));
        }
    }

    @Test
    void settingValidationRejectsNumericReferenceAndRelationalErrors() throws Exception {
        try (var f = new StaffFixture(directory)) {
            for (Number invalid : List.of(Double.NaN, Double.POSITIVE_INFINITY, -1))
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SettingValidation.validate(f.run.dungeon, "players.lives", invalid));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettingValidation.validate(f.run.dungeon, "team.max_size", 0));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "generator.trunk.straightness", 1.1));
            f.run.dungeon.getConfig().set("generator.range.max", 10);
            f.run.dungeon.getConfig().set("generator.range.min", 3);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettingValidation.validate(f.run.dungeon, "generator.range.min", 11));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettingValidation.validate(f.run.dungeon, "generator.range.max", 2));
            assertDoesNotThrow(
                    () -> SettingValidation.validate(f.run.dungeon, "generator.range.min", 3));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> SettingValidation.validate(f.run.dungeon, "access.cooldown.value", 24));
            f.run.dungeon.getConfig().set("access.cooldown.period", "TIMER");
            assertDoesNotThrow(
                    () -> SettingValidation.validate(f.run.dungeon, "access.cooldown.value", 60));
            f.run.dungeon.getConfig().set("access.cooldown.period", "WEEKLY");
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "access.cooldown.reset_day", 8));
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "runs.time_limit_minutes", 1000001));
            assertDoesNotThrow(
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "dungeon.display_name", "Name"));
            var branching = mock(BranchingDungeon.class);
            when(branching.asBranching()).thenReturn(branching);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    branching, "generator.room_pool", List.of("missing")));
        }
    }

    @Test
    void corruptStaffStateFailsClosedAndReasonValidationIsBounded() throws Exception {
        try (var f = new StaffFixture(directory)) {
            for (String text :
                    List.of(
                            "maintenance: false\n",
                            "restrictions: []\n",
                            "maintenance:\n  crypt: false\n",
                            "restrictions:\n  bad-uuid: {}\n")) {
                f.run.paper.state.seed("staff-state", "server", null, text);
                assertThrows(RuntimeException.class, () -> new StaffOperations(f.runtime), text);
            }
            assertThrows(IllegalArgumentException.class, () -> StaffOperations.requireReason(" "));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> StaffOperations.requireReason("a".repeat(257)));
            assertDoesNotThrow(() -> StaffOperations.requireReason("a".repeat(256)));
        }
    }

    @Test
    void changingCooldownCadenceValidatesTheValuesItWillActivate() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var config = f.run.dungeon.getConfig();
            config.set("access.cooldown.period", "TIMER");
            config.set("access.cooldown.value", 60);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "access.cooldown.period", "DAILY"));
            config.set("access.cooldown.value", 12);
            assertDoesNotThrow(
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "access.cooldown.period", "DAILY"));
            config.set("rewards.loot_cooldown.reset_day", 31);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "rewards.loot_cooldown.period", "WEEKLY"));
            assertDoesNotThrow(
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "rewards.loot_cooldown.period", "MONTHLY"));
            config.set("rewards.loot_cooldown.reset_day", 7);
            assertDoesNotThrow(
                    () ->
                            SettingValidation.validate(
                                    f.run.dungeon, "rewards.loot_cooldown.period", "WEEKLY"));
        }
    }

    @Test
    void roomGraphsAtIdenticalRelativeCoordinatesAreAllInspected() throws Exception {
        try (var f = new StaffFixture(directory)) {
            var branching = mock(BranchingDungeon.class);
            when(branching.asBranching()).thenReturn(branching);
            var first = mock(nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition.class);
            var second = mock(nl.hauntedmc.dungeons.generation.room.BranchingRoomDefinition.class);
            var script = new CommandFunction();
            var ordinary = new nl.hauntedmc.dungeons.content.function.MessageFunction();
            var location = new org.bukkit.Location(null, 0, 0, 0);
            when(first.getFunctionsMapRelative()).thenReturn(Map.of(location, script));
            when(second.getFunctionsMapRelative()).thenReturn(Map.of(location, ordinary));
            when(branching.getUniqueRooms()).thenReturn(Map.of("first", first, "second", second));
            var visited = new ArrayList<Object>();
            ContentReferences.dungeon(branching, visited::add);
            assertTrue(visited.contains(script));
            assertTrue(visited.contains(ordinary));
        }
    }

    @Test
    void permissionPresetsNeverInheritGameplayBypassesAndOnlyOperatorsGetScripts()
            throws Exception {
        var yaml = new YamlConfiguration();
        yaml.load(Path.of("src/main/resources/plugin.yml").toFile());
        for (String role : List.of("moderator", "builder", "administrator", "operator")) {
            String path = "permissions.dungeons.role." + role + ".children";
            // Bukkit permission node names contain dots; inspect raw child mappings rather than
            // YAML paths.
            var permissions = yaml.getConfigurationSection("permissions");
            var node = permissions.getConfigurationSection("dungeons.role." + role);
            assertNotNull(node);
            var children = node.getConfigurationSection("children");
            assertFalse(children.getBoolean("dungeons.bypass.commands"));
            assertFalse(children.getBoolean("dungeons.bypass.cooldown"));
            assertEquals(role.equals("operator"), children.getBoolean("dungeons.staff.scripts"));
        }
    }
}
