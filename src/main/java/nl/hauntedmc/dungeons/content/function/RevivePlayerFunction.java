package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.entity.EntityUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that revives targeted players in a playable instance.
 */
@AutoRegister(id = "dungeons.function.revive_player")
@SerializableAs("dungeons.function.revive_player")
public class RevivePlayerFunction extends DungeonFunction {
    @PersistedField private int maxRevives = 1;
    @PersistedField private int livesAfterRevival = 1;
    private int currentRevives = 0;

    /**
     * Creates a new RevivePlayerFunction instance.
     */
    public RevivePlayerFunction(Map<String, Object> config) {
        super("Reviver", config);
        this.setTargetType(FunctionTargetType.PLAYER);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new RevivePlayerFunction instance.
     */
    public RevivePlayerFunction() {
        super("Reviver");
        this.setTargetType(FunctionTargetType.PLAYER);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (!targets.isEmpty()) {
            DungeonPlayerSession playerSession = targets.getFirst();
            Player player = playerSession.getPlayer();
            if (this.maxRevives != 0 && this.currentRevives >= this.maxRevives) {
                LangUtils.sendMessage(player, "instance.play.functions.reviver.no-uses");
            } else {
                if (this.maxRevives == 0) {
                    LangUtils.sendMessage(player, "instance.play.functions.reviver.infinite-uses");
                } else {
                    LangUtils.sendMessage(
                            player,
                            "instance.play.functions.reviver.uses-remaining",
                            LangUtils.placeholder("uses", String.valueOf(this.maxRevives - this.currentRevives)));
                }

                playerSession.setActiveFunction(this);
                RuntimeContext.guiService().openGui(player, "revivalmenu");
            }
        }
    }

    /**
     * Performs revive player.
     */
    public void revivePlayer(DungeonPlayerSession playerSession, DungeonPlayerSession reviver) {
        if (this.maxRevives != 0 && this.currentRevives >= this.maxRevives) {
            LangUtils.sendMessage(reviver.getPlayer(), "instance.play.functions.reviver.no-uses");
        } else {
            Player player = playerSession.getPlayer();
            if (playerSession.getInstance() != null && playerSession.getInstance() == this.instance) {
                PlayableInstance instance = this.instance.asPlayInstance();
                if (instance != null) {
                    if (instance.getLivingPlayers().contains(playerSession)) {
                        LangUtils.sendMessage(reviver.getPlayer(), "instance.play.functions.reviver.not-dead");
                    } else {
                        EntityUtils.forceTeleport(player, reviver.getPlayer().getLocation());

                        GameMode gamemode;
                        try {
                            gamemode =
                                    GameMode.valueOf(
                                            instance
                                                    .getDungeon()
                                                    .getConfig()
                                                    .getString("players.gamemode", "ADVENTURE")
                                                    .toUpperCase(Locale.ROOT));
                        } catch (IllegalArgumentException exception) {
                            gamemode = GameMode.ADVENTURE;
                            this.logger()
                                    .warn(
                                            "RevivePlayerFunction in dungeon '{}' at {} encountered an invalid gamemode configuration. Falling back to ADVENTURE.",
                                            this.dungeonNameForLogs(),
                                            this.locationForLogs(),
                                            exception);
                        }
                        player.setGameMode(gamemode);
                        instance.getLivingPlayers().add(playerSession);
                        instance.getPlayerLives().put(player.getUniqueId(), this.livesAfterRevival);
                        this.currentRevives++;

                        for (DungeonPlayerSession target : instance.getPlayers()) {
                            Player targetPlayer = target.getPlayer();
                            LangUtils.sendMessage(
                                    reviver.getPlayer(),
                                    "instance.play.functions.reviver.revived",
                                    LangUtils.placeholder("player", player.getName()));
                            targetPlayer.playSound(this.location, "block.beacon.activate", 1.0F, 1.0F);
                            targetPlayer.playSound(this.location, "entity.player.levelup", 0.5F, 1.5F);
                        }
                    }
                }
            } else {
                LangUtils.sendMessage(
                        reviver.getPlayer(),
                        "instance.play.functions.reviver.not-in-dungeon",
                        LangUtils.placeholder("player", player.getName()));
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.TOTEM_OF_UNDYING);
        button.setDisplayName("&6Player Reviver");
        button.addLore("&eAllows players to revive other");
        button.addLore("&edead players still spectating.");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.TOTEM_OF_UNDYING);
                        this.button.setDisplayName("&d&lMax Revives");
                        this.button.setAmount(RevivePlayerFunction.this.maxRevives);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.revive-player.ask-max-revives");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RevivePlayerFunction.this.maxRevives = Math.max(value.orElse(1), 0);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.revive-player.max-revives-set",
                                LangUtils.placeholder(
                                        "max_revives", String.valueOf(RevivePlayerFunction.this.maxRevives)));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PLAYER_HEAD);
                        this.button.setDisplayName("&d&lLives After Revival");
                        this.button.setAmount(RevivePlayerFunction.this.livesAfterRevival);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.revive-player.ask-lives-after");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RevivePlayerFunction.this.livesAfterRevival = Math.max(value.orElse(1), 1);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.revive-player.lives-after-set",
                                LangUtils.placeholder(
                                        "lives", String.valueOf(RevivePlayerFunction.this.livesAfterRevival)));
                    }
                });
    }

    /**
     * Returns the max revives.
     */
    public int getMaxRevives() {
        return this.maxRevives;
    }

    /**
     * Returns the lives after revival.
     */
    public int getLivesAfterRevival() {
        return this.livesAfterRevival;
    }

    /**
     * Returns the current revives.
     */
    public int getCurrentRevives() {
        return this.currentRevives;
    }
}
