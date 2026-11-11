package nl.hauntedmc.dungeons.gui.menu;

import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;

/** Hotbar menu builders used by the dungeon editor. */
public class HotbarMenus {

    public static void initializeRoomEditMenu() {
        RoomEditHotbar.initializeRoomEditMenu();
    }

    public static void initializeRoomRulesMenu() {
        RoomEditHotbar.initializeRoomRulesMenu();
    }

    public static PlayerHotbarMenu getRoomEditMenu() {
        return RoomEditHotbar.getRoomEditMenu();
    }

    public static void initializeFunctionEditMenu() {
        FunctionEditHotbar.initializeFunctionEditMenu();
    }

    public static PlayerHotbarMenu getFunctionEditMenu() {
        return FunctionEditHotbar.getFunctionEditMenu();
    }
}
