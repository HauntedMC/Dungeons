package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;

/**
 * Function that removes targeted players from the current dungeon instance.
 */
@AutoRegister(id = "dungeons.function.leave_dungeon")
@SerializableAs("dungeons.function.leave_dungeon")
public class LeaveDungeonFunction extends DungeonFunction {
    /**
     * Creates a new LeaveDungeonFunction instance.
     */
    public LeaveDungeonFunction(Map<String, Object> config) {
        super("Leave Dungeon", config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new LeaveDungeonFunction instance.
     */
    public LeaveDungeonFunction() {
        super("Leave Dungeon");
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        for (DungeonPlayerSession playerSession : targets) {
            this.instance.removePlayer(playerSession, false);
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.LADDER);
        functionButton.setDisplayName("&6Leave Dungeon");
        functionButton.addLore("&eImmediately takes the player");
        functionButton.addLore("&eor players out of the dungeon.");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {}
}
