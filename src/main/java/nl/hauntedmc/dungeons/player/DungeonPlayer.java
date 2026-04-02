package nl.hauntedmc.dungeons.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.config.AsyncConfiguration;
import nl.hauntedmc.dungeons.api.events.HotbarSetEvent;
import nl.hauntedmc.dungeons.api.generation.rooms.Connector;
import nl.hauntedmc.dungeons.api.generation.rooms.ConnectorDoor;
import nl.hauntedmc.dungeons.api.generation.rooms.DungeonRoomContainer;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonTrigger;
import nl.hauntedmc.dungeons.api.parents.elements.TriggerCondition;
import nl.hauntedmc.dungeons.api.parents.instances.AbstractInstance;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.dungeons.dungeontypes.DungeonClassic;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public final class DungeonPlayer {
   private Player player;
   private AbstractInstance instance;
   private boolean awaitingDungeon;
   @Nullable
   private IDungeonParty iDungeonParty;
   private final File playerDataFile;
   private final AsyncConfiguration playerData;
   private List<String> completedDungeons = new ArrayList<>();
   private GameMode savedGameMode;
   private Location savedPosition;
   private float savedExp;
   private int savedLevel;
   private Location dungeonRespawn;
   private final List<ItemStack> rewardsInv;
   private boolean isDisconnecting;
   private boolean dead;
   private boolean editMode;
   private ItemStack[] savedInventory;
   private ItemStack[] savedArmor;
   private double savedHealth;
   private int savedFood;
   private ItemStack[] savedEditInventory;
   private ItemStack[] savedEditArmor;
   private DungeonPlayerHotbar savedDungeonPlayerHotbar;
   private DungeonFunction activeFunction;
   private DungeonTrigger activeTrigger;
    private Location targetLocation;
   private final List<DungeonPlayerHotbar> previousDungeonPlayerHotbars;
   private DungeonPlayerHotbar currentDungeonPlayerHotbar;
   private boolean chatListening = false;
   private DungeonFunction copiedFunction;
   private boolean isCopying;
   private boolean isCutting;
   private Location pos1;
   private Location pos2;
   private boolean awaitingRoomName;
   private DungeonRoomContainer activeRoom;
   private Connector activeConnector;
   private ConnectorDoor activeDoor;
   private boolean confirmRoomAction;
   private Connector copiedConnector;
   private boolean addingWhitelistEntry;
   private boolean editingWhitelistEntry;
   private boolean removingWhitelistEntry;
   private boolean reloadQueued;

   public DungeonPlayer(Player player) {
      this.player = player;
      this.previousDungeonPlayerHotbars = new ArrayList<>();
      this.savedGameMode = Bukkit.getDefaultGameMode();
      this.rewardsInv = new ArrayList<>();
      this.playerData = new AsyncConfiguration(Dungeons.inst());
      this.playerDataFile = new File(Dungeons.inst().getDataFolder(), "globalplayerdata/" + player.getUniqueId() + ".yml");

      try {
         if (!this.playerDataFile.exists()) {
            this.playerDataFile.createNewFile();
         }

         this.playerData.load(this.playerDataFile);
         this.completedDungeons = (List<String>) this.playerData.getList("FinishedDungeons", new ArrayList<>());
      } catch (IOException var3) {
         Dungeons.inst()
            .getLogger()
            .info(HelperUtils.colorize("&cERROR :: Could not load player data file for '" + player.getName() + "'! Failed to create file."));
      }
   }

   public void setEditMode(boolean editMode) {
      this.editMode = editMode;
      if (editMode) {
         this.player.getInventory().clear();
         this.player.setGameMode(GameMode.CREATIVE);
         this.loadEditInventory();
      } else {
         this.player.getInventory().clear();
         this.restoreInventory();
      }
   }

   public void saveInventory() {
      this.savedInventory = this.player.getInventory().getContents();
      this.savedArmor = this.player.getInventory().getArmorContents();
      this.savedHealth = this.player.getHealth();
      this.savedFood = this.player.getFoodLevel();
   }

   public void restoreInventory() {
      if (Dungeons.inst().isEnabled()) {
         this.player.getInventory().setContents(this.savedInventory);
         this.player.getInventory().setArmorContents(this.savedArmor);
         this.player.setHealth(Math.min(this.savedHealth, this.player.getMaxHealth()));
         this.player.setFoodLevel(this.savedFood);
      } else {
         this.player.getInventory().setContents(this.savedInventory);
         this.player.getInventory().setArmorContents(this.savedArmor);
      }
   }

   public void restoreExp() {
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
            this.player.setExp(this.savedExp);
            this.player.setLevel(this.savedLevel);
         }, 1L);
      } else {
         this.player.setExp(this.savedExp);
         this.player.setLevel(this.savedLevel);
      }
   }

   public void saveEditInventory() {
      this.savedEditInventory = this.player.getInventory().getContents();
      this.savedEditArmor = this.player.getInventory().getArmorContents();
   }

   public void loadEditInventory() {
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
            if (this.savedEditInventory != null) {
               this.player.getInventory().setContents(this.savedEditInventory);
            } else {
               ItemUtils.giveOrDrop(this.player, ItemUtils.getFunctionTool());
            }

            if (this.savedEditArmor != null) {
               this.player.getInventory().setArmorContents(this.savedEditArmor);
            }
         }, 2L);
      } else {
         if (this.savedEditInventory != null) {
            this.player.getInventory().setContents(this.savedEditInventory);
         } else {
            ItemUtils.giveOrDrop(this.player, ItemUtils.getFunctionTool());
         }

         if (this.savedEditArmor != null) {
            this.player.getInventory().setArmorContents(this.savedEditArmor);
         }

         ItemUtils.giveOrDrop(this.player, ItemUtils.getFunctionTool());
      }
   }

   public boolean hasEditInventory() {
      return this.savedEditInventory != null;
   }

   public List<ItemStack> getRewardsInv() {
      List<ItemStack> rewards = new ArrayList<>(this.rewardsInv);
      if (this.instance != null && this.instance instanceof InstancePlayable) {
         List<ItemStack> instanceRewards = ((InstancePlayable)this.instance).getRewardInventories().get(this.player.getUniqueId());
         if (instanceRewards != null && !instanceRewards.isEmpty()) {
            rewards.addAll(instanceRewards);
         }
      }

      return rewards;
   }

   public void addReward(ItemStack... items) {
      this.rewardsInv.addAll(Arrays.asList(items));
   }

   public void removeReward(ItemStack item) {
      this.rewardsInv.remove(item);
   }

   public void clearRewards() {
      this.rewardsInv.clear();
   }

   public void saveHotbar() {
      this.savedDungeonPlayerHotbar = new DungeonPlayerHotbar(this.player);
   }

   public void restoreHotbar() {
      HotbarSetEvent event = new HotbarSetEvent(this.savedDungeonPlayerHotbar, this);
      Bukkit.getPluginManager().callEvent(event);
      if (!event.isCancelled()) {
         if (this.savedDungeonPlayerHotbar != null) {
            this.savedDungeonPlayerHotbar.setHotbar(this.player.getInventory(), true);
            this.savedDungeonPlayerHotbar = null;
            this.player.playSound(this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
         }

         this.currentDungeonPlayerHotbar = null;
         this.previousDungeonPlayerHotbars.clear();
      }
   }

   public void switchHotbar(DungeonPlayerHotbar dungeonPlayerHotbar) {
      this.saveHotbar();
      this.setHotbar(dungeonPlayerHotbar, true);
   }

   public void setHotbar(DungeonPlayerHotbar dungeonPlayerHotbar) {
      this.setHotbar(dungeonPlayerHotbar, false);
   }

   public void setHotbar(final DungeonPlayerHotbar dungeonPlayerHotbar, final boolean resetHand) {
      (new BukkitRunnable() {
         public void run() {
            HotbarSetEvent event = new HotbarSetEvent(dungeonPlayerHotbar, DungeonPlayer.this);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
               if (dungeonPlayerHotbar != DungeonPlayer.this.currentDungeonPlayerHotbar) {
                  DungeonPlayer.this.previousDungeonPlayerHotbars.add(DungeonPlayer.this.currentDungeonPlayerHotbar);
               }

               DungeonPlayer.this.currentDungeonPlayerHotbar = dungeonPlayerHotbar;
               dungeonPlayerHotbar.setHotbar(DungeonPlayer.this.player.getInventory(), resetHand);
               DungeonPlayer.this.player.playSound(DungeonPlayer.this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
               DungeonPlayer.this.pos1 = null;
               DungeonPlayer.this.pos2 = null;
            }
         }
      }).runTaskLater(Dungeons.inst(), 1L);
   }

   public DungeonPlayerHotbar getPreviousHotbar() {
      return this.previousDungeonPlayerHotbars.isEmpty() ? null : this.previousDungeonPlayerHotbars.getLast();
   }

   public void previousHotbar() {
      this.previousHotbar(false);
   }

   public void previousHotbar(final boolean resetHand) {
      (new BukkitRunnable() {
         public void run() {
            DungeonPlayerHotbar dungeonPlayerHotbar = DungeonPlayer.this.getPreviousHotbar();
            if (dungeonPlayerHotbar == null) {
               DungeonPlayer.this.restoreHotbar();
            } else {
               HotbarSetEvent event = new HotbarSetEvent(dungeonPlayerHotbar, DungeonPlayer.this);
               Bukkit.getPluginManager().callEvent(event);
               if (!event.isCancelled()) {
                  DungeonPlayer.this.previousDungeonPlayerHotbars.removeLast();
                  DungeonPlayer.this.currentDungeonPlayerHotbar = dungeonPlayerHotbar;
                  dungeonPlayerHotbar.setHotbar(DungeonPlayer.this.player.getInventory(), resetHand);
                  DungeonPlayer.this.player.playSound(DungeonPlayer.this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
               }
            }
         }
      }).runTaskLater(Dungeons.inst(), 1L);
   }

   public void sendToCheckpoint() {
      HelperUtils.forceTeleport(this.player, this.dungeonRespawn);
   }

   public void setSavedPosition(Location loc) {
      this.savedPosition = loc;
      this.saveExitLocation();
   }

   public void saveExitLocation() {
      if (this.instance != null) {
         this.playerData.set("StoredExitLocation", this.savedPosition);
         this.playerData.save(this.playerDataFile);
      }
   }

   @Nullable
   public Location getExitLocation() {
      return this.playerData.getLocation("StoredExitLocation");
   }

   public void clearExitLocation() {
      this.playerData.set("StoredExitLocation", null);
      this.playerData.save(this.playerDataFile);
   }

   public void setDungeonFinished(DungeonClassic dungeon) {
      if (!this.completedDungeons.contains(dungeon.getWorldName())) {
         this.completedDungeons.add(dungeon.getWorldName());
      }

      this.playerData.set("FinishedDungeons", this.completedDungeons);
      this.playerData.save(this.playerDataFile);
   }

   public void setDungeonUnfinished(DungeonClassic dungeon) {
      this.completedDungeons.remove(dungeon.getWorldName());
      this.playerData.set("FinishedDungeons", this.completedDungeons);
      this.playerData.save(this.playerDataFile);
   }

   public void setDungeonSavePoint(Location loc, String dungeon) {
      loc = loc.clone();
      loc.setWorld(null);
      this.playerData.set("DungeonSavePoint." + dungeon, loc);
      this.playerData.save(this.playerDataFile);
   }

   @Nullable
   public Location getDungeonSavePoint(String dungeon) {
      return this.playerData.getLocation("DungeonSavePoint." + dungeon);
   }

   public void clearDungeonSavePoint(String dungeon) {
      this.playerData.set("DungeonSavePoint." + dungeon, null);
   }

   public void setiDungeonParty(@Nullable IDungeonParty party) {
      this.iDungeonParty = party;
      if (party != null) {
         Dungeons.inst().getProviderManager().put(this.player);
      } else {
         this.setAwaitingDungeon(false);
      }
   }

   @Nullable
   public IDungeonParty getiDungeonParty() {
      if (this.iDungeonParty != null) {
         return this.iDungeonParty;
      } else {
          return null;
      }
   }

   public boolean hasParty() {
      if (this.iDungeonParty == null) {
         return false;
      } else if (!this.iDungeonParty.hasPlayer(this.player)) {
         this.iDungeonParty.removePlayer(this.player);
         this.setAwaitingDungeon(false);
         return false;
      } else {
         return true;
      }
   }

   public void queueReload() {
      this.reloadQueued = true;
      Bukkit.getScheduler().runTaskLater(Dungeons.inst(), () -> {
         if (this.reloadQueued) {
            this.reloadQueued = false;
         }
      }, 200L);
   }

   public void unqueueReload() {
      this.reloadQueued = false;
   }

   public Player getPlayer() {
      return this.player;
   }

   public void setPlayer(Player player) {
      this.player = player;
   }

   public AbstractInstance getInstance() {
      return this.instance;
   }

   public void setInstance(AbstractInstance instance) {
      this.instance = instance;
   }

   public boolean isAwaitingDungeon() {
      return this.awaitingDungeon;
   }

   public void setAwaitingDungeon(boolean awaitingDungeon) {
      this.awaitingDungeon = awaitingDungeon;
   }

   public GameMode getSavedGameMode() {
      return this.savedGameMode;
   }

   public void setSavedGameMode(GameMode savedGameMode) {
      this.savedGameMode = savedGameMode;
   }

   public Location getSavedPosition() {
      return this.savedPosition;
   }

   public void setSavedExp(float savedExp) {
      this.savedExp = savedExp;
   }

   public void setSavedLevel(int savedLevel) {
      this.savedLevel = savedLevel;
   }

   public Location getDungeonRespawn() {
      return this.dungeonRespawn;
   }

   public void setDungeonRespawn(Location dungeonRespawn) {
      this.dungeonRespawn = dungeonRespawn;
   }

   public boolean isDisconnecting() {
      return this.isDisconnecting;
   }

   public void setDisconnecting(boolean isDisconnecting) {
      this.isDisconnecting = isDisconnecting;
   }

   public boolean isDead() {
      return this.dead;
   }

   public void setDead(boolean dead) {
      this.dead = dead;
   }

   public boolean isEditMode() {
      return this.editMode;
   }

   public ItemStack[] getSavedEditInventory() {
      return this.savedEditInventory;
   }

   public DungeonPlayerHotbar getSavedHotbar() {
      return this.savedDungeonPlayerHotbar;
   }

   public DungeonFunction getActiveFunction() {
      return this.activeFunction;
   }

   public void setActiveFunction(DungeonFunction activeFunction) {
      this.activeFunction = activeFunction;
   }

   public DungeonTrigger getActiveTrigger() {
      return this.activeTrigger;
   }

   public void setActiveTrigger(DungeonTrigger activeTrigger) {
      this.activeTrigger = activeTrigger;
   }

   public void setActiveCondition(TriggerCondition activeCondition) {
   }

   public Location getTargetLocation() {
      return this.targetLocation;
   }

   public void setTargetLocation(Location targetLocation) {
      this.targetLocation = targetLocation;
   }

   public DungeonPlayerHotbar getCurrentHotbar() {
      return this.currentDungeonPlayerHotbar;
   }

   public boolean isChatListening() {
      return this.chatListening;
   }

   public void setChatListening(boolean chatListening) {
      this.chatListening = chatListening;
   }

   public DungeonFunction getCopiedFunction() {
      return this.copiedFunction;
   }

   public void setCopiedFunction(DungeonFunction copiedFunction) {
      this.copiedFunction = copiedFunction;
   }

   public boolean isCopying() {
      return this.isCopying;
   }

   public void setCopying(boolean isCopying) {
      this.isCopying = isCopying;
   }

   public boolean isCutting() {
      return this.isCutting;
   }

   public void setCutting(boolean isCutting) {
      this.isCutting = isCutting;
   }

   public Location getPos1() {
      return this.pos1;
   }

   public void setPos1(Location pos1) {
      this.pos1 = pos1;
   }

   public Location getPos2() {
      return this.pos2;
   }

   public void setPos2(Location pos2) {
      this.pos2 = pos2;
   }

   public boolean isAwaitingRoomName() {
      return this.awaitingRoomName;
   }

   public void setAwaitingRoomName(boolean awaitingRoomName) {
      this.awaitingRoomName = awaitingRoomName;
   }

   public DungeonRoomContainer getActiveRoom() {
      return this.activeRoom;
   }

   public void setActiveRoom(DungeonRoomContainer activeRoom) {
      this.activeRoom = activeRoom;
   }

   public Connector getActiveConnector() {
      return this.activeConnector;
   }

   public void setActiveConnector(Connector activeConnector) {
      this.activeConnector = activeConnector;
   }

   public ConnectorDoor getActiveDoor() {
      return this.activeDoor;
   }

   public void setActiveDoor(ConnectorDoor activeDoor) {
      this.activeDoor = activeDoor;
   }

   public boolean isConfirmRoomAction() {
      return this.confirmRoomAction;
   }

   public void setConfirmRoomAction(boolean confirmRoomAction) {
      this.confirmRoomAction = confirmRoomAction;
   }

   public Connector getCopiedConnector() {
      return this.copiedConnector;
   }

   public void setCopiedConnector(Connector copiedConnector) {
      this.copiedConnector = copiedConnector;
   }

   public boolean isAddingWhitelistEntry() {
      return this.addingWhitelistEntry;
   }

   public void setAddingWhitelistEntry(boolean addingWhitelistEntry) {
      this.addingWhitelistEntry = addingWhitelistEntry;
   }

   public boolean isEditingWhitelistEntry() {
      return this.editingWhitelistEntry;
   }

   public void setEditingWhitelistEntry(boolean editingWhitelistEntry) {
      this.editingWhitelistEntry = editingWhitelistEntry;
   }

   public boolean isRemovingWhitelistEntry() {
      return this.removingWhitelistEntry;
   }

   public void setRemovingWhitelistEntry(boolean removingWhitelistEntry) {
      this.removingWhitelistEntry = removingWhitelistEntry;
   }

   public boolean isReloadQueued() {
      return this.reloadQueued;
   }

}
