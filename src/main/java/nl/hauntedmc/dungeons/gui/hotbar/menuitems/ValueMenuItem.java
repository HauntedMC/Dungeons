package nl.hauntedmc.dungeons.gui.hotbar.menuitems;

import java.util.function.Consumer;
import java.util.function.Supplier;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/** Shared text control for simple mechanic settings; rejected input leaves the value unchanged. */
public final class ValueMenuItem extends ChatMenuItem {
    private final String label;
    private final Supplier<?> value;
    private final Consumer<String> update;

    public ValueMenuItem(String label, Supplier<?> value, Consumer<String> update) {
        this.label = label;
        this.value = value;
        this.update = update;
    }

    @Override
    public void buildButton() {
        this.button = new MenuButton(Material.PAPER);
        this.button.setDisplayName("&d&l" + label);
        this.button.addLore("&7" + String.valueOf(value.get()));
    }

    @Override
    public void onSelect(Player player) {
        LangUtils.sendMessage(
                player,
                "mechanic-editor.ask-value",
                LangUtils.placeholder("setting", label),
                LangUtils.placeholder("value", String.valueOf(value.get())));
    }

    @Override
    public void onInput(Player player, String message) {
        try {
            update.accept(message.trim());
            LangUtils.sendMessage(
                    player,
                    "mechanic-editor.value-set",
                    LangUtils.placeholder("setting", label),
                    LangUtils.placeholder("value", String.valueOf(value.get())));
        } catch (IllegalArgumentException exception) {
            LangUtils.sendMessage(player, "mechanic-editor.invalid-value");
        }
    }

    public static int nonNegativeInt(String text) {
        int result = Integer.parseInt(text);
        if (result < 0) throw new IllegalArgumentException();
        return result;
    }

    public static double nonNegativeDouble(String text) {
        double result = Double.parseDouble(text);
        if (!Double.isFinite(result) || result < 0) throw new IllegalArgumentException();
        return result;
    }
}
