package nl.hauntedmc.dungeons.content.function.reward;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.EditorHidden;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.reward.CooldownPeriod;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.PlayerHotbarMenu;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Base reward function that manages reward GUIs, cooldowns, and delivery rules.
 */
@AutoRegister(id = "dungeons.function.reward")
@SerializableAs("dungeons.function.reward")
public class RewardFunction extends DungeonFunction {
    @PersistedField @EditorHidden protected List<ItemStack> rewards;
    @PersistedField protected int xp = 0;
    @PersistedField protected int levels = 0;
    @PersistedField protected boolean cooldownDisabled = false;
    @PersistedField protected boolean overrideCooldown = false;
    @PersistedField protected int period = 0;
    @PersistedField protected int cooldownTime = 0;
    @PersistedField protected int resetDay = 1;
    protected PlayerHotbarMenu cooldownMenu;
    protected GuiWindow rewardsGui;
    protected GuiWindow instanceGui;
    protected String rewardsGuiName;
    protected String instanceGuiName;
    protected List<UUID> playerRewards;
    private transient boolean invalidCooldownLogged;

    /**
     * Creates a new RewardFunction instance.
     */
    public RewardFunction(String namespace, Map<String, Object> config) {
        super(namespace, config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
        this.setColour("#fceb03");
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new RewardFunction instance.
     */
    public RewardFunction(Map<String, Object> config) {
        super("Rewards", config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
        this.setColour("#fceb03");
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new RewardFunction instance.
     */
    public RewardFunction(String namespace) {
        super(namespace);
        this.rewards = new ArrayList<>();
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
        this.setColour("#fceb03");
    }

    /**
     * Creates a new RewardFunction instance.
     */
    public RewardFunction() {
        super("Rewards");
        this.rewards = new ArrayList<>();
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
        this.setColour("#fceb03");
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.CHEST);
        functionButton.setDisplayName("&eRewards");
        functionButton.addLore("&eCreates a rewards menu at this");
        functionButton.addLore("&elocation.");
        return functionButton;
    }

    /**
     * Sets the location.
     */
    @Override
    public void setLocation(Location loc) {
        super.setLocation(loc);
        this.initializeRewardsGui();
        this.initializeCooldownMenu();
    }

    /**
     * Performs override cooldown.
     */
    public boolean overrideCooldown() {
        return this.overrideCooldown;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        this.ensureRewardStorage();
        for (DungeonPlayerSession playerSession : targets) {
            Player player = playerSession.getPlayer();
            // XP and level rewards are granted immediately, while item rewards are exposed through
            // the per-instance GUI so the player can claim them explicitly.
            if (!this.playerRewards.contains(player.getUniqueId())
                    && !this.instance.getDungeon().hasLootCooldown(player, this)) {
                if (this.xp > 0) {
                    player.giveExp(this.xp);
                    LangUtils.sendMessage(
                            player,
                            "instance.play.rewards.xp-received",
                            LangUtils.placeholder("xp", String.valueOf(this.xp)));
                }

                if (this.levels > 0) {
                    player.giveExpLevels(this.levels);
                    LangUtils.sendMessage(
                            player,
                            "instance.play.rewards.levels-received",
                            LangUtils.placeholder("levels", String.valueOf(this.levels)));
                }
            }

            if (this.instanceGuiName != null) {
                RuntimeContext.guiService().openGui(player, this.instanceGuiName);
            } else {
                this.logger()
                        .warn(
                                "Reward function in dungeon '{}' at {} has no active menu name. Skipping menu open for '{}'.",
                                this.dungeonNameForLogs(),
                                this.locationForLogs(),
                                player.getName());
            }
            if (!this.playerRewards.contains(player.getUniqueId())) {
                this.playerRewards.add(player.getUniqueId());
            }

            if (!this.instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                this.processCooldown(player);
            }
        }
    }

    /**
     * Performs process cooldown.
     */
    public void processCooldown(Player player) {
        if (!this.cooldownDisabled) {
            if (!this.instance.getConfig().getBoolean("rewards.deliver_on_finish", false)
                    || this.playerRewards.contains(player.getUniqueId())) {
                if (!(this.instance instanceof EditableInstance)) {
                    PlayableInstance instance = (PlayableInstance) this.instance;
                    DungeonDefinition dungeon = instance.getDungeon();
                    // Reward-specific cooldowns and dungeon-wide completion cooldowns share the
                    // same trigger point but are persisted differently by the dungeon model.
                    if (dungeon.isCooldownsPerReward()) {
                        if (!dungeon.hasLootCooldown(player, this)) {
                            if (!this.overrideCooldown) {
                                dungeon.addLootCooldown(player, this);
                            } else {
                                dungeon.addLootCooldown(player, this, this.getCooldownTime());
                            }

                            dungeon.saveCooldowns(player);
                        }
                    } else if (!dungeon.hasLootCooldown(player, this)) {
                        instance.setReceivedRewards(player, true);
                    }
                }
            }
        }
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.ensureRewardStorage();
        this.instanceGuiName =
                "viewrewards_"
                        + this.instance.getInstanceWorld().getName()
                        + "_"
                        + this.location.getBlockX()
                        + "_"
                        + this.location.getBlockY()
                        + "_"
                        + this.location.getBlockZ();
        this.instanceGui =
                                new GuiWindow(
                        this.instanceGuiName,
                        54,
                        LangUtils.getMessage("instance.play.rewards.gui-name", false));
        this.instanceGui.setCancelClick(false);
        this.instanceGui.addOpenAction(
                "loaditems",
                event -> {
                    Player player = (Player) event.getPlayer();
                    if (!this.playerRewards.contains(player.getUniqueId())) {
                        if (!(this.instance instanceof EditableInstance)) {
                            PlayableInstance instance = (PlayableInstance) this.instance;
                            if (!this.cooldownDisabled && instance.getDungeon().hasLootCooldown(player, this)) {
                                LangUtils.sendMessage(player, "instance.play.rewards.already-received");
                                Date unlockTime = instance.getDungeon().getLootUnlockTime(player, this);
                                if (unlockTime != null) {
                                    SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d, hh:mm aaa z");
                                    LangUtils.sendMessage(
                                            player,
                                            "instance.play.rewards.cooldown-time",
                                            LangUtils.placeholder("time", format.format(unlockTime)));
                                }
                            } else {
                                this.playerRewards.add(player.getUniqueId());
                                if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                                    LangUtils.sendMessage(player, "instance.play.rewards.rewards-inv-info");
                                    LangUtils.sendMessage(player, "instance.play.rewards.view-rewards-info");
                                }

                                GuiInventory guiInv = this.instanceGui.getInventoryFor(player);
                                Inventory inv = guiInv.inventory();
                                int i = 0;

                                for (ItemStack item : this.rewards) {

                                    if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                                        if (item == null) {
                                            continue;
                                        }

                                        Button button = new Button("item_" + i, item);
                                        int finalSlot = i;
                                        button.addAction(
                                                "click",
                                                clickEvent -> {
                                                    clickEvent.setCancelled(true);
                                                    Player clicker = (Player) clickEvent.getWhoClicked();
                                                    instance.addPlayerReward(clicker, item);
                                                    guiInv.removeButton(finalSlot);
                                                });
                                        guiInv.setButton(i, button);
                                    } else {
                                        inv.setItem(i, item);
                                    }

                                    i++;
                                }
                            }
                        }
                    }
                });
        this.instanceGui.addCloseAction(
                "dropitems",
                event -> {
                    if ((this.trigger == null || !this.trigger.isAllowRetrigger())
                            && (this.parentFunction == null
                                    || this.parentFunction.getTrigger() == null
                                    || !this.parentFunction.getTrigger().isAllowRetrigger())) {
                        if (!(this.instance instanceof EditableInstance)) {
                            PlayableInstance instance = (PlayableInstance) this.instance;
                            Player player = (Player) event.getPlayer();
                            Inventory inv = this.instanceGui.getInventoryFor(player).inventory();
                            if (!inv.isEmpty()) {
                                if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                                    LangUtils.sendMessage(player, "instance.play.rewards.added-to-rewards-inv");
                                    instance.addPlayerReward(player, inv.getContents());
                                } else {
                                    LangUtils.sendMessage(player, "instance.play.rewards.added-to-inv");
                                    ItemUtils.giveOrDrop(player, inv.getContents());
                                }

                                inv.clear();
                            }
                        }
                    }
                });
    }

    /**
     * Initializes rewards gui.
     */
    public void initializeRewardsGui() {
        this.ensureRewardStorage();
        this.rewardsGuiName =
                "editrewards_"
                        + this.location.getBlockX()
                        + "_"
                        + this.location.getBlockY()
                        + "_"
                        + this.location.getBlockZ();
        this.rewardsGui = new GuiWindow(this.rewardsGuiName, 45, "Modify Rewards");
        this.rewardsGui.setCancelClick(false);
        this.rewardsGui.addOpenAction(
                "loaditems",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    if (playerSession.isEditMode()) {
                        Inventory inv = this.rewardsGui.getInventoryFor(player).inventory();
                        int i = 0;

                        for (ItemStack item : this.rewards) {
                            inv.setItem(i, item);
                            i++;
                        }
                    }
                });
        this.rewardsGui.addCloseAction(
                "saveitems",
                event -> {
                    Player player = (Player) event.getPlayer();
                    DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                    if (playerSession.isEditMode()) {
                        Inventory inv = this.rewardsGui.getInventoryFor(player).inventory();
                        this.rewards = new ArrayList<>();
                        this.rewards.addAll(Arrays.asList(inv.getContents()));
                        LangUtils.sendMessage(player, "editor.function.reward.saved");
                    }
                });
    }

    /**
     * Initializes cooldown menu.
     */
    public void initializeCooldownMenu() {
        this.cooldownMenu = PlayerHotbarMenu.createMenu();
        this.cooldownMenu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.RED_STAINED_GLASS_PANE);
                        this.button.setDisplayName("&c&lBACK");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.restorePreviousHotbar();
                    }
                });
        this.cooldownMenu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.BEACON);
                        this.button.setDisplayName("&d&lToggle Cooldown Enabled");
                        this.button.setEnchanted(!RewardFunction.this.cooldownDisabled);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RewardFunction.this.cooldownDisabled) {
                            LangUtils.sendMessage(player, "editor.function.reward.cooldown-disabled");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.reward.cooldown-enabled");
                        }

                        RewardFunction.this.cooldownDisabled = !RewardFunction.this.cooldownDisabled;
                    }
                });
        this.cooldownMenu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.NETHER_STAR);
                        this.button.setDisplayName("&e&lToggle Custom Cooldown");
                        this.button.setEnchanted(RewardFunction.this.overrideCooldown);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!RewardFunction.this.overrideCooldown) {
                            LangUtils.sendMessage(player, "editor.function.reward.custom-cooldown-enabled");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.reward.custom-cooldown-disabled");
                        }

                        RewardFunction.this.overrideCooldown = !RewardFunction.this.overrideCooldown;
                    }
                });
        this.cooldownMenu.addMenuItem(
                3,
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        if (!RewardFunction.this.overrideCooldown) {
                            this.button = null;
                        } else {
                            this.button = new MenuButton(Material.CLOCK);
                            this.button.setDisplayName(
                                    "&d&lType: " + CooldownPeriod.fromIndex(RewardFunction.this.period));
                        }
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        if (RewardFunction.this.overrideCooldown) {
                            Player player = event.getPlayer();
                            DungeonPlayerSession playerSession =
                                    RuntimeContext.playerSessions().get(player);
                            RewardFunction.this.period++;
                            if (RewardFunction.this.period >= CooldownPeriod.values().length) {
                                RewardFunction.this.period = 0;
                            }

                            CooldownPeriod period = CooldownPeriod.fromIndex(RewardFunction.this.period);
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.reward.cooldown-type-set",
                                    LangUtils.placeholder("type", period.toString()));
                            playerSession.showHotbar(RewardFunction.this.cooldownMenu, false);
                        }
                    }
                });
        this.cooldownMenu.addMenuItem(
                4,
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        if (!RewardFunction.this.overrideCooldown) {
                            this.button = null;
                        } else {
                            CooldownPeriod period = CooldownPeriod.fromIndex(RewardFunction.this.period);
                            this.button = new MenuButton(Material.REPEATER);
                            if (period == CooldownPeriod.TIMER) {
                                this.button.setDisplayName("&d&lCooldown in Minutes");
                            } else {
                                this.button.setDisplayName("&d&lHour of Day");
                            }

                            this.button.setAmount(RewardFunction.this.cooldownTime);
                        }
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (RewardFunction.this.overrideCooldown) {
                            CooldownPeriod period = CooldownPeriod.fromIndex(RewardFunction.this.period);
                            if (period == CooldownPeriod.TIMER) {
                                LangUtils.sendMessage(player, "editor.function.reward.ask-cooldown-minutes");
                            } else {
                                LangUtils.sendMessage(player, "editor.function.reward.ask-reset-hour");
                            }
                        }
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RewardFunction.this.cooldownTime = value.orElse(RewardFunction.this.cooldownTime);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.reward.cooldown-time-set",
                                    LangUtils.placeholder("time", String.valueOf(RewardFunction.this.cooldownTime)));
                        }
                    }
                });
        this.cooldownMenu.addMenuItem(
                5,
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        if (!RewardFunction.this.overrideCooldown) {
                            this.button = null;
                        } else {
                            CooldownPeriod period = CooldownPeriod.fromIndex(RewardFunction.this.period);
                            if (period == CooldownPeriod.WEEKLY) {
                                this.button = new MenuButton(Material.CLOCK);
                                this.button.setDisplayName("&d&lDay of Week");
                                this.button.setAmount(RewardFunction.this.resetDay);
                            } else if (period == CooldownPeriod.MONTHLY) {
                                this.button = new MenuButton(Material.CLOCK);
                                this.button.setDisplayName("&d&lDay of Month");
                                this.button.setAmount(RewardFunction.this.resetDay);
                            } else {
                                this.button = null;
                            }
                        }
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (RewardFunction.this.overrideCooldown) {
                            if (RewardFunction.this.period >= 3) {
                                CooldownPeriod cPeriod = CooldownPeriod.fromIndex(RewardFunction.this.period);
                                if (cPeriod == CooldownPeriod.WEEKLY) {
                                    LangUtils.sendMessage(player, "editor.function.reward.ask-reset-weekday");
                                } else if (cPeriod == CooldownPeriod.MONTHLY) {
                                    LangUtils.sendMessage(player, "editor.function.reward.ask-reset-monthday");
                                }
                            }
                        }
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RewardFunction.this.resetDay = value.orElse(RewardFunction.this.resetDay);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.reward.reset-day-set",
                                    LangUtils.placeholder("day", String.valueOf(RewardFunction.this.resetDay)));
                        }
                    }
                });
    }

    /**
     * Returns the cooldown time.
     */
    public Date getCooldownTime() {
        CooldownPeriod period = CooldownPeriod.fromIndex(this.period);
        int cooldownTime = Math.max(0, this.cooldownTime);
        if (period == CooldownPeriod.TIMER) {
            return period.fromNow(cooldownTime);
        } else {
            int normalizedResetDay = this.resetDay;
            Calendar cal = Calendar.getInstance();
            if (cooldownTime > 23) {
                cooldownTime = 23;
                this.logInvalidCooldownConfiguration();
            }

            if (cooldownTime > cal.get(Calendar.HOUR_OF_DAY)) {
                cal.set(Calendar.DATE, cal.get(Calendar.DATE) - 1);
            }

            cal.set(Calendar.HOUR_OF_DAY, cooldownTime);
            cal.set(Calendar.MINUTE, 0);
            cal.set(Calendar.SECOND, 0);
            cal.set(Calendar.MILLISECOND, 0);
            if (period == CooldownPeriod.WEEKLY) {
                int dayOfWeek = Math.min(Math.max(normalizedResetDay, Calendar.SUNDAY), Calendar.SATURDAY);
                if (dayOfWeek != normalizedResetDay) {
                    this.logInvalidCooldownConfiguration();
                }
                if (dayOfWeek > cal.get(Calendar.DAY_OF_WEEK)) {
                    cal.set(Calendar.WEEK_OF_MONTH, cal.get(Calendar.WEEK_OF_MONTH) - 1);
                }

                cal.set(Calendar.DAY_OF_WEEK, dayOfWeek);
            }

            if (period == CooldownPeriod.MONTHLY) {
                int dayOfMonth = Math.max(1, normalizedResetDay);
                dayOfMonth = Math.min(dayOfMonth, cal.getActualMaximum(Calendar.DAY_OF_MONTH));
                if (dayOfMonth != normalizedResetDay) {
                    this.logInvalidCooldownConfiguration();
                }
                if (dayOfMonth > cal.get(Calendar.DAY_OF_MONTH)) {
                    cal.set(Calendar.MONTH, cal.get(Calendar.MONTH) - 1);
                }

                cal.set(
                        Calendar.DAY_OF_MONTH,
                        Math.min(dayOfMonth, cal.getActualMaximum(Calendar.DAY_OF_MONTH)));
            }

            return period.fromDate(cal.getTime());
        }
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.CHEST);
                        this.button.setDisplayName("&d&lEdit Rewards");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, RewardFunction.this.rewardsGuiName);
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.EMERALD);
                        this.button.setDisplayName("&d&lExp Reward");
                        this.button.setAmount(RewardFunction.this.xp);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.reward.ask-exp");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RewardFunction.this.xp = value.orElse(RewardFunction.this.xp);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.reward.exp-set",
                                    LangUtils.placeholder("xp", String.valueOf(RewardFunction.this.xp)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.NETHER_STAR);
                        this.button.setDisplayName("&d&lExp Levels Reward");
                        this.button.setAmount(RewardFunction.this.levels);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.reward.ask-levels");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RewardFunction.this.levels = value.orElse(RewardFunction.this.levels);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.reward.levels-set",
                                    LangUtils.placeholder("levels", String.valueOf(RewardFunction.this.levels)));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.CLOCK);
                        this.button.setDisplayName("&a&lCooldown Settings");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        DungeonPlayerSession playerSession = RuntimeContext.playerSessions().get(player);
                        playerSession.showHotbar(RewardFunction.this.cooldownMenu, true);
                    }
                });
    }

    /**
     * Returns the rewards map.
     */
    public Map<String, Object> getRewardsMap() {
        Map<String, Object> map = new HashMap<>();
        int i = -1;

        for (ItemStack item : this.rewards) {
            i++;
            if (item != null) {
                map.put(String.valueOf(i), item);
            }
        }

        return map;
    }

    /**
     * Returns the rewards.
     */
    public List<ItemStack> getRewards() {
        return this.rewards;
    }

    /**
     * Performs ensure reward storage.
     */
    protected final void ensureRewardStorage() {
        if (this.rewards == null) {
            this.rewards = new ArrayList<>();
        }

        if (this.playerRewards == null) {
            this.playerRewards = new ArrayList<>();
        }
    }

    /**
     * Performs log invalid cooldown configuration.
     */
    private void logInvalidCooldownConfiguration() {
        if (!this.invalidCooldownLogged) {
            this.invalidCooldownLogged = true;
            this.logger()
                    .warn(
                            "Reward function in dungeon '{}' at {} had an out-of-range cooldown configuration (period={}, hour={}, resetDay={}). Values were normalized.",
                            this.dungeonNameForLogs(),
                            this.locationForLogs(),
                            this.period,
                            this.cooldownTime,
                            this.resetDay);
        }
    }

    /**
     * Returns the xp.
     */
    public int getXp() {
        return this.xp;
    }

    /**
     * Returns the levels.
     */
    public int getLevels() {
        return this.levels;
    }

    /**
     * Returns whether cooldown disabled.
     */
    public boolean isCooldownDisabled() {
        return this.cooldownDisabled;
    }
}
