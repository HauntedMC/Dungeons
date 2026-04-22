package nl.hauntedmc.dungeons.bootstrap;

import nl.hauntedmc.dungeons.gui.menu.EditorMenus;
import nl.hauntedmc.dungeons.gui.menu.HotbarMenus;
import nl.hauntedmc.dungeons.gui.menu.PlayMenus;
import nl.hauntedmc.dungeons.gui.menu.RoomMenus;
import nl.hauntedmc.dungeons.runtime.DungeonsRuntime;

/**
 * Initializes the static menu definitions used by the current GUI layer.
 *
 * <p>The GUI layer is still largely static, so startup keeps that initialization contained in one
 * explicit bootstrap stage instead of spreading it across constructors.</p>
 */
final class MenuBootstrap {
    private final DungeonsRuntime runtime;

    /**
     * Creates the menu bootstrap stage.
     */
    MenuBootstrap(DungeonsRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Initializes all shared GUI and hotbar menus.
     */
    void initializeMenus() {
        EditorMenus.initializeFunctionMenu();
        EditorMenus.initializeTriggerMenu();
        EditorMenus.initializeConditionsMenus();
        EditorMenus.initializeGateTriggerMenus();
        EditorMenus.initializeItemSelectTriggerMenu();
        EditorMenus.initializeItemSelectFunctionMenu();
        PlayMenus.initializeRewardMenu();
        PlayMenus.initializeRevivalMenu();
        EditorMenus.initializeCompositeFunctionMenus();
        RoomMenus.initializeConnectorWhitelistMenu();
        HotbarMenus.initializeFunctionEditMenu();
        HotbarMenus.initializeRoomEditMenu();
        HotbarMenus.initializeRoomRulesMenu();
        this.runtime.environment().logger().info("Initialized GUI menus.");
    }
}
