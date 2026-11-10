package nl.hauntedmc.dungeons.gui.menu;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.staff.StaffEditGuard;
import nl.hauntedmc.dungeons.util.metadata.PersistedFieldSupport;
import nl.hauntedmc.dungeons.util.text.ColorUtils;

import java.util.Collection;

/** Shared access-controlled windows and persisted-field descriptions for editor workflows. */
final class EditorMenuSupport {
    private EditorMenuSupport() {}

    static GuiWindow editorWindow(String namespace, int size, String title) {
        var window = new GuiWindow(namespace, size, title);
        window.setAccessCheck(
                player ->
                        StaffEditGuard.editor(
                                player, RuntimeContext.playerSessions().get(player)));
        return window;
    }

    /** Adds field metadata and current value details to button lore. */
    static void addPersistedFieldLore(
            Button button, Object target, boolean formatTriggerValues, String logContext) {
        for (PersistedFieldSupport.FieldMetadata metadata :
                PersistedFieldSupport.editorVisibleFields(target.getClass())) {
            try {
                Object value = metadata.field().get(target);
                if (value == null || value instanceof Collection<?>) {
                    continue;
                }

                if (formatTriggerValues && value instanceof DungeonTrigger trigger) {
                    button.addLore(
                            ColorUtils.colorize(" &eTrigger: &7" + trigger.getDisplayName()));
                } else {
                    button.addLore(ColorUtils.colorize(" &e" + metadata.name() + ": &7" + value));
                }
            } catch (IllegalAccessException exception) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .error(
                                "Failed to inspect {} field '{}' on '{}'.",
                                logContext,
                                metadata.name(),
                                target.getClass().getSimpleName(),
                                exception);
            }
        }
    }
}
