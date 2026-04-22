package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Hotbar menu item that immediately re-renders the current menu after selection.
 */
public abstract class ToggleMenuItem extends MenuItem {
    @Override
    public void onSelect(PlayerEvent event) {
        Player player = event.getPlayer();
        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
        this.onSelect(player);
        playerSession.showHotbar(this.menu);
    }

    /** Handles menu selection while ensuring the menu is immediately re-rendered. */
    public abstract void onSelect(Player player);
}
