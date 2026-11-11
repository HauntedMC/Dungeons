package nl.hauntedmc.dungeons.util.command;

import java.util.Optional;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Typed command-input parsing helpers with localized validation errors. */
public final class InputUtils {
    private InputUtils() {}

    /** Parses integer input and reports localization-backed validation failure. */
    public static Optional<Integer> readIntegerInput(CommandSender sender, String string) {
        try {
            int value = Integer.parseInt(string);
            return Optional.of(value);
        } catch (NumberFormatException exception) {
            LangUtils.sendMessage(sender, "input.invalid-integer");
            return Optional.empty();
        }
    }

    /** Parses decimal input and reports localization-backed validation failure. */
    public static Optional<Double> readDoubleInput(Player player, String string) {
        if (string == null) {
            LangUtils.sendMessage(player, "input.invalid-decimal");
            return Optional.empty();
        }
        try {
            double value = Double.parseDouble(string);
            if (!Double.isFinite(value)) {
                throw new NumberFormatException("A finite decimal is required.");
            }
            return Optional.of(value);
        } catch (NumberFormatException exception) {
            LangUtils.sendMessage(player, "input.invalid-decimal");
            return Optional.empty();
        }
    }
}
