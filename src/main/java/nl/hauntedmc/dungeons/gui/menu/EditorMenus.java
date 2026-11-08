package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;

/**
 * Inventory menu builders used by the dungeon editor.
 */
public class EditorMenus {
    public static GuiWindow editorWindow(String namespace, int size, String title) {
        return EditorMenuSupport.editorWindow(namespace, size, title);
    }

    public static void initializeConditionsMenus() {
        ConditionEditorMenus.initializeConditionsMenus();
    }

    public static void initializeGateTriggerMenus() {
        GateTriggerEditorMenus.initializeGateTriggerMenus();
    }

    public static void initializeCompositeFunctionMenus() {
        CompositeFunctionEditorMenus.initializeCompositeFunctionMenus();
    }

    public static void initializeFunctionMenu() {
        FunctionBrowserMenus.initializeFunctionMenu();
    }

    public static void initializeItemSelectFunctionMenu() {
        FunctionBrowserMenus.initializeItemSelectFunctionMenu();
    }

    public static void initializeTriggerMenu() {
        TriggerBrowserMenus.initializeTriggerMenu();
    }

    public static void initializeItemSelectTriggerMenu() {
        TriggerBrowserMenus.initializeItemSelectTriggerMenu();
    }

}
