package net.playavalon.mythicdungeons.api.parents.instances;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.parents.dungeons.AbstractDungeon;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.dungeons.triggers.TriggerInteract;
import net.playavalon.mythicdungeons.listeners.dungeonlisteners.EditListener;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.ParticleUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import net.playavalon.mythicdungeons.utility.tasks.ProcessTimer;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.SystemUtils;
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
      long autosaveTime = MythicDungeons.inst().getConfig().getInt("General.AutoSaveInterval", 300) * 20L;
      if (autosaveTime > 0L) {
         this.autosaveTicker = new BukkitRunnable() {
            public void run() {
               InstanceEditable.this.autosave();
            }
         };
         this.autosaveTicker.runTaskTimer(MythicDungeons.inst(), autosaveTime, autosaveTime);
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
               DustOptions dustOptions = new DustOptions(Util.hexToColor(functionx.getColour()), 1.0F);

               for (MythicPlayer aPlayer : InstanceEditable.this.players) {
                  Player player = aPlayer.getPlayer();
                  if (!(loc.distance(player.getLocation()) > 15.0)) {
                     player.spawnParticle(ParticleUtils.getVersionParticle("DUST"), loc, 12, 0.25, 0.25, 0.25, dustOptions);
                     player.spawnParticle(Particle.END_ROD, loc, 1, 0.25, 0.25, 0.25, 0.01);
                  }
               }
            }
         }
      };
      this.functionParticles.runTaskTimer(MythicDungeons.inst(), 0L, 20L);
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
      for (MythicPlayer mPlayer : this.players) {
         mPlayer.getPlayer()
            .spigot()
            .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(LangUtils.getMessage("instance.editmode.autosaving")));
      }

      new ProcessTimer().run("Autosave of " + this.dungeon.getWorldName(), this::saveWorld);

      for (MythicPlayer mPlayer : this.players) {
         mPlayer.getPlayer()
            .spigot()
            .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(LangUtils.getMessage("instance.editmode.autosaved")));
      }
   }

   @Override
   public void saveWorld() {
      this.displayHandler.temporaryClear();
      this.instanceWorld.save();
      if (MythicDungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskAsynchronously(MythicDungeons.inst(), () -> this.commitWorld(null));
      } else {
         this.commitWorld(null);
      }

      this.displayHandler.restore();
   }

   @Override
   public void saveWorld(CountDownLatch latch) {
      new ProcessTimer().run("Saving of " + this.dungeon.getWorldName(), () -> {
         this.instanceWorld.save();
         if (MythicDungeons.inst().isEnabled()) {
            Bukkit.getScheduler().runTaskAsynchronously(MythicDungeons.inst(), () -> this.commitWorld(latch));
         } else {
            this.commitWorld(latch);
         }
      });
   }

   public void commitWorld(@Nullable CountDownLatch latch) {
      this.dungeon.setSaving(true);

      try {
         FileFilter filter = file -> !file.getName().equals("rooms");
         if (SystemUtils.IS_OS_WINDOWS) {
            filter = file -> !file.getName().equals("rooms") && !file.getName().endsWith(".lock");
            FileUtils.copyDirectory(this.instanceWorld.getWorldFolder(), this.dungeon.getFolder(), filter);
         } else {
            FileUtils.copyDirectory(this.instanceWorld.getWorldFolder(), this.dungeon.getFolder(), filter);
         }

         new File(this.dungeon.getFolder(), "uid.dat").delete();
      } catch (IOException var3) {
         var3.printStackTrace();
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
   public void addPlayer(MythicPlayer aPlayer) {
      Player player = aPlayer.getPlayer();
      aPlayer.saveInventory();
      super.addPlayer(aPlayer);
      aPlayer.setEditMode(true);
      Util.forceTeleport(player, this.startLoc);
   }

   @Override
   public void removePlayer(MythicPlayer aPlayer, boolean force) {
      Player player = aPlayer.getPlayer();
      if (!this.players.contains(aPlayer)) {
         if (aPlayer.getInstance() == this && player.isOnline() && aPlayer.getSavedPosition() != null) {
            aPlayer.setInstance(null);
            Util.forceTeleport(player, aPlayer.getSavedPosition());
            aPlayer.setSavedPosition(null);
         }
      } else {
         if (MythicDungeons.inst().isEnabled()) {
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
         String text = Util.fullColor("<" + function.getColour() + ">" + function.getDisplayName());
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
