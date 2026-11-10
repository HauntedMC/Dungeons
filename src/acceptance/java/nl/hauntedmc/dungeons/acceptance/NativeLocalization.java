package nl.hauntedmc.dungeons.acceptance;

import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Validate installed localization resources and language selection through the actual facade. */
final class NativeLocalization {
    private NativeLocalization() {}

    static CompletionStage<Void> run(AcceptancePlugin host) {
        var plugin = host.runtime().environment().plugin();
        var directory = plugin.getDataFolder().toPath();
        AcceptancePlugin.require(
                plugin.getClass().getResource("/messages.yml") == null,
                "Obsolete root messages resource is still packaged");
        AcceptancePlugin.require(
                !Files.exists(directory.resolve("messages.yml")),
                "Fresh startup created a duplicate root messages file");
        AcceptancePlugin.require(
                Files.isRegularFile(directory.resolve("lang/messages.yml")),
                "Default messages are missing");
        AcceptancePlugin.require(
                Files.isRegularFile(directory.resolve("lang/messages_EN.yml")),
                "Example English translation was not installed");
        var english = audience(Locale.ENGLISH);
        var dutch = audience(Locale.forLanguageTag("nl-NL"));
        var key = "commands.play.dungeon-not-found";
        AcceptancePlugin.require(
                ComponentUtils.plainText(
                                LangUtils.component(
                                        key, english, false, Map.of("dungeon", "crypt")))
                        .equals("Dungeon 'crypt' does not exist."),
                "Bundled English override was not selected");
        AcceptancePlugin.require(
                ComponentUtils.plainText(
                                LangUtils.component(key, dutch, false, Map.of("dungeon", "crypt")))
                        .equals("Dungeon 'crypt' bestaat niet."),
                "Dutch messages did not fall back to the default file");
        AcceptancePlugin.require(
                ComponentUtils.plainText(
                                LangUtils.component(
                                        key,
                                        Bukkit.getConsoleSender(),
                                        false,
                                        Map.of("dungeon", "crypt")))
                        .equals("Dungeon 'crypt' bestaat niet."),
                "Console output incorrectly used a player language override");
        AcceptancePlugin.require(
                LangUtils.components("commands.team.help.lines", english).size() == 9,
                "Translated list lost team commands");
        return CompletableFuture.completedFuture(null);
    }

    private static Player audience(Locale locale) {
        UUID id = UUID.randomUUID();
        return (Player)
                Proxy.newProxyInstance(
                        Player.class.getClassLoader(),
                        new Class<?>[] {Player.class},
                        (proxy, method, args) ->
                                switch (method.getName()) {
                                    case "getUniqueId" -> id;
                                    case "locale" -> locale;
                                    case "toString" -> "LocalizationAudience";
                                    case "hashCode" -> System.identityHashCode(proxy);
                                    case "equals" -> proxy == args[0];
                                    default ->
                                            throw new UnsupportedOperationException(
                                                    method.getName());
                                });
    }
}
