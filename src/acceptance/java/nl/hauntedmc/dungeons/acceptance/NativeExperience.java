package nl.hauntedmc.dungeons.acceptance;

import static nl.hauntedmc.dungeons.acceptance.AcceptancePlugin.require;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import nl.hauntedmc.dungeons.api.DungeonExperienceApi;
import nl.hauntedmc.dungeons.event.ExperienceEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.EnderCrystal;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/** Real Paper coverage for optional loot content, native destruction and encounter ownership. */
final class NativeExperience {
    private NativeExperience() {}

    static CompletionStage<Void> run(AcceptancePlugin test) throws Exception {
        require(
                Bukkit.getServicesManager().load(DungeonExperienceApi.class) != null,
                "Missing experience API");
        var loot = WorldFixtures.definition(test, "acceptance_experience_loot", "static");
        UUID profileId = UUID.randomUUID();
        test.seedPlayer(profileId);
        var nativePlayer = NativePlayerRecovery.nativePlayer(profileId);
        var session =
                new nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession(
                        test.runtime(), nativePlayer);
        session.setExperiencePreference("profile", "immersive");
        session.setExperienceSavePoint(
                new Location(Bukkit.getWorlds().getFirst(), 7, 70, 9),
                loot.getWorldName(),
                loot.getDungeonId().toString(),
                "profile-fixture",
                "hard",
                java.util.Map.of("seals", 2),
                List.of("seals"),
                List.of("personal"));
        var reloaded =
                new nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession(
                        test.runtime(), nativePlayer);
        require(
                reloaded.experiencePreference("profile", "inherit").equals("immersive"),
                "Presentation preference was not durable");
        require(
                reloaded.experienceCheckpoint(
                                loot.getDungeonId().toString(), "profile-fixture", "hard")
                        .equals(java.util.Map.of("seals", 2)),
                "Personal goals were not durable");
        require(
                reloaded.experienceSavedReveals(loot.getDungeonId().toString())
                        .equals(List.of("seals")),
                "Revealed goals were not durable");
        require(
                reloaded.experienceSavedTracks(loot.getDungeonId().toString())
                        .equals(List.of("personal")),
                "Activated personal tracks were not durable");
        reloaded.discardExperienceCheckpoint(loot.getWorldName(), loot.getDungeonId().toString());
        var adventure = WorldFixtures.definition(test, "acceptance_experience_waves", "static");
        Files.writeString(
                adventure.getFolder().toPath().resolve("experience.yml"),
                """
                version: 1
                objectives:
                  guards: {source: kill, selector: 'group:guards', target: 4}
                  crystals: {source: destroy, selector: 'type:END_CRYSTAL', target: 2}
                groups:
                  guards:
                    entity: ZOMBIE
                    count: 2
                    location: {x: 0, y: 66, z: 0, radius: 16}
                encounters:
                  arena:
                    auto_start: true
                    preparation_seconds: 1
                    waves:
                      first: {groups: [guards]}
                      second: {groups: [guards]}
                """);
        return WorldFixtures.start(test, loot)
                .thenCompose(
                        lootRun -> {
                            require(
                                    !lootRun.experienceDefinition().hasMechanics(),
                                    "Loot received synthetic mechanics");
                            require(
                                    test.runtime().experience().mechanics(lootRun) == null,
                                    "Loot allocated an objective runtime");
                            return WorldFixtures.dispose(test, lootRun);
                        })
                .thenCompose(ignored -> WorldFixtures.start(test, adventure))
                .thenCompose(
                        run -> {
                            run.getInstanceWorld().getChunkAt(0, 0).addPluginChunkTicket(test);
                            run.getInstanceWorld().setTime(18000);
                            for (int x = -4; x <= 4; x++)
                                for (int z = -4; z <= 4; z++)
                                    run.getInstanceWorld()
                                            .getBlockAt(x, 65, z)
                                            .setType(Material.STONE);
                            List<String> transitions = new ArrayList<>();
                            Listener observer =
                                    new Listener() {
                                        @EventHandler
                                        public void change(ExperienceEvent event) {
                                            if (event.getInstance() == run)
                                                transitions.add(event.kind());
                                        }
                                    };
                            Bukkit.getPluginManager().registerEvents(observer, test);
                            var service = test.runtime().experience();
                            var mechanics = service.mechanics(run);
                            var location = new Location(run.getInstanceWorld(), 3, 67, 3);
                            EnderCrystal removed =
                                    run.getInstanceWorld().spawn(location, EnderCrystal.class);
                            removed.remove();
                            require(
                                    mechanics.progress().count("crystals", new UUID(0, 0)) == 0,
                                    "Plugin removal counted as destruction");
                            for (int i = 0; i < 2; i++) {
                                EnderCrystal crystal =
                                        run.getInstanceWorld()
                                                .spawn(
                                                        location.clone().add(i, 0, 0),
                                                        EnderCrystal.class);
                                run.getInstanceWorld()
                                        .createExplosion(crystal.getLocation(), 1.0f, false, false);
                            }
                            return test.await(
                                            "first tracked wave",
                                            () ->
                                                    run.getInstanceWorld()
                                                                    .getEntitiesByClass(
                                                                            Zombie.class)
                                                                    .size()
                                                            == 2)
                                    .thenCompose(
                                            ignored -> {
                                                run.getInstanceWorld()
                                                        .getEntitiesByClass(Zombie.class)
                                                        .forEach(entity -> entity.setHealth(0));
                                                return test.await(
                                                        "second tracked wave",
                                                        () ->
                                                                transitions.stream()
                                                                                        .filter(
                                                                                                "wave-started"
                                                                                                        ::equals)
                                                                                        .count()
                                                                                == 2
                                                                        && run.getInstanceWorld()
                                                                                        .getEntitiesByClass(
                                                                                                Zombie
                                                                                                        .class)
                                                                                        .size()
                                                                                == 2);
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                run.getInstanceWorld()
                                                        .getEntitiesByClass(Zombie.class)
                                                        .forEach(entity -> entity.setHealth(0));
                                                return test.await(
                                                        "encounter completion",
                                                        () ->
                                                                transitions.contains(
                                                                        "encounter-completed"));
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                require(
                                                        mechanics
                                                                        .progress()
                                                                        .count(
                                                                                "guards",
                                                                                new UUID(0, 0))
                                                                == 4,
                                                        "Tracked wave kills were not counted");
                                                require(
                                                        mechanics
                                                                        .progress()
                                                                        .count(
                                                                                "crystals",
                                                                                new UUID(0, 0))
                                                                == 2,
                                                        "Native crystal explosions were not counted exactly once");
                                                require(
                                                        transitions.stream()
                                                                        .filter(
                                                                                "encounter-completed"
                                                                                        ::equals)
                                                                        .count()
                                                                == 1,
                                                        "Duplicate encounter completion");
                                                mechanics.reset("arena");
                                                mechanics.start("arena");
                                                return test.await(
                                                        "reset encounter wave",
                                                        () ->
                                                                run.getInstanceWorld()
                                                                                .getEntitiesByClass(
                                                                                        Zombie
                                                                                                .class)
                                                                                .size()
                                                                        == 2);
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                run.getInstanceWorld()
                                                        .getEntitiesByClass(Zombie.class)
                                                        .iterator()
                                                        .next()
                                                        .remove();
                                                return test.await(
                                                        "unexpected removal fails the encounter",
                                                        () ->
                                                                transitions.contains(
                                                                        "encounter-failed"));
                                            })
                                    .thenCompose(
                                            ignored -> {
                                                require(
                                                        transitions.stream()
                                                                        .filter(
                                                                                "encounter-completed"
                                                                                        ::equals)
                                                                        .count()
                                                                == 1,
                                                        "Removal produced a false victory");
                                                HandlerList.unregisterAll(observer);
                                                run.getInstanceWorld()
                                                        .getChunkAt(0, 0)
                                                        .removePluginChunkTicket(test);
                                                return WorldFixtures.dispose(test, run);
                                            })
                                    .whenComplete(
                                            (ignored, failure) ->
                                                    HandlerList.unregisterAll(observer));
                        });
    }
}
