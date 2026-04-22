package nl.hauntedmc.dungeons.content.function.variable;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.variable.VariableEditMode;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Function that mutates instance-scoped variables for later trigger/condition checks.
 */
@AutoRegister(id = "dungeons.function.instance_variable")
@SerializableAs("dungeons.function.instance_variable")
public class InstanceVariableFunction extends DungeonFunction {
    @PersistedField private String varName;
    @PersistedField private String varValue;
    @PersistedField private VariableEditMode mode = VariableEditMode.SET;
    private int modeIndex;

    /**
     * Creates a new InstanceVariableFunction instance.
     */
    public InstanceVariableFunction(Map<String, Object> config) {
        super("Instance Variable", config);
        this.setTargetType(FunctionTargetType.NONE);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Creates a new InstanceVariableFunction instance.
     */
    public InstanceVariableFunction() {
        super("Instance Variable");
        this.setTargetType(FunctionTargetType.NONE);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.META);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        Bukkit.getScheduler()
                .runTaskLater(
                        RuntimeContext.plugin(),
                        () -> {
                            PlayableInstance play = this.instance.asPlayInstance();
                            if (play != null) {
                                double change;
                                switch (this.mode) {
                                    case SET:
                                        play.getInstanceVariables().set(this.varName, this.varValue);
                                        break;
                                    case ADD:
                                        try {
                                            change = Double.parseDouble(this.varValue);
                                            play.getInstanceVariables().add(this.varName, change);
                                        } catch (NumberFormatException exception) {
                                            RuntimeContext.plugin()
                                                    .getSLF4JLogger()
                                                    .warn(
                                                            "Failed to add value '{}' to instance variable '{}' in dungeon '{}' at {},{},{}: value is not numeric.",
                                                            this.varValue,
                                                            this.varName,
                                                            this.instance.getDungeon().getWorldName(),
                                                            this.location.getBlockX(),
                                                            this.location.getBlockY(),
                                                            this.location.getBlockZ());
                                        }
                                        break;
                                    case SUBTRACT:
                                        try {
                                            change = Double.parseDouble(this.varValue);
                                            play.getInstanceVariables().subtract(this.varName, change);
                                        } catch (NumberFormatException exception) {
                                            RuntimeContext.plugin()
                                                    .getSLF4JLogger()
                                                    .warn(
                                                            "Failed to subtract value '{}' from instance variable '{}' in dungeon '{}' at {},{},{}: value is not numeric.",
                                                            this.varValue,
                                                            this.varName,
                                                            this.instance.getDungeon().getWorldName(),
                                                            this.location.getBlockX(),
                                                            this.location.getBlockY(),
                                                            this.location.getBlockZ());
                                        }
                                }
                            }
                        },
                        1L);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OAK_SIGN);
        functionButton.setDisplayName("&aDungeon Variable");
        functionButton.addLore("&eStores and manages instance-wide");
        functionButton.addLore("&evariables. Used in combination with");
        functionButton.addLore("&ethe dungeon variable trigger condition.");
        return functionButton;
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
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lEdit Name");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.instance-variable.ask-name");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.current-name",
                                LangUtils.placeholder("name", InstanceVariableFunction.this.varName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        InstanceVariableFunction.this.varName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.name-set",
                                LangUtils.placeholder("name", InstanceVariableFunction.this.varName));
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.OBSERVER);
                        this.button.setDisplayName("&d&lEdit Mode: " + InstanceVariableFunction.this.mode);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        InstanceVariableFunction.this.modeIndex++;
                        InstanceVariableFunction.this.verifyModeType();
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.mode-set",
                                LangUtils.placeholder("mode", InstanceVariableFunction.this.mode.toString()));
                        playerSession.showHotbar(this.menu);
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lValue");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        switch (InstanceVariableFunction.this.mode) {
                            case SET:
                                LangUtils.sendMessage(player, "editor.function.instance-variable.ask-value-set");
                                break;
                            case ADD:
                                LangUtils.sendMessage(player, "editor.function.instance-variable.ask-value-add");
                                break;
                            case SUBTRACT:
                                LangUtils.sendMessage(
                                        player, "editor.function.instance-variable.ask-value-subtract");
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.current-value",
                                LangUtils.placeholder(
                                        "value", String.valueOf(InstanceVariableFunction.this.varValue)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        if (InstanceVariableFunction.this.mode != VariableEditMode.SET) {
                            Optional<Double> value = InputUtils.readDoubleInput(player, message);
                            InstanceVariableFunction.this.varValue =
                                    value.isPresent() ? message : InstanceVariableFunction.this.varValue;
                            if (value.isPresent()) {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.instance-variable.value-set",
                                        LangUtils.placeholder(
                                                "value", String.valueOf(InstanceVariableFunction.this.varValue)));
                            }
                        } else {
                            InstanceVariableFunction.this.varValue = message;
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.instance-variable.value-set",
                                    LangUtils.placeholder(
                                            "value", String.valueOf(InstanceVariableFunction.this.varValue)));
                        }
                    }
                });
    }

    /**
     * Performs verify mode type.
     */
    private void verifyModeType() {
        if (this.modeIndex >= VariableEditMode.values().length) {
            this.modeIndex = 0;
        }

        this.mode = VariableEditMode.fromIndex(this.modeIndex);
    }
}
