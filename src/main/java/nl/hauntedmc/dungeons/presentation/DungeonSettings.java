package nl.hauntedmc.dungeons.presentation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import nl.hauntedmc.dungeons.config.DungeonConfigView;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;

/** Player-facing view of the permissions enforced in a playable dungeon. */
public final class DungeonSettings {
    private DungeonSettings() {}

    public static void show(
            CommandSender viewer, DungeonDefinition dungeon, FileConfiguration config) {
        viewer.sendMessage(
                DungeonUi.heading(
                        DungeonUi.join(
                                label(viewer, "settings-heading"),
                                dungeon.getDisplayNameComponent())));
        GameMode mode;
        try {
            mode =
                    GameMode.valueOf(
                            config.getString("players.gamemode", "ADVENTURE")
                                    .toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            mode = GameMode.ADVENTURE;
        }
        String modeName = mode.name().toLowerCase(Locale.ROOT);
        send(
                viewer,
                metric(
                        viewer,
                        "game-mode",
                        Component.translatable(
                                "gameMode." + modeName,
                                mode.name().charAt(0) + modeName.substring(1))));
        boolean breakBlocks = DungeonConfigView.canBreakBlocks(config);
        boolean placeBlocks = DungeonConfigView.canPlaceBlocks(config);
        send(
                viewer,
                permission(
                        viewer,
                        "break-blocks",
                        breakBlocks,
                        dungeon.getBreakWhitelist(),
                        dungeon.getBreakBlacklist()));
        send(
                viewer,
                permission(
                        viewer,
                        "place-blocks",
                        placeBlocks,
                        dungeon.getPlaceWhitelist(),
                        dungeon.getPlaceBlacklist()));
        if (dungeon.isBreakPlacedBlocks()) send(viewer, permission(viewer, "break-placed", true));
        send(
                viewer,
                permission(
                        viewer,
                        "place-entities",
                        config.getBoolean("rules.building.place_entities", false)),
                permission(viewer, "pvp", config.getBoolean("rules.combat.pvp", false)));
        send(
                viewer,
                permission(viewer, "buckets", DungeonConfigView.canUseBuckets(config)),
                permission(viewer, "pearls", DungeonConfigView.canUseEnderPearls(config)));
        send(
                viewer,
                permission(
                        viewer, "chorus", config.getBoolean("rules.movement.chorus_fruit", false)));
        var protectedGear = new ArrayList<Component>();
        if (DungeonConfigView.isArmorDurabilityLossPrevented(config))
            protectedGear.add(label(viewer, "armor"));
        if (DungeonConfigView.isWeaponDurabilityLossPrevented(config))
            protectedGear.add(label(viewer, "weapons"));
        if (DungeonConfigView.isToolDurabilityLossPrevented(config))
            protectedGear.add(label(viewer, "tools"));
        if (!protectedGear.isEmpty())
            send(viewer, metric(viewer, "durability", DungeonUi.join(protectedGear)));
    }

    private static Component permission(CommandSender viewer, String key, boolean allowed) {
        return permission(viewer, key, allowed, List.of(), List.of());
    }

    private static Component permission(
            CommandSender viewer,
            String key,
            boolean allowed,
            List<Material> whitelist,
            List<Material> blacklist) {
        var result =
                metric(
                        viewer,
                        key,
                        label(viewer, allowed ? "allowed" : "blocked")
                                .color(allowed ? NamedTextColor.GREEN : NamedTextColor.RED));
        var blocked =
                blacklist.stream().filter(material -> !material.isLegacy()).distinct().toList();
        var allowedExceptions =
                whitelist.stream()
                        .filter(material -> !material.isLegacy())
                        .filter(material -> !blocked.contains(material))
                        .distinct()
                        .toList();
        if (allowedExceptions.isEmpty() && blocked.isEmpty()) return result;
        var hover = Component.text();
        if (!allowedExceptions.isEmpty())
            hover.append(materials(viewer, "allowed", allowedExceptions));
        if (!allowedExceptions.isEmpty() && !blocked.isEmpty()) hover.append(Component.newline());
        if (!blocked.isEmpty()) hover.append(materials(viewer, "blocked", blocked));
        return DungeonUi.join(result, label(viewer, "exceptions").color(NamedTextColor.AQUA))
                .hoverEvent(hover.build());
    }

    private static Component materials(CommandSender viewer, String key, List<Material> materials) {
        var list = Component.text();
        list.append(label(viewer, key).color(NamedTextColor.GOLD));
        materials.stream()
                .sorted()
                .limit(12)
                .forEach(
                        material ->
                                list.append(Component.newline())
                                        .append(
                                                Component.translatable(material.translationKey())
                                                        .color(NamedTextColor.WHITE)));
        if (materials.size() > 12)
            list.append(Component.newline())
                    .append(Component.text("+" + (materials.size() - 12), NamedTextColor.GRAY));
        return list.build();
    }

    private static Component metric(CommandSender viewer, String key, Component value) {
        return DungeonUi.metric(label(viewer, key).color(NamedTextColor.GRAY), value);
    }

    private static Component label(CommandSender viewer, String key) {
        return LangUtils.component("experience.rules-ui." + key, viewer, false);
    }

    private static void send(CommandSender viewer, Component... rows) {
        viewer.sendMessage(DungeonUi.detail(DungeonUi.join(rows)));
    }
}
