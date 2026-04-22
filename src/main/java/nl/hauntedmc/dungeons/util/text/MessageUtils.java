package nl.hauntedmc.dungeons.util.text;

import java.time.Duration;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** Title and clickable-message helpers for player messaging. */
public final class MessageUtils {

    /** Shows a title/subtitle with fade timings in ticks. */
    public static void showTitle(
            Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        Title.Times times =
                Title.Times.times(
                        Duration.ofMillis(fadeIn * 50L),
                        Duration.ofMillis(stay * 50L),
                        Duration.ofMillis(fadeOut * 50L));
        Title adventureTitle =
                Title.title(ComponentUtils.component(title), ComponentUtils.component(subtitle), times);
        player.showTitle(adventureTitle);
    }

    /** Resets any active title for a player. */
    public static void resetTitle(Player player) {
        player.resetTitle();
    }

    /** Sends a clickable open-url message. */
    public static void sendClickableLink(Player player, String message, String url) {
        player.sendMessage(ComponentUtils.component(message).clickEvent(ClickEvent.openUrl(url)));
    }

    /** Sends a clickable run-command message. */
    public static void sendClickableCommand(Player player, String message, String command) {
        player.sendMessage(
                ComponentUtils.component(message).clickEvent(ClickEvent.runCommand("/" + command)));
    }
}
