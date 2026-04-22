package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.text.ColorUtils;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Hotbar menu item that temporarily switches the player into chat-input mode.
 */
public abstract class ChatMenuItem extends MenuItem {
    private boolean cancelled;

    @Override
    public void onSelect(PlayerEvent event) {
        this.cancelled = false;
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        this.onSelect(player);
        if (!this.cancelled) {
            playerSession.setChatListening(true);
        }
    }

    /** Handles menu selection before switching to chat input mode. */
    public abstract void onSelect(Player player);

    @Override
    public void onChat(Player player, String message) {
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        this.onInput(player, ColorUtils.fullColor(message));
        playerSession.showHotbar(this.menu);
    }

    /** Handles chat input text captured for this menu item. */
    public abstract void onInput(Player player, String message);

    /** Returns whether chat mode activation was cancelled. */
    public boolean isCancelled() {
        return this.cancelled;
    }

    /** Sets whether chat mode activation should be cancelled. */
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
