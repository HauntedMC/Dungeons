package net.playavalon.mythicdungeons.player;

import io.papermc.paper.entity.TeleportFlag;
import io.papermc.paper.entity.TeleportFlag.EntityState;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.ClickEvent.Action;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.config.AsyncConfiguration;
import net.playavalon.mythicdungeons.api.events.dungeon.HotbarSetEvent;
import net.playavalon.mythicdungeons.api.generation.rooms.Connector;
import net.playavalon.mythicdungeons.api.generation.rooms.ConnectorDoor;
import net.playavalon.mythicdungeons.api.generation.rooms.DungeonRoomContainer;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonFunction;
import net.playavalon.mythicdungeons.api.parents.elements.DungeonTrigger;
import net.playavalon.mythicdungeons.api.parents.elements.TriggerCondition;
import net.playavalon.mythicdungeons.api.parents.instances.AbstractInstance;
import net.playavalon.mythicdungeons.api.parents.instances.InstancePlayable;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.dungeons.dungeontypes.DungeonClassic;
import net.playavalon.mythicdungeons.player.party.partysystem.MythicParty;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public final class MythicPlayer {
   private Player player;
   private AbstractInstance instance;
   private boolean awaitingDungeon;
   @Nullable
   private IDungeonParty dungeonParty;
   private File playerDataFile;
   private AsyncConfiguration playerData;
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
   private Hotbar savedHotbar;
   private DungeonFunction activeFunction;
   private DungeonTrigger activeTrigger;
   private TriggerCondition activeCondition;
   private Location targetLocation;
   private final List<Hotbar> previousHotbars;
   private Hotbar currentHotbar;
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
   private MythicParty mythicParty;
   private boolean partyChat;
   private Player inviteFrom;
   private ItemStack[] mvInventory;

   public MythicPlayer(Player player) {
      this.player = player;
      this.previousHotbars = new ArrayList<>();
      this.savedGameMode = Bukkit.getDefaultGameMode();
      this.rewardsInv = new ArrayList<>();
      this.playerData = new AsyncConfiguration(MythicDungeons.inst());
      this.playerDataFile = new File(MythicDungeons.inst().getDataFolder(), "globalplayerdata/" + player.getUniqueId() + ".yml");

      try {
         if (!this.playerDataFile.exists()) {
            this.playerDataFile.createNewFile();
         }

         this.playerData.load(this.playerDataFile);
         this.completedDungeons = (List<String>) this.playerData.getList("FinishedDungeons", new ArrayList());
      } catch (IOException var3) {
         MythicDungeons.inst()
            .getLogger()
            .info(Util.colorize("&cERROR :: Could not load player data file for '" + player.getName() + "'! Failed to create file."));
         var3.printStackTrace();
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
      if (MythicDungeons.inst().isEnabled()) {
         if (Bukkit.getPluginManager().getPlugin("Multiverse-Inventories") != null) {
            Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
               this.player.getInventory().setContents(this.savedInventory);
               this.player.getInventory().setArmorContents(this.savedArmor);
               this.player.setHealth(Math.min(this.savedHealth, this.player.getMaxHealth()));
               this.player.setFoodLevel(this.savedFood);
            }, 1L);
         } else {
            this.player.getInventory().setContents(this.savedInventory);
            this.player.getInventory().setArmorContents(this.savedArmor);
            this.player.setHealth(Math.min(this.savedHealth, this.player.getMaxHealth()));
            this.player.setFoodLevel(this.savedFood);
         }
      } else {
         this.player.getInventory().setContents(this.savedInventory);
         this.player.getInventory().setArmorContents(this.savedArmor);
      }
   }

   public void restoreExp() {
      if (MythicDungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
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
      if (MythicDungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
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
      this.savedHotbar = new Hotbar(this.player);
   }

   public void restoreHotbar() {
      HotbarSetEvent event = new HotbarSetEvent(this.savedHotbar, this);
      Bukkit.getPluginManager().callEvent(event);
      if (!event.isCancelled()) {
         if (this.savedHotbar != null) {
            this.savedHotbar.setHotbar(this.player.getInventory(), true);
            this.savedHotbar = null;
            this.player.playSound(this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
         }

         this.currentHotbar = null;
         this.previousHotbars.clear();
      }
   }

   public void switchHotbar(Hotbar hotbar) {
      this.saveHotbar();
      this.setHotbar(hotbar, true);
   }

   public void setHotbar(Hotbar hotbar) {
      this.setHotbar(hotbar, false);
   }

   public void setHotbar(final Hotbar hotbar, final boolean resetHand) {
      (new BukkitRunnable() {
         public void run() {
            HotbarSetEvent event = new HotbarSetEvent(hotbar, MythicPlayer.this);
            Bukkit.getPluginManager().callEvent(event);
            if (!event.isCancelled()) {
               if (hotbar != MythicPlayer.this.currentHotbar) {
                  MythicPlayer.this.previousHotbars.add(MythicPlayer.this.currentHotbar);
               }

               MythicPlayer.this.currentHotbar = hotbar;
               hotbar.setHotbar(MythicPlayer.this.player.getInventory(), resetHand);
               MythicPlayer.this.player.playSound(MythicPlayer.this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
               MythicPlayer.this.pos1 = null;
               MythicPlayer.this.pos2 = null;
            }
         }
      }).runTaskLater(MythicDungeons.inst(), 1L);
   }

   public Hotbar getPreviousHotbar() {
      return this.previousHotbars.size() <= 0 ? null : this.previousHotbars.get(this.previousHotbars.size() - 1);
   }

   public void previousHotbar() {
      this.previousHotbar(false);
   }

   public void previousHotbar(final boolean resetHand) {
      (new BukkitRunnable() {
         public void run() {
            Hotbar hotbar = MythicPlayer.this.getPreviousHotbar();
            if (hotbar == null) {
               MythicPlayer.this.restoreHotbar();
            } else {
               HotbarSetEvent event = new HotbarSetEvent(hotbar, MythicPlayer.this);
               Bukkit.getPluginManager().callEvent(event);
               if (!event.isCancelled()) {
                  MythicPlayer.this.previousHotbars.remove(MythicPlayer.this.previousHotbars.size() - 1);
                  MythicPlayer.this.currentHotbar = hotbar;
                  hotbar.setHotbar(MythicPlayer.this.player.getInventory(), resetHand);
                  MythicPlayer.this.player.playSound(MythicPlayer.this.player.getLocation(), "minecraft:item.armor.equip_gold", 1.0F, 1.2F);
               }
            }
         }
      }).runTaskLater(MythicDungeons.inst(), 1L);
   }

   public void sendToCheckpoint() {
      if (MythicDungeons.inst().isSupportsTeleportFlags()) {
         this.player.teleport(this.dungeonRespawn, new TeleportFlag[]{EntityState.RETAIN_PASSENGERS});
      } else {
         List<Entity> passengers = this.player.getPassengers();
         this.player.eject();
         this.player.teleport(this.dungeonRespawn);
         if (!passengers.isEmpty()) {
            this.player.addPassenger(passengers.get(0));
         }
      }
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

   public void setDungeonParty(@Nullable IDungeonParty party) {
      this.dungeonParty = party;
      if (party != null) {
         MythicDungeons.inst().getProviderManager().put(this.player);
      } else {
         this.setAwaitingDungeon(false);
      }
   }

   @Nullable
   public IDungeonParty getDungeonParty() {
      if (this.mythicParty != null) {
         return this.mythicParty;
      } else if (this.dungeonParty != null && this.hasParty()) {
         MythicDungeons.inst().getProviderManager().updatePartyPlayers(this.dungeonParty);
         return this.dungeonParty;
      } else {
         return null;
      }
   }

   public boolean hasParty() {
      if (this.mythicParty != null) {
         return true;
      } else if (this.dungeonParty == null) {
         return false;
      } else if (!this.dungeonParty.hasPlayer(this.player)) {
         this.dungeonParty.removePlayer(this.player);
         this.setAwaitingDungeon(false);
         return false;
      } else {
         return true;
      }
   }

   public void queueReload() {
      this.reloadQueued = true;
      Bukkit.getScheduler().runTaskLater(MythicDungeons.inst(), () -> {
         if (this.reloadQueued) {
            this.reloadQueued = false;
         }
      }, 200L);
   }

   public void unqueueReload() {
      this.reloadQueued = false;
   }

   public boolean isPartyChat() {
      return this.partyChat;
   }

   public void setPartyChat(boolean partyChat) {
      this.partyChat = partyChat;
   }

   public void togglePartyChat() {
      if (this.isPartyChat()) {
         this.setPartyChat(false);
         LangUtils.sendMessage(this.player, "party.chat.disable");
      } else {
         this.setPartyChat(true);
         LangUtils.sendMessage(this.player, "party.chat.enable");
      }
   }

   public Player getInviteFrom() {
      return this.inviteFrom;
   }

   public void setInviteFrom(final Player inviteFrom) {
      this.inviteFrom = inviteFrom;
      if (inviteFrom != null) {
         this.player.playSound(this.player, "entity.experience_orb.pickup", 1.0F, 1.0F);
         TextComponent debugPrefix = new TextComponent(TextComponent.fromLegacyText(LangUtils.getMessage("party.prefix", false) + " "));
         TextComponent message = new TextComponent(
            TextComponent.fromLegacyText(LangUtils.getMessage("party.invite.message", false, inviteFrom.getName()) + " ")
         );
         TextComponent joinParty = new TextComponent(TextComponent.fromLegacyText(LangUtils.getMessage("party.invite.join-prompt", false)));
         joinParty.setClickEvent(new ClickEvent(Action.RUN_COMMAND, "/dparty join"));
         debugPrefix.addExtra(message);
         debugPrefix.addExtra(joinParty);
         this.player.spigot().sendMessage(ChatMessageType.CHAT, debugPrefix);
         (new BukkitRunnable() {
            public void run() {
               if (MythicPlayer.this.getInviteFrom() != null) {
                  MythicPlayer.this.player.sendMessage(LangUtils.getMessage("party.invite.expired", inviteFrom.getName()));
                  MythicPlayer.this.setInviteFrom(null);
               }
            }
         }).runTaskLater(MythicDungeons.inst(), 4800L);
      }
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

   public AsyncConfiguration getPlayerData() {
      return this.playerData;
   }

   public List<String> getCompletedDungeons() {
      return this.completedDungeons;
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

   public float getSavedExp() {
      return this.savedExp;
   }

   public void setSavedExp(float savedExp) {
      this.savedExp = savedExp;
   }

   public int getSavedLevel() {
      return this.savedLevel;
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

   public Hotbar getSavedHotbar() {
      return this.savedHotbar;
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

   public TriggerCondition getActiveCondition() {
      return this.activeCondition;
   }

   public void setActiveCondition(TriggerCondition activeCondition) {
      this.activeCondition = activeCondition;
   }

   public Location getTargetLocation() {
      return this.targetLocation;
   }

   public void setTargetLocation(Location targetLocation) {
      this.targetLocation = targetLocation;
   }

   public List<Hotbar> getPreviousHotbars() {
      return this.previousHotbars;
   }

   public Hotbar getCurrentHotbar() {
      return this.currentHotbar;
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

   public MythicParty getMythicParty() {
      return this.mythicParty;
   }

   public void setMythicParty(MythicParty mythicParty) {
      this.mythicParty = mythicParty;
   }

   public ItemStack[] getMvInventory() {
      return this.mvInventory;
   }

   public void setMvInventory(ItemStack[] mvInventory) {
      this.mvInventory = mvInventory;
   }
}
