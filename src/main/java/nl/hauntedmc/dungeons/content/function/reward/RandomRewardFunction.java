package nl.hauntedmc.dungeons.content.function.reward;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Reward function that chooses a random subset from its configured reward list.
 */
@AutoRegister(id = "dungeons.function.random_reward")
@SerializableAs("dungeons.function.random_reward")
public class RandomRewardFunction extends RewardFunction {
    @PersistedField private int rewardMinCount = 1;
    @PersistedField private int rewardMaxCount = 3;
    private transient boolean invalidRewardCountLogged;

    /**
     * Creates a new RandomRewardFunction instance.
     */
    public RandomRewardFunction(Map<String, Object> config) {
        super("Random Rewards", config);
    }

    /**
     * Creates a new RandomRewardFunction instance.
     */
    public RandomRewardFunction() {
        super("Random Rewards");
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.ENDER_CHEST);
        functionButton.setDisplayName("&eRandom Rewards");
        functionButton.addLore("&eCreates a random rewards menu");
        functionButton.addLore("&eat this location.");
        return functionButton;
    }

    /**
     * Performs on enable.
     */
    @Override
    public void onEnable() {
        this.ensureRewardStorage();
        this.playerRewards = new ArrayList<>();
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

                                event.setCancelled(true);
                            } else {
                                if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)
                                        || !instance.getConfig().getBoolean("players.keep_on_entry.inventory", true)) {
                                    LangUtils.sendMessage(player, "instance.play.rewards.rewards-inv-info");
                                    LangUtils.sendMessage(player, "instance.play.rewards.view-rewards-info");
                                }

                                GuiInventory guiInv = this.instanceGui.getInventoryFor(player);
                                Inventory inv = guiInv.inventory();
                                List<ItemStack> validRewards = new ArrayList<>();

                                for (ItemStack item : this.rewards) {
                                    if (item != null && item.getType() != Material.AIR) {
                                        validRewards.add(item);
                                    }
                                }

                                if (!validRewards.isEmpty()) {
                                    this.playerRewards.add(player.getUniqueId());
                                    int bonusItems = 0;
                                    if (instance.getDifficulty() != null) {
                                        bonusItems = instance.getDifficulty().getBonusLoot().randomizeAsInt();
                                    }

                                    int minRewards = Math.max(0, this.rewardMinCount);
                                    int maxRewards = Math.max(minRewards, this.rewardMaxCount);
                                    if ((minRewards != this.rewardMinCount || maxRewards != this.rewardMaxCount)
                                            && !this.invalidRewardCountLogged) {
                                        this.invalidRewardCountLogged = true;
                                        this.logger()
                                                .warn(
                                                        "Random reward function in dungeon '{}' at {} had invalid min/max reward counts ({}, {}). Using {}, {}.",
                                                        this.dungeonNameForLogs(),
                                                        this.locationForLogs(),
                                                        this.rewardMinCount,
                                                        this.rewardMaxCount,
                                                        minRewards,
                                                        maxRewards);
                                    }

                                    int rewardCount =
                                            MathUtils.getRandomNumberInRange(minRewards, maxRewards) + bonusItems;
                                    rewardCount = Math.min(rewardCount, validRewards.size());

                                    for (int r = 0; r < rewardCount; r++) {
                                        ItemStack rewardItem =
                                                validRewards.get(
                                                        MathUtils.getRandomNumberInRange(0, validRewards.size() - 1));
                                        validRewards.remove(rewardItem);

                                        if (!instance.getConfig().getBoolean("rewards.deliver_on_finish", false)
                                                && instance
                                                        .getConfig()
                                                        .getBoolean("players.keep_on_entry.inventory", true)) {
                                            inv.setItem(r, rewardItem);
                                        } else if (rewardItem != null) {
                                            Button button = new Button("item_" + r, rewardItem);
                                            int finalSlot = r;
                                            button.addAction(
                                                    "click",
                                                    clickEvent -> {
                                                        clickEvent.setCancelled(true);
                                                        Player clicker = (Player) clickEvent.getWhoClicked();
                                                        instance.addPlayerReward(clicker, rewardItem);
                                                        guiInv.removeButton(finalSlot);
                                                    });
                                            guiInv.setButton(r, button);
                                        }
                                    }
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
                        if (this.instance instanceof EditableInstance) {
                            return;
                        }

                        Player player = (Player) event.getPlayer();
                        Inventory inv = this.instanceGui.getInventoryFor(player).inventory();
                        if (!inv.isEmpty()) {
                            if (!this.instance.getConfig().getBoolean("rewards.deliver_on_finish", false)
                                    && this.instance
                                            .getConfig()
                                            .getBoolean("players.keep_on_entry.inventory", true)) {
                                LangUtils.sendMessage(player, "instance.play.rewards.added-to-inv");
                                ItemUtils.giveOrDrop(player, inv.getContents());
                            } else {
                                LangUtils.sendMessage(player, "instance.play.rewards.added-to-rewards-inv");
                                ((PlayableInstance) this.instance).addPlayerReward(player, inv.getContents());
                            }

                            inv.clear();
                        }
                    }
                });
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        super.buildHotbarMenu();
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COAL);
                        this.button.setDisplayName("&d&lMin Rewards");
                        this.button.setAmount(RandomRewardFunction.this.rewardMinCount);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.random-reward.ask-min");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RandomRewardFunction.this.rewardMinCount =
                                value.orElse(RandomRewardFunction.this.rewardMinCount);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.random-reward.min-set",
                                    LangUtils.placeholder(
                                            "min", String.valueOf(RandomRewardFunction.this.rewardMinCount)));
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
                        this.button = new MenuButton(Material.GOLD_INGOT);
                        this.button.setDisplayName("&d&lMax Rewards");
                        this.button.setAmount(RandomRewardFunction.this.rewardMaxCount);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.random-reward.ask-max");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        RandomRewardFunction.this.rewardMaxCount =
                                value.orElse(RandomRewardFunction.this.rewardMaxCount);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.random-reward.max-set",
                                LangUtils.placeholder(
                                        "max", String.valueOf(RandomRewardFunction.this.rewardMaxCount)));
                    }
                });
    }
}
