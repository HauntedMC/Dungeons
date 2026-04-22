package nl.hauntedmc.dungeons.util.command;

import java.util.Optional;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Typed command-input parsing helpers with localized validation errors. */
public final class InputUtils {

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
        try {
            double value = Double.parseDouble(string);
            return Optional.of(value);
        } catch (NumberFormatException exception) {
            LangUtils.sendMessage(player, "input.invalid-decimal");
            return Optional.empty();
        }
    }
}
