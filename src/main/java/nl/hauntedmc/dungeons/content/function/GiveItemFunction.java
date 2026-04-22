package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Function that gives or drops a configured item stack.
 *
 * <p>When the item is recognized as a dungeon-owned item, the function reapplies the identifying
 * persistent-data marker before delivery.
 */
@AutoRegister(id = "dungeons.function.give_item")
@SerializableAs("dungeons.function.give_item")
public class GiveItemFunction extends DungeonFunction {
    @PersistedField protected ItemStack item;
    @PersistedField protected boolean drop = false;
    @PersistedField protected boolean notify = true;

    /**
     * Creates a new GiveItemFunction instance.
     */
    public GiveItemFunction(String namespace, Map<String, Object> config) {
        super(namespace, config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Creates a new GiveItemFunction instance.
     */
    public GiveItemFunction(Map<String, Object> config) {
        super("Item Dispenser", config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Creates a new GiveItemFunction instance.
     */
    public GiveItemFunction(String namespace) {
        super(namespace);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Creates a new GiveItemFunction instance.
     */
    public GiveItemFunction() {
        super("Item Dispenser");
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.item != null) {
            ItemStack item = this.item.clone();

            if (ItemUtils.verifyDungeonItem(this.item)) {
                ItemMeta preMeta = item.getItemMeta();
                if (preMeta != null) {
                    PersistentDataContainer data = preMeta.getPersistentDataContainer();
                    data.set(
                                                        new NamespacedKey(RuntimeContext.plugin(), "DungeonItem"),
                            PersistentDataType.INTEGER,
                            1);
                    item.setItemMeta(preMeta);
                } else {
                    this.logger()
                            .warn(
                                    "GiveItemFunction in dungeon '{}' at {} could not mark a dungeon item because the item has no metadata.",
                                    this.dungeonNameForLogs(),
                                    this.locationForLogs());
                }
            }

            item.addUnsafeEnchantments(this.item.getEnchantments());

            if (!targets.isEmpty() && !this.drop) {
                for (DungeonPlayerSession playerSession : targets) {
                    String itemName = ItemUtils.getItemDisplayName(item);

                    Player player = playerSession.getPlayer();
                    if (this.notify) {
                        LangUtils.sendMessage(
                                player,
                                "instance.play.functions.item-dispenser",
                                LangUtils.placeholder("amount", String.valueOf(item.getAmount())),
                                LangUtils.placeholder("item", itemName));
                    }

                    ItemUtils.giveOrDropSilently(player, item.clone());
                }
            } else {
                this.instance.getInstanceWorld().dropItem(this.location, item.clone());
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.DROPPER);
        functionButton.setDisplayName("&aItem Dispenser");
        functionButton.addLore("&eGives or drops an item at this");
        functionButton.addLore("&elocation.");
        return functionButton;
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
                        this.button = new MenuButton(Material.ITEM_FRAME);
                        this.button.setDisplayName("&d&lChoose Item");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        RuntimeContext.guiService().openGui(player, "selectitem_function");
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.DROPPER);
                        this.button.setDisplayName("&d&lToggle Drop");
                        this.button.setEnchanted(GiveItemFunction.this.drop);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!GiveItemFunction.this.drop) {
                            LangUtils.sendMessage(player, "editor.function.give-item.drop-at-location");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.give-item.give-to-targets");
                        }

                        GiveItemFunction.this.drop = !GiveItemFunction.this.drop;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lToggle Notification");
                        this.button.setEnchanted(GiveItemFunction.this.notify);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!GiveItemFunction.this.notify) {
                            LangUtils.sendMessage(player, "editor.function.give-item.notify");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.give-item.do-not-notify");
                        }

                        GiveItemFunction.this.notify = !GiveItemFunction.this.notify;
                    }
                });
    }

    /**
     * Returns the item.
     */
    public ItemStack getItem() {
        return this.item;
    }

    /**
     * Sets the item.
     */
    public void setItem(ItemStack item) {
        this.item = item;
    }

    /**
     * Returns whether drop.
     */
    public boolean isDrop() {
        return this.drop;
    }

    /**
     * Sets the drop.
     */
    public void setDrop(boolean drop) {
        this.drop = drop;
    }

    /**
     * Returns whether notify.
     */
    public boolean isNotify() {
        return this.notify;
    }

    /**
     * Sets the notify.
     */
    public void setNotify(boolean notify) {
        this.notify = notify;
    }
}
