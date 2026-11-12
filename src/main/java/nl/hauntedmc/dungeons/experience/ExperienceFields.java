package nl.hauntedmc.dungeons.experience;

import java.util.function.Consumer;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Consistent editor fields for the small graph adapters. */
public final class ExperienceFields {
    private ExperienceFields() {}

    public static void field(
            PlayerHotbarMenu menu,
            String label,
            Supplier<String> current,
            Consumer<String> update) {
        menu.addMenuItem(
                new ChatMenuItem() {
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.WRITABLE_BOOK);
                        this.button.setDisplayName("&d" + label);
                        this.button.addLore("&7" + current.get());
                    }

                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendRaw(player, label + ": " + current.get());
                    }

                    @Override
                    public void onInput(Player player, String input) {
                        update.accept(input);
                    }
                });
    }
}
