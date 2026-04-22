package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.ArrayList;
import java.util.List;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;

/**
 * Base model for one hotbar editor action slot.
 */
public abstract class MenuItem {
    public static final MenuItem BACK =
            new MenuItem() {
                @Override
                public void buildButton() {
                    this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                    this.button.setDisplayName("&c&lBACK");
                    this.button.addLore("&7Go back one editor menu.");
                    this.button.addLore("&7Shortcut: Q");
                }

                @Override
                public void onSelect(PlayerEvent event) {
                    Player player = event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    playerSession.setActiveTrigger(null);
                    playerSession.restorePreviousHotbar(true);
                }
            };

    public static final MenuItem CLOSE =
            new MenuItem() {
                @Override
                public void buildButton() {
                    this.button = new MenuButton(Material.BARRIER);
                    this.button.setDisplayName("&4&lCLOSE");
                    this.button.addLore("&7Close the editor hotbar.");
                    this.button.addLore("&7Shortcut: F");
                }

                @Override
                public void onSelect(PlayerEvent event) {
                    Player player = event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    playerSession.setActiveTrigger(null);
                    playerSession.restoreCapturedHotbar();
                }
            };

    protected PlayerHotbarMenu menu;
    protected MenuButton button;
    protected final List<MenuAction<PlayerEvent>> selectActions = new ArrayList<>();
    protected final List<MenuAction<PlayerEvent>> clickActions = new ArrayList<>();
    protected final List<ChatInputAction> chatActions = new ArrayList<>();
    protected final List<MenuAction<PlayerItemHeldEvent>> hoverActions = new ArrayList<>();
    protected final List<MenuAction<PlayerItemHeldEvent>> unhoverActions = new ArrayList<>();

    /** Builds or rebuilds the button representation for this menu item. */
    public abstract void buildButton();

    /** Handles right-click/primary selection for this menu item. */
    public abstract void onSelect(PlayerEvent event);

    /** Handles left-click interaction for this menu item. */
    public void onClick(PlayerEvent event) {}

    /** Handles chat input while this menu item is active. */
    public void onChat(Player player, String message) {}

    /** Handles hover enter for this menu item's slot. */
    public void onHover(PlayerItemHeldEvent event) {}

    /** Handles hover leave for this menu item's slot. */
    public void onUnhover(PlayerItemHeldEvent event) {}

    /** Runs select handler plus registered select actions. */
    public void runSelectActions(PlayerEvent event) {
        this.onSelect(event);
        for (MenuAction<PlayerEvent> action : this.selectActions) {
            action.run(event);
        }
    }

    /** Runs click handler plus registered click actions. */
    public void runClickActions(PlayerEvent event) {
        this.onClick(event);
        for (MenuAction<PlayerEvent> action : this.clickActions) {
            action.run(event);
        }
    }

    /** Runs chat handler plus registered chat actions. */
    public void runChatActions(Player player, String message) {
        this.onChat(player, message);
        for (ChatInputAction action : this.chatActions) {
            action.run(player, message);
        }
    }

    /** Runs hover-enter handler plus registered hover actions. */
    public void runHoverActions(PlayerItemHeldEvent event) {
        this.onHover(event);
        for (MenuAction<PlayerItemHeldEvent> action : this.hoverActions) {
            action.run(event);
        }
    }

    /** Runs hover-leave handler plus registered unhover actions. */
    public void runUnhoverActions(PlayerItemHeldEvent event) {
        this.onUnhover(event);
        for (MenuAction<PlayerItemHeldEvent> action : this.unhoverActions) {
            action.run(event);
        }
    }

    /** Adds a select action callback. */
    public void addSelectAction(MenuAction<PlayerEvent> action) {
        this.selectActions.add(action);
    }

    /** Adds a click action callback. */
    public void addClickAction(MenuAction<PlayerEvent> action) {
        this.clickActions.add(action);
    }

    /** Adds a chat action callback. */
    public void addChatAction(ChatInputAction action) {
        this.chatActions.add(action);
    }

    /** Adds a hover-enter action callback. */
    public void addHoverAction(MenuAction<PlayerItemHeldEvent> action) {
        this.hoverActions.add(action);
    }

    /** Adds a hover-leave action callback. */
    public void addUnhoverAction(MenuAction<PlayerItemHeldEvent> action) {
        this.unhoverActions.add(action);
    }

    /** Returns the owning hotbar menu. */
    public PlayerHotbarMenu getMenu() {
        return this.menu;
    }

    /** Sets the owning hotbar menu. */
    public void setMenu(PlayerHotbarMenu menu) {
        this.menu = menu;
    }

    /** Returns the current button representation. */
    public MenuButton getButton() {
        return this.button;
    }

    @FunctionalInterface
    public interface ChatInputAction {
        /** Handles one chat input message for this item. */
        void run(Player player, String message);
    }
}
