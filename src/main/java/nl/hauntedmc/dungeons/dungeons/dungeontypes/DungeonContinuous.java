package nl.hauntedmc.dungeons.dungeons.dungeontypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.exceptions.DungeonInitException;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceContinuous;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DungeonContinuous extends DungeonClassic {
   private final List<InstanceContinuous> activeInstances = new ArrayList<>();

   public DungeonContinuous(@NotNull File folder, @Nullable YamlConfiguration loadedConfig) throws DungeonInitException {
      super(folder, loadedConfig);
      if (this.config.getBoolean("ContinuousDungeons.LoadAtStart", false)) {
         Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> this.preLoad(), 1L);
      }
   }

   @Override
   public InstancePlayable createPlaySession(CountDownLatch latch) {
      InstanceContinuous instance = new InstanceContinuous(this, latch);
      instance.init();
      return instance;
   }

   public InstancePlayable preLoad() {
      return this.preLoad(new CountDownLatch(1));
   }

   public InstancePlayable preLoad(CountDownLatch latch) {
      InstanceContinuous inst = (InstanceContinuous)this.createPlaySession(latch);
      this.instances.add(inst);
      Dungeons.inst().getActiveInstances().add(inst);
      this.activeInstances.add(inst);
      return inst;
   }

   @Override
   public boolean prepInstance(Player player, String difficultyName) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      LangUtils.sendMessage(player, "instance.loading");
      InstanceContinuous inst = this.getFirstAvailableInstance();
      if (inst == null) {
         DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);
         Bukkit.getScheduler()
            .runTaskAsynchronously(Dungeons.inst(), () -> new ProcessTimer().run("Loading ongoing dungeon " + this.getWorldName(), () -> {
               CountDownLatch latch = new CountDownLatch(1);
               InstancePlayable newInst = this.preLoad(latch);
               newInst.setDifficulty(difficulty);

               try {
                  boolean loaded = latch.await(5L, TimeUnit.SECONDS);
                  if (!loaded) {
                     this.removeInstance(newInst);
                     LangUtils.sendMessage(player, "instance.timed-out");
                     return;
                  }

                  Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
                     newInst.addPlayer(aPlayer);
                     aPlayer.setAwaitingDungeon(false);
                     if (Dungeons.inst().isPartiesEnabled() && aPlayer.getiDungeonParty() != null) {
                        for (Player partyPlayer : aPlayer.getiDungeonParty().getPlayers()) {
                           DungeonPlayer enteringPlayer = Dungeons.inst().getDungeonPlayer(partyPlayer);
                           if (enteringPlayer != aPlayer) {
                              newInst.addPlayer(enteringPlayer);
                              enteringPlayer.setAwaitingDungeon(false);
                           }
                        }
                     }

                     LangUtils.sendMessage(player, "instance.loaded");
                  });
               } catch (InterruptedException var7) {
                  LangUtils.sendMessage(player, "instance.failed");
                  Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var7.getMessage());
               }
            }));
      } else {
         Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
            inst.addPlayer(aPlayer);
            if (Dungeons.inst().isPartiesEnabled() && aPlayer.getiDungeonParty() != null) {
               for (Player partyPlayer : aPlayer.getiDungeonParty().getPlayers()) {
                  DungeonPlayer enteringPlayer = Dungeons.inst().getDungeonPlayer(partyPlayer);
                  if (enteringPlayer != aPlayer) {
                     inst.addPlayer(enteringPlayer);
                  }
               }
            }

            LangUtils.sendMessage(player, "instance.loaded");
         });
      }

      return true;
   }

   @Nullable
   public InstanceContinuous getFirstAvailableInstance() {
      int maxPlayers = this.config.getInt("ContinuousDungeons.MaxPlayers", 0);
      if (this.activeInstances.isEmpty()) {
         return null;
      } else if (maxPlayers <= 0) {
         return this.activeInstances.getFirst();
      } else {
         for (InstanceContinuous inst : this.activeInstances) {
            if (inst.getPlayers().size() < maxPlayers) {
               return inst;
            }
         }

         return null;
      }
   }

   public List<InstanceContinuous> getActiveInstances() {
      return this.activeInstances;
   }
}
