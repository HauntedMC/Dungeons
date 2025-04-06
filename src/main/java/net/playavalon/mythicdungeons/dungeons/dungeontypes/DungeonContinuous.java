package net.playavalon.mythicdungeons.dungeons.dungeontypes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.exceptions.DungeonInitException;
import net.playavalon.mythicdungeons.api.parents.DungeonDifficulty;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.dungeons.instancetypes.play.InstanceContinuous;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.tasks.ProcessTimer;
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
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> this.preLoad(), 1L);
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
      MythicDungeons.inst().getActiveInstances().add(inst);
      this.activeInstances.add(inst);
      return inst;
   }

   @Override
   public boolean prepInstance(Player player, String difficultyName) {
      MythicPlayer aPlayer = MythicDungeons.inst().getMythicPlayer(player);
      LangUtils.sendMessage(player, "instance.loading");
      InstanceContinuous inst = this.getFirstAvailableInstance();
      if (inst == null) {
         DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);
         Bukkit.getScheduler()
            .runTaskAsynchronously(MythicDungeons.inst(), () -> new ProcessTimer().run("Loading ongoing dungeon " + this.getWorldName(), () -> {
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

                  Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> {
                     newInst.addPlayer(aPlayer);
                     aPlayer.setAwaitingDungeon(false);
                     if (MythicDungeons.inst().isPartiesEnabled() && aPlayer.getDungeonParty() != null) {
                        for (Player partyPlayer : aPlayer.getDungeonParty().getPlayers()) {
                           MythicPlayer enteringPlayer = MythicDungeons.inst().getMythicPlayer(partyPlayer);
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
                  var7.printStackTrace();
               }
            }));
      } else {
         Bukkit.getScheduler().runTask(MythicDungeons.inst(), () -> {
            inst.addPlayer(aPlayer);
            if (MythicDungeons.inst().isPartiesEnabled() && aPlayer.getDungeonParty() != null) {
               for (Player partyPlayer : aPlayer.getDungeonParty().getPlayers()) {
                  MythicPlayer enteringPlayer = MythicDungeons.inst().getMythicPlayer(partyPlayer);
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
         return this.activeInstances.get(0);
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
