package nl.hauntedmc.dungeons.util.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

class DungeonRuleConfigurationTest {
    @Test
    void missingRulesInheritEvenWhenGetterHasAnExplicitFallback() throws Exception {
        YamlConfiguration global = new YamlConfiguration();
        global.loadFromString(
                "global_rules:\n  combat:\n    pvp: true\n  commands:\n    allow_list: [dungeon leave]\n");
        DungeonRuleConfiguration dungeon = new DungeonRuleConfiguration(global);
        dungeon.loadFromString("rules:\n  combat:\n    hide_death_messages: true\n");

        assertTrue(dungeon.getBoolean("rules.combat.pvp", false));
        assertTrue(dungeon.getBoolean("rules.combat.hide_death_messages", false));
        assertEquals(List.of("dungeon leave"), dungeon.getStringList("rules.commands.allow_list"));
        assertFalse(dungeon.getBoolean("rules.missing", false));
        assertTrue(dungeon.getBoolean("rules.missing", true));
        assertFalse(dungeon.getBoolean("unrelated.pvp", false));
    }

    @Test
    void explicitFalseAndEmptyListsOverrideAndRemovalRestoresInheritance() throws Exception {
        YamlConfiguration global = new YamlConfiguration();
        global.set("global_rules.combat.pvp", true);
        global.set("global_rules.items.banned_materials", List.of("TNT"));
        DungeonRuleConfiguration dungeon = new DungeonRuleConfiguration(global);
        dungeon.set("rules.combat.pvp", false);
        dungeon.set("rules.items.banned_materials", List.of());

        assertFalse(dungeon.getBoolean("rules.combat.pvp", true));
        assertTrue(dungeon.getStringList("rules.items.banned_materials").isEmpty());
        assertTrue(global.getBoolean("global_rules.combat.pvp"));
        assertEquals(List.of("TNT"), global.getStringList("global_rules.items.banned_materials"));

        dungeon.set("rules.combat.pvp", null);
        assertTrue(dungeon.getBoolean("rules.combat.pvp", false));
        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(dungeon.saveToString());
        assertFalse(saved.contains("rules.combat.pvp"));
        assertEquals(List.of(), saved.getList("rules.items.banned_materials"));
    }

    @Test
    void syncDoesNotFillOrRemoveDungeonOverridesEvenWithAnOldActiveTemplate() throws Exception {
        YamlConfiguration template = new YamlConfiguration();
        template.loadFromString("dungeon:\n  enabled: true\nrules:\n  combat:\n    pvp: false\n");
        DungeonRuleConfiguration dungeon = new DungeonRuleConfiguration(new YamlConfiguration());
        dungeon.set("rules.combat.hide_death_messages", true);

        ConfigSyncUtils.syncMissingAndObsolete(template, dungeon, Set.of("rules"));

        assertTrue(dungeon.getBoolean("dungeon.enabled"));
        assertTrue(dungeon.getBoolean("rules.combat.hide_death_messages"));
        assertFalse(dungeon.getKeys(true).contains("rules.combat.pvp"));
    }

    @Test
    void globalCommandAdditionReachesAllInheritingMapsAfterConfigReplacement() throws Exception {
        YamlConfiguration original = new YamlConfiguration();
        original.set("global_rules.commands.allow_list", List.of("dungeon leave"));
        AtomicReference<YamlConfiguration> current = new AtomicReference<>(original);
        DungeonRuleConfiguration first = new DungeonRuleConfiguration(current::get);
        DungeonRuleConfiguration second = new DungeonRuleConfiguration(current::get);
        DungeonRuleConfiguration overridden = new DungeonRuleConfiguration(current::get);
        first.set("rules.combat.pvp", true);
        overridden.set("rules.commands.allow_list", List.of("dungeon help"));

        // A normal plugin config reload creates an entirely new YAML configuration object.
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.set("global_rules.commands.allow_list", List.of("dungeon leave", "balance"));
        current.set(reloaded);

        assertEquals(
                List.of("dungeon leave", "balance"),
                first.getStringList("rules.commands.allow_list"));
        assertEquals(
                List.of("dungeon leave", "balance"),
                second.getStringList("rules.commands.allow_list"));
        assertEquals(
                List.of("dungeon help"), overridden.getStringList("rules.commands.allow_list"));
        overridden.set("rules.commands.allow_list", null);
        assertEquals(
                List.of("dungeon leave", "balance"),
                overridden.getStringList("rules.commands.allow_list"));

        for (DungeonRuleConfiguration dungeon : List.of(first, second, overridden)) {
            DungeonRuleConfiguration saved = new DungeonRuleConfiguration(current::get);
            saved.loadFromString(dungeon.saveToString());
            assertFalse(saved.getKeys(true).contains("rules.commands.allow_list"));
            assertEquals(
                    List.of("dungeon leave", "balance"),
                    saved.getStringList("rules.commands.allow_list"));
        }
    }

    @Test
    void modifyingAnInheritedListDoesNotModifyGlobalsOrOtherDungeons() {
        YamlConfiguration global = new YamlConfiguration();
        global.set("global_rules.items.banned_items", List.of("example"));
        DungeonRuleConfiguration dungeon = new DungeonRuleConfiguration(global);
        List<?> inherited = dungeon.getList("rules.items.banned_items");
        inherited.clear();

        assertEquals(List.of("example"), global.getList("global_rules.items.banned_items"));
        assertEquals(List.of("example"), dungeon.getList("rules.items.banned_items"));
        assertTrue(dungeon.getKeys(true).isEmpty());
    }

    @Test
    void bundledNewMapTemplateInheritsGlobalRulesWithoutPersistingThem() throws Exception {
        YamlConfiguration global;
        try (var reader =
                new InputStreamReader(
                        getClass().getResourceAsStream("/config.yml"), StandardCharsets.UTF_8)) {
            global = YamlConfiguration.loadConfiguration(reader);
        }
        DungeonRuleConfiguration dungeon = new DungeonRuleConfiguration(global);
        try (var reader =
                new InputStreamReader(
                        getClass().getResourceAsStream("/dungeons/config_default.yml"),
                        StandardCharsets.UTF_8)) {
            dungeon.load(reader);
        }

        assertFalse(dungeon.getKeys(true).stream().anyMatch(key -> key.startsWith("rules")));
        assertTrue(dungeon.getBoolean("rules.world.freeze_random_ticks", false));
        assertTrue(dungeon.getStringList("rules.commands.allow_list").contains("dungeon leave"));
        YamlConfiguration saved = new YamlConfiguration();
        saved.loadFromString(dungeon.saveToString());
        assertFalse(saved.contains("rules"));
    }
}
