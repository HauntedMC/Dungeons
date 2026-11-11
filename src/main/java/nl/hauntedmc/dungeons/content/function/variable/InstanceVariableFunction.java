package nl.hauntedmc.dungeons.content.function.variable;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.variable.VariableEditMode;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.localization.LangUtils;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.scheduler.BukkitRunnable;

/** Function that mutates instance-scoped variables for later trigger/condition checks. */
@AutoRegister(id = "dungeons.function.instance_variable")
@SerializableAs("dungeons.function.instance_variable")
public class InstanceVariableFunction extends DungeonFunction {
    @PersistedField private String varName;
    @PersistedField private String varValue;
    @PersistedField private VariableEditMode mode = VariableEditMode.SET;
    private int modeIndex;
    private Set<BukkitRunnable> tasks = new HashSet<>();

    /** Creates a new InstanceVariableFunction instance. */
    public InstanceVariableFunction(Map<String, Object> config) {
        super("Instance Variable", config);
        this.setTargetType(FunctionTargetType.NONE);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.META);
    }

    /** Creates a new InstanceVariableFunction instance. */
    public InstanceVariableFunction() {
        super("Instance Variable");
        this.setTargetType(FunctionTargetType.NONE);
        this.setAllowChangingTargetType(false);
        this.setCategory(FunctionCategory.META);
    }

    /** Performs run function. */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.instance == null
                || this.varName == null
                || this.varName.isBlank()
                || this.mode == null) return;
        final PlayableInstance play = this.instance.asPlayInstance();
        if (play == null) return;
        final String name = this.varName;
        final String value = this.varValue;
        final VariableEditMode operation = this.mode;
        final double change;
        if (operation != VariableEditMode.SET) {
            try {
                change = Double.parseDouble(value);
                if (!Double.isFinite(change)) return;
            } catch (IllegalArgumentException | NullPointerException exception) {
                this.logger()
                        .warn(
                                "Skipping nonnumeric instance variable change '{}' for '{}' in dungeon '{}'.",
                                value,
                                name,
                                this.dungeonNameForLogs());
                return;
            }
        } else {
            change = 0;
        }
        BukkitRunnable task =
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        try {
                            switch (operation) {
                                case SET -> play.getInstanceVariables().set(name, value);
                                case ADD -> play.getInstanceVariables().add(name, change);
                                case SUBTRACT -> play.getInstanceVariables().subtract(name, change);
                            }
                        } finally {
                            tasks.remove(this);
                        }
                    }
                };
        tasks.add(task);
        try {
            task.runTaskLater(this.plugin(), 1L);
        } catch (RuntimeException exception) {
            tasks.remove(task);
            throw exception;
        }
    }

    @Override
    public void onDisable() {
        for (BukkitRunnable task : tasks) {
            this.cleanupOwnedResource(
                    "variable change",
                    () -> {
                        if (!task.isCancelled()) task.cancel();
                    });
        }
        tasks.clear();
    }

    @Override
    public InstanceVariableFunction clone() {
        InstanceVariableFunction copy = (InstanceVariableFunction) super.clone();
        copy.tasks = new HashSet<>();
        return copy;
    }

    /** Builds menu button. */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.OAK_SIGN);
        functionButton.setDisplayName("&aDungeon Variable");
        functionButton.addLore("&eStores and manages instance-wide");
        functionButton.addLore("&evariables. Used in combination with");
        functionButton.addLore("&ethe dungeon variable trigger condition.");
        return functionButton;
    }

    /** Builds hotbar menu. */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                new ChatMenuItem() {
                    /** Builds button. */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lEdit Name");
                    }

                    /** Performs on select. */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.instance-variable.ask-name");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.current-name",
                                LangUtils.placeholder(
                                        "name", InstanceVariableFunction.this.varName));
                    }

                    /** Performs on input. */
                    @Override
                    public void onInput(Player player, String message) {
                        InstanceVariableFunction.this.varName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.name-set",
                                LangUtils.placeholder(
                                        "name", InstanceVariableFunction.this.varName));
                    }
                });
        this.menu.addMenuItem(
                new MenuItem() {
                    /** Builds button. */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.OBSERVER);
                        this.button.setDisplayName(
                                "&d&lEdit Mode: " + InstanceVariableFunction.this.mode);
                    }

                    /** Performs on select. */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession =
                                InstanceVariableFunction.this
                                        .runtime()
                                        .playerSessions()
                                        .get(player);
                        InstanceVariableFunction.this.modeIndex++;
                        InstanceVariableFunction.this.verifyModeType();
                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.mode-set",
                                LangUtils.placeholder(
                                        "mode", InstanceVariableFunction.this.mode.toString()));
                        playerSession.showHotbar(this.menu);
                    }
                });
        this.menu.addMenuItem(
                new ChatMenuItem() {
                    /** Builds button. */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lValue");
                    }

                    /** Performs on select. */
                    @Override
                    public void onSelect(Player player) {
                        switch (InstanceVariableFunction.this.mode) {
                            case SET:
                                LangUtils.sendMessage(
                                        player, "editor.function.instance-variable.ask-value-set");
                                break;
                            case ADD:
                                LangUtils.sendMessage(
                                        player, "editor.function.instance-variable.ask-value-add");
                                break;
                            case SUBTRACT:
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.instance-variable.ask-value-subtract");
                        }

                        LangUtils.sendMessage(
                                player,
                                "editor.function.instance-variable.current-value",
                                LangUtils.placeholder(
                                        "value",
                                        String.valueOf(InstanceVariableFunction.this.varValue)));
                    }

                    /** Performs on input. */
                    @Override
                    public void onInput(Player player, String message) {
                        if (InstanceVariableFunction.this.mode != VariableEditMode.SET) {
                            Optional<Double> value = InputUtils.readDoubleInput(player, message);
                            InstanceVariableFunction.this.varValue =
                                    value.isPresent()
                                            ? message
                                            : InstanceVariableFunction.this.varValue;
                            if (value.isPresent()) {
                                LangUtils.sendMessage(
                                        player,
                                        "editor.function.instance-variable.value-set",
                                        LangUtils.placeholder(
                                                "value",
                                                String.valueOf(
                                                        InstanceVariableFunction.this.varValue)));
                            }
                        } else {
                            InstanceVariableFunction.this.varValue = message;
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.instance-variable.value-set",
                                    LangUtils.placeholder(
                                            "value",
                                            String.valueOf(
                                                    InstanceVariableFunction.this.varValue)));
                        }
                    }
                });
    }

    /** Performs verify mode type. */
    private void verifyModeType() {
        if (this.modeIndex >= VariableEditMode.values().length) {
            this.modeIndex = 0;
        }

        this.mode = VariableEditMode.fromIndex(this.modeIndex);
    }
}
