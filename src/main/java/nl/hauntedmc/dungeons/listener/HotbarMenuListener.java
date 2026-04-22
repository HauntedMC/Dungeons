package nl.hauntedmc.dungeons.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.plugin.DungeonsPlugin;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.runtime.player.PlayerSessionRegistry;
import nl.hauntedmc.dungeons.util.config.PluginConfigView;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Global listener for the custom editor hotbar-menu interaction model.
 */
public final class HotbarMenuListener implements Listener {
    private final DungeonsPlugin plugin;
    private final PlayerSessionRegistry playerManager;
    private final Set<UUID> pendingChatInputs = ConcurrentHashMap.newKeySet();

    /**
     * Creates the hotbar-menu listener with runtime dependencies.
     */
    public HotbarMenuListener(DungeonsPlugin plugin, PlayerSessionRegistry playerManager) {
        this.plugin = plugin;
        this.playerManager = playerManager;
    }

    /** Handles right-click selection for the currently highlighted menu item. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotSelect(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.RIGHT_CLICK_AIR) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu == null) {
            return;
        }

        menu.setSelected(player.getInventory().getHeldItemSlot());
        MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
        if (menuItem == null) {
            return;
        }

        event.setCancelled(true);
        this.playMenuSound(player);
        playerSession.setChatListening(false);
        menuItem.runSelectActions(event);
        menu.renderMenu();
    }

    /** Handles entity right-click selection for the currently highlighted menu item. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotSelect(PlayerInteractAtEntityEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu == null) {
            return;
        }

        menu.setSelected(player.getInventory().getHeldItemSlot());
        MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
        if (menuItem == null) {
            return;
        }

        event.setCancelled(true);
        this.playMenuSound(player);
        playerSession.setChatListening(false);
        menuItem.runSelectActions(event);
        menu.renderMenu();
    }

    /** Handles left-click actions for the currently highlighted menu item. */
    @EventHandler(priority = EventPriority.LOW)
    public void onSlotClick(PlayerInteractEvent event) {
        if (event.getAction() != Action.LEFT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_AIR) {
            return;
        }

        if (event.getHand() == EquipmentSlot.OFF_HAND) {
            return;
        }

        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu == null) {
            return;
        }

        menu.setSelected(player.getInventory().getHeldItemSlot());
        MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
        if (menuItem == null) {
            return;
        }

        event.setCancelled(true);
        this.playMenuSound(player);
        playerSession.setChatListening(false);
        menuItem.runClickActions(event);
        menu.renderMenu();
    }

    /**
     * Updates menu hover state when the held hotbar slot changes.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onHover(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu == null) {
            if (this.isPlainEditMode(playerSession)) {
                Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
            }
            return;
        }

        MenuItem previousItem = menu.getMenuItems().get(event.getPreviousSlot());
        if (previousItem != null) {
            previousItem.runUnhoverActions(event);
        }

        MenuItem newItem = menu.getMenuItems().get(event.getNewSlot());
        if (newItem != null) {
            newItem.runHoverActions(event);
        }

        menu.renderMenu();
    }

    /**
     * Routes chat text to chat-aware menu items.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent event) {
        String message = ComponentUtils.plainText(event.originalMessage());
        if (this.handleMenuChat(event.getPlayer().getUniqueId(), message, event.isAsynchronous())) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents inventory interactions from modifying managed hotbar menu slots.
     */
    @EventHandler
    public void onClickHotbarItem(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu != null) {
            if (this.touchesManagedHotbar(event.getSlot(), event.getHotbarButton())) {
                event.setCancelled(true);
                return;
            }

            if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() <= 8) {
                ItemStack newItem = menu.getItem(event.getSlot());
                if (newItem != null && newItem.getType() != Material.AIR) {
                    event.setCancelled(true);
                    player.closeInventory();
                }
            }
            return;
        }

        if (!this.isPlainEditMode(playerSession)) {
            return;
        }

        if (this.touchesProtectedEditTool(
                event.getSlot(), event.getHotbarButton(), event.getCurrentItem(), event.getCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
        }
    }

    /**
     * Creative inventory variant of managed hotbar protection.
     */
    @EventHandler
    public void onClickHotbarItem(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu != null) {
            if (this.touchesManagedHotbar(event.getSlot(), event.getHotbarButton())) {
                event.setCancelled(true);
                return;
            }

            if (event.getClickedInventory() instanceof PlayerInventory && event.getSlot() <= 8) {
                ItemStack newItem = menu.getItem(event.getSlot());
                if (newItem != null && newItem.getType() != Material.AIR) {
                    event.setCancelled(true);
                    player.closeInventory();
                }
            }
            return;
        }

        if (!this.isPlainEditMode(playerSession)) {
            return;
        }

        if (this.touchesProtectedEditTool(
                event.getSlot(), event.getHotbarButton(), event.getCurrentItem(), event.getCursor())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
        }
    }

    /**
     * Prevents drag interactions from rewriting managed hotbar or protected tool slots.
     */
    @EventHandler
    public void onDragHotbarItem(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu != null) {
            if (!(event.getInventory() instanceof PlayerInventory)) {
                return;
            }

            for (int slot : event.getInventorySlots()) {
                if (slot <= 8) {
                    ItemStack newItem = menu.getItem(slot);
                    if (newItem != null && newItem.getType() != Material.AIR) {
                        event.setCancelled(true);
                        player.closeInventory();
                        return;
                    }
                }
            }
            return;
        }

        if (!this.isPlainEditMode(playerSession)) {
            return;
        }

        for (int slot : event.getInventorySlots()) {
            if (this.isProtectedEditToolSlot(slot)) {
                event.setCancelled(true);
                Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
                return;
            }
        }
    }

    /**
     * Uses item drop as the "back" action while a hotbar menu is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDropMenuItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu != null) {
            event.setCancelled(true);
            playerSession.setChatListening(false);
            playerSession.restorePreviousHotbar(true);
            LangUtils.sendMessage(player, "editor.session.hotbar.went-back");
            this.playMenuSound(player);
            return;
        }

        if (!this.isPlainEditMode(playerSession)) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();
        if (this.isEditorTool(dropped)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
        }
    }

    /**
     * Uses hand-swap as the "close menu" action while a hotbar menu is open.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = this.playerManager.get(player);
        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu != null) {
            event.setCancelled(true);
            playerSession.setChatListening(false);
            playerSession.restoreCapturedHotbar();
            LangUtils.sendMessage(player, "editor.session.hotbar.closed");
            this.playMenuSound(player);
            return;
        }

        if (!this.isPlainEditMode(playerSession)) {
            return;
        }

        if (this.isEditorTool(event.getMainHandItem()) || this.isEditorTool(event.getOffHandItem())) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(this.plugin, playerSession::ensureEditModeTools);
        }
    }

    /**
     * Returns the currently active hotbar menu for a player session.
     */
    private PlayerHotbarMenu getActiveMenu(DungeonPlayerSession playerSession) {
        if (playerSession == null) {
            return null;
        }

        return playerSession.getCurrentHotbar() instanceof PlayerHotbarMenu menu ? menu : null;
    }

    /**
     * Returns whether the session is in edit mode without an active hotbar menu.
     */
    private boolean isPlainEditMode(DungeonPlayerSession playerSession) {
        return playerSession != null
                && playerSession.isEditMode()
                && playerSession.getCurrentHotbar() == null;
    }

    /**
     * Returns whether an inventory action targets managed hotbar slots.
     */
    private boolean touchesManagedHotbar(int slot, int hotbarButton) {
        return slot <= 8 || (hotbarButton >= 0 && hotbarButton <= 8);
    }

    /**
     * Returns whether an inventory action touches protected editor tool items.
     */
    private boolean touchesProtectedEditTool(
            int slot, int hotbarButton, ItemStack current, ItemStack cursor) {
        return this.isProtectedEditToolSlot(slot)
                || this.isProtectedEditToolSlot(hotbarButton)
                || this.isEditorTool(current)
                || this.isEditorTool(cursor);
    }

    /**
     * Returns whether the given slot is reserved for editor tools.
     */
    private boolean isProtectedEditToolSlot(int slot) {
        if (slot < 0) {
            return false;
        }

        int functionSlot = PluginConfigView.getFunctionToolSlot(this.plugin.getConfig());
        int roomSlot = PluginConfigView.getRoomToolSlot(this.plugin.getConfig());
        return slot == functionSlot || slot == roomSlot;
    }

    /**
     * Returns whether an item is one of the editor tool items.
     */
    private boolean isEditorTool(ItemStack item) {
        return ItemUtils.isFunctionTool(item) || ItemUtils.isRoomTool(item);
    }

    /**
     * Routes chat actions to the main thread when chat events are asynchronous.
     */
    private boolean handleMenuChat(UUID playerId, String message, boolean asynchronous) {
        if (!asynchronous) {
            return this.handleMenuChatSync(playerId, message);
        }

        try {
            // Menu actions can mutate inventory and hotbar state, so they always execute on
            // the Bukkit main thread.
            return Bukkit.getScheduler()
                    .callSyncMethod(this.plugin, () -> this.handleMenuChatSync(playerId, message))
                    .get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            Throwable root = exception.getCause() == null ? exception : exception.getCause();
            this.plugin
                    .getSLF4JLogger()
                    .error("Failed to process hotbar chat input for player '{}'.", playerId, root);
            return false;
        }
    }

    /**
     * Processes chat input for players currently listening through a selected menu item.
     */
    private boolean handleMenuChatSync(UUID playerId, String message) {
        Player player = Bukkit.getPlayer(playerId);
        if (player == null || !player.isOnline()) {
            return false;
        }

        DungeonPlayerSession playerSession = this.playerManager.get(playerId);
        if (playerSession == null) {
            return false;
        }

        PlayerHotbarMenu menu = this.getActiveMenu(playerSession);
        if (menu == null || !playerSession.isChatListening()) {
            return false;
        }

        MenuItem menuItem = menu.getMenuItems().get(menu.getSelected());
        if (menuItem == null) {
            return false;
        }

        if (!this.pendingChatInputs.add(playerId)) {
            return true;
        }

        try {
            if (playerSession.isChatListening()) {
                menuItem.runChatActions(player, message);
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5F, 1.2F);
            }
        } finally {
            playerSession.setChatListening(false);
            menu.renderMenu();
            this.pendingChatInputs.remove(playerId);
        }

        return true;
    }

    /**
     * Plays the shared feedback sound used for hotbar-menu interactions.
     */
    private void playMenuSound(Player player) {
        player.playSound(player.getLocation(), Sound.ITEM_LODESTONE_COMPASS_LOCK, 1.0F, 1.2F);
    }
}
