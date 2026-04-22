package nl.hauntedmc.dungeons.content.function.reward;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.reward.LootTable;
import nl.hauntedmc.dungeons.gui.framework.buttons.Button;
import nl.hauntedmc.dungeons.gui.framework.window.GuiInventory;
import nl.hauntedmc.dungeons.gui.framework.window.GuiWindow;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.math.MathUtils;
import nl.hauntedmc.dungeons.util.text.MessageUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/**
 * Reward function that pulls items from a named loot table.
 */
@AutoRegister(id = "dungeons.function.loot_table_rewards")
@SerializableAs("dungeons.function.loot_table_rewards")
public class LootTableRewardsFunction extends RewardFunction {
    @PersistedField private String loottableName = "";

    /**
     * Creates a new LootTableRewardsFunction instance.
     */
    public LootTableRewardsFunction(Map<String, Object> config) {
        super("Loot Table Rewards", config);
    }

    /**
     * Creates a new LootTableRewardsFunction instance.
     */
    public LootTableRewardsFunction() {
        super("Loot Table Rewards");
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.SHULKER_BOX);
        functionButton.setDisplayName("&eLoot Table Rewards");
        functionButton.addLore("&eGenerates rewards from a loot");
        functionButton.addLore("&etable when opened.");
        functionButton.addLore("");
        functionButton.addLore("&7Use /dungeon loot [create/edit]");
        functionButton.addLore("&7to create or edit a loot table.");
        return functionButton;
    }

    /**
     * Initializes rewards gui.
     */
    @Override
    public void initializeRewardsGui() {}

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
                                LootTable table =
                                        RuntimeContext.lootTableRepository().get(this.loottableName);
                                if (table == null) {
                                    this.logger()
                                            .warn(
                                                    "Loot table reward in dungeon '{}' at {} references missing loot table '{}'.",
                                                    this.dungeonNameForLogs(),
                                                    this.locationForLogs(),
                                                    this.loottableName);
                                    LangUtils.sendMessage(player, "instance.play.rewards.invalid-loottable");
                                } else {
                                    this.playerRewards.add(player.getUniqueId());
                                    if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                                        LangUtils.sendMessage(player, "instance.play.rewards.rewards-inv-info");
                                        LangUtils.sendMessage(player, "instance.play.rewards.view-rewards-info");
                                    }

                                    GuiInventory guiInv = this.instanceGui.getInventoryFor(player);
                                    Inventory inv = guiInv.inventory();
                                    List<ItemStack> items;
                                    if (instance.getDifficulty() != null) {
                                        items =
                                                table.randomizeItems(
                                                        instance.getDifficulty().getBonusLoot().randomizeAsInt());
                                    } else {
                                        items = table.randomizeItems();
                                    }

                                    List<Integer> occupied = new ArrayList<>();

                                    for (ItemStack item : items) {
                                        if (occupied.size() >= 54) {
                                            break;
                                        }

                                        int i = MathUtils.getRandomNumberInRange(0, 53);

                                        while (occupied.contains(i)) {
                                            i = MathUtils.getRandomNumberInRange(0, 53);
                                        }

                                        occupied.add(i);
                                        if (instance.getConfig().getBoolean("rewards.deliver_on_finish", false)) {
                                            if (item != null) {
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
                                            }
                                        } else {
                                            inv.setItem(i, item);
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
                    Player player = (Player) event.getPlayer();
                    if ((this.trigger == null || !this.trigger.isAllowRetrigger())
                            && (this.parentFunction == null
                                    || this.parentFunction.getTrigger() == null
                                    || !this.parentFunction.getTrigger().isAllowRetrigger())) {
                        if (!(this.instance instanceof EditableInstance)) {
                            PlayableInstance instance = (PlayableInstance) this.instance;
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
                        this.button = new MenuButton(Material.CHEST);
                        this.button.setDisplayName("&d&lSet Loot Table");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.loot-table-rewards.ask-loot-table");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.loot-table-rewards.current-loot-table",
                                LangUtils.placeholder("loot_table", LootTableRewardsFunction.this.loottableName));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        if (message.contains(" ")) {
                            LangUtils.sendMessage(player, "editor.function.loot-table-rewards.warning-no-spaces");
                            LangUtils.sendMessage(
                                    player, "editor.function.loot-table-rewards.warning-spaces-replaced");
                            message = message.replace(" ", "_");
                        }

                        if (!RuntimeContext.lootTableRepository().contains(message)) {
                            LangUtils.sendMessage(player, "editor.function.loot-table-rewards.warning-not-found");
                            MessageUtils.sendClickableCommand(
                                    player,
                                    RuntimeContext.logPrefix() + "&b&lClick here &bto create it!",
                                    "dungeon loot create " + message);
                        }

                        LootTableRewardsFunction.this.loottableName = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.loot-table-rewards.loot-table-set",
                                LangUtils.placeholder("loot_table", LootTableRewardsFunction.this.loottableName));
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
                        this.button.setAmount(LootTableRewardsFunction.this.xp);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.loot-table-rewards.ask-exp");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        LootTableRewardsFunction.this.xp = value.orElse(LootTableRewardsFunction.this.xp);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.loot-table-rewards.exp-set",
                                    LangUtils.placeholder("xp", String.valueOf(LootTableRewardsFunction.this.xp)));
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
                        this.button.setAmount(LootTableRewardsFunction.this.levels);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.loot-table-rewards.ask-levels");
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Integer> value = InputUtils.readIntegerInput(player, message);
                        LootTableRewardsFunction.this.levels =
                                value.orElse(LootTableRewardsFunction.this.levels);
                        if (value.isPresent()) {
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.loot-table-rewards.levels-set",
                                    LangUtils.placeholder(
                                            "levels", String.valueOf(LootTableRewardsFunction.this.levels)));
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
                        playerSession.showHotbar(LootTableRewardsFunction.this.cooldownMenu, true);
                    }
                });
    }
}
