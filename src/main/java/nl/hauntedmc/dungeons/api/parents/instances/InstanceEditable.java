package nl.hauntedmc.dungeons.api.parents.instances;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonClassic;
import nl.hauntedmc.dungeons.dungeons.triggers.TriggerInteract;
import nl.hauntedmc.dungeons.listeners.dungeonlisteners.EditListener;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.apache.commons.io.FileUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public class InstanceEditable extends AbstractInstance {
   protected BukkitRunnable autosaveTicker;
   protected BukkitRunnable functionParticles;

   public InstanceEditable(AbstractDungeon dungeon, CountDownLatch latch) {
      super(dungeon, latch);
      this.listener = new EditListener(this);
      long autosaveTime = Dungeons.inst().getConfig().getInt("General.AutoSaveInterval", 300) * 20L;
      if (autosaveTime > 0L) {
         this.autosaveTicker = new BukkitRunnable() {
            public void run() {
               InstanceEditable.this.autosave();
            }
         };
         this.autosaveTicker.runTaskTimer(Dungeons.inst(), autosaveTime, autosaveTime);
      }
   }

   @Override
   public void onLoadGame() {
      this.dungeon.backupDungeon();
      final DungeonClassic dungeon = (DungeonClassic)this.dungeon;
      List<DungeonFunction> functions = new ArrayList<>(dungeon.getFunctions().values());
      dungeon.getFunctions().clear();

      for (DungeonFunction function : functions) {
         function.setInstance(this);
         function.getLocation().setWorld(this.instanceWorld);
         this.addFunctionLabel(function);
         dungeon.addFunction(function.getLocation(), function);
         function.initMenu();
         DungeonTrigger trigger = function.getTrigger();
         if (trigger != null) {
            trigger.initMenu();
            trigger.initConditionsMenu();
         }
      }

      this.functionParticles = new BukkitRunnable() {
         public void run() {
            for (DungeonFunction functionx : dungeon.getFunctions().values()) {
               Location loc = functionx.getLocation().clone();
               loc.setX(loc.getX() + 0.5);
               loc.setY(loc.getY() + 0.7);
               loc.setZ(loc.getZ() + 0.5);
               loc.setWorld(InstanceEditable.this.instanceWorld);
               DustOptions dustOptions = new DustOptions(HelperUtils.hexToColor(functionx.getColour()), 1.0F);

               for (DungeonPlayer aPlayer : InstanceEditable.this.players) {
                  Player player = aPlayer.getPlayer();
                  if (!(loc.distance(player.getLocation()) > 15.0)) {
                     player.spawnParticle(Particle.DUST, loc, 12, 0.25, 0.25, 0.25, dustOptions);
                     player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
                  }
               }
            }
         }
      };
      this.functionParticles.runTaskTimer(Dungeons.inst(), 0L, 20L);
      this.latch.countDown();
   }

   @Override
   public void onDispose() {
      if (this.autosaveTicker != null && !this.autosaveTicker.isCancelled()) {
         this.autosaveTicker.cancel();
         this.autosaveTicker = null;
      }

      this.functionParticles.cancel();
      this.functionParticles = null;
      this.dungeon.setEditSession(null);
   }

   public void autosave() {
      for (DungeonPlayer mPlayer : this.players) {
         mPlayer.getPlayer().sendActionBar(HelperUtils.component(LangUtils.getMessage("instance.editmode.autosaving")));
      }

      new ProcessTimer().run("Autosave of " + this.dungeon.getWorldName(), this::saveWorld);

      for (DungeonPlayer mPlayer : this.players) {
         mPlayer.getPlayer().sendActionBar(HelperUtils.component(LangUtils.getMessage("instance.editmode.autosaved")));
      }
   }

   @Override
   public void saveWorld() {
      this.displayHandler.temporaryClear();
      this.instanceWorld.save();
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> this.commitWorld(null));
      } else {
         this.commitWorld(null);
      }

      this.displayHandler.restore();
   }

   @Override
   public void saveWorld(CountDownLatch latch) {
      new ProcessTimer().run("Saving of " + this.dungeon.getWorldName(), () -> {
         this.instanceWorld.save();
         if (Dungeons.inst().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> this.commitWorld(latch));
         } else {
            this.commitWorld(latch);
         }
      });
   }

   public void commitWorld(@Nullable CountDownLatch latch) {
      this.dungeon.setSaving(true);

      try {
         FileFilter filter = file -> !file.getName().equals("rooms");
         FileUtils.copyDirectory(this.instanceWorld.getWorldFolder(), this.dungeon.getFolder(), filter);

         new File(this.dungeon.getFolder(), "uid.dat").delete();
      } catch (IOException var3) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var3.getMessage());
      }

      this.dungeon.saveFunctions();
      this.onCommitWorld();
      this.dungeon.setSaving(false);
      if (latch != null) {
         latch.countDown();
      }
   }

   public void onCommitWorld() {
   }

   @Override
   public void addPlayer(DungeonPlayer aPlayer) {
      Player player = aPlayer.getPlayer();
      aPlayer.saveInventory();
      super.addPlayer(aPlayer);
      aPlayer.setEditMode(true);
      HelperUtils.forceTeleport(player, this.startLoc);
   }

   @Override
   public void removePlayer(DungeonPlayer aPlayer, boolean force) {
      Player player = aPlayer.getPlayer();
      if (!this.players.contains(aPlayer)) {
         if (aPlayer.getInstance() == this && player.isOnline() && aPlayer.getSavedPosition() != null) {
            aPlayer.setInstance(null);
            HelperUtils.forceTeleport(player, aPlayer.getSavedPosition());
            aPlayer.setSavedPosition(null);
         }
      } else {
         if (Dungeons.inst().isEnabled()) {
            aPlayer.restoreHotbar();
         }

         aPlayer.saveEditInventory();
         aPlayer.setEditMode(false);
         aPlayer.setChatListening(false);
         aPlayer.setActiveFunction(null);
         aPlayer.setActiveTrigger(null);
         aPlayer.setActiveCondition(null);
         aPlayer.setCopiedFunction(null);
         aPlayer.setCutting(false);
         aPlayer.setCopying(false);
         aPlayer.setPos1(null);
         aPlayer.setPos2(null);
         aPlayer.setAwaitingRoomName(false);
         aPlayer.setActiveRoom(null);
         aPlayer.setActiveConnector(null);
         aPlayer.setActiveDoor(null);
         aPlayer.setConfirmRoomAction(false);
         aPlayer.setCopiedConnector(null);
         super.removePlayer(aPlayer, force);
      }
   }

   public void addFunctionLabel(DungeonFunction function) {
      if (this.displayHandler != null) {
         this.setTextDisplayLabel(function);
      }
   }

   public void updateLabel(DungeonFunction function) {
      if (this.displayHandler != null) {
         this.setTextDisplayLabel(function);
      }
   }

   public void removeFunctionLabelByFunction(DungeonFunction function) {
      if (this.displayHandler != null) {
         this.removeTextDisplayLabel(function);
      }
   }

   protected void setTextDisplayLabel(DungeonFunction function) {
      if (this.displayHandler != null) {
         Location fLoc = function.getLocation().clone();
         if (fLoc.getWorld() == null) {
            fLoc.setWorld(this.instanceWorld);
         }

         fLoc.setX(fLoc.getX() + 0.5);
         fLoc.setY(fLoc.getY() + 1.2);
         fLoc.setZ(fLoc.getZ() + 0.5);
         String text = HelperUtils.fullColor("<" + function.getColour() + ">" + function.getDisplayName());
         if (function.getTrigger() != null) {
            text = text + "\n<#9753f5>" + function.getTrigger().getDisplayName();
         }

         this.displayHandler.setHologram(fLoc, 10.0F, text, true);
      }
   }

   public void removeTextDisplayLabel(DungeonFunction function) {
      if (this.displayHandler != null) {
         Location fLoc = function.getLocation().clone();
         if (fLoc.getWorld() == null) {
            fLoc.setWorld(this.instanceWorld);
         }

         fLoc.setX(fLoc.getX() + 0.5);
         fLoc.setY(fLoc.getY() + 1.2);
         fLoc.setZ(fLoc.getZ() + 0.5);
         this.displayHandler.removeHologram(fLoc);
      }
   }

   public int cleanSigns() {
      int count = 0;

      for (DungeonFunction function : this.functions.values()) {
         if (!(function.getTrigger() instanceof TriggerInteract)) {
            Block sign = this.instanceWorld.getBlockAt(function.getLocation());
            BlockState state = sign.getState();
            if (state instanceof Sign) {
               sign.setType(Material.AIR);
               count++;
            }
         }
      }

      return count;
   }
}
