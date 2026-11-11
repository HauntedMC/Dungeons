package nl.hauntedmc.dungeons.command;

import java.util.List;
import java.util.Locale;
import nl.hauntedmc.dungeons.localization.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Argument slicing and discovery helpers shared by command branches. */
final class CommandArguments {
    private CommandArguments() {}

    /** Runs shift args. */
    static String[] shiftArgs(String[] args) {
        return shiftArgs(args, 1);
    }

    /** Runs shift args. */
    static String[] shiftArgs(String[] args, int amount) {
        if (args.length <= amount) {
            return new String[0];
        }

        String[] shiftedArgs = new String[args.length - amount];
        System.arraycopy(args, amount, shiftedArgs, 0, shiftedArgs.length);
        return shiftedArgs;
    }

    /** Adds matching players. */
    static void addMatchingPlayers(List<String> options, String query) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.getName().toLowerCase(Locale.ROOT).contains(lowerQuery)) {
                options.add(target.getName());
            }
        }
    }

    /** Adds matching literal options. */
    static void addMatchingLiteralOptions(List<String> options, String query, String... literals) {
        String lowerQuery = query.toLowerCase(Locale.ROOT);
        for (String literal : literals) {
            if (literal.toLowerCase(Locale.ROOT).contains(lowerQuery)
                    && !options.contains(literal)) {
                options.add(literal);
            }
        }
    }

    /** Runs send usage list. */
    static void sendUsageList(CommandSender sender, String key) {
        LangUtils.sendMessageList(sender, key);
    }
}
