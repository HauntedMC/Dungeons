package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.item.ItemUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Specialized item-give function for dungeon key items.
 */
@AutoRegister(id = "dungeons.function.give_key")
@SerializableAs("dungeons.function.give_key")
public class GiveKeyFunction extends GiveItemFunction {
    /**
     * Creates a new GiveKeyFunction instance.
     */
    public GiveKeyFunction(Map<String, Object> config) {
        super("Key Dispenser", config);
        this.item = ItemUtils.getDefaultKeyItem();
    }

    /**
     * Creates a new GiveKeyFunction instance.
     */
    public GiveKeyFunction() {
        super("Key Dispenser");
        this.item = ItemUtils.getDefaultKeyItem();
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (this.item != null) {
            if (!targets.isEmpty() && !this.drop) {
                for (DungeonPlayerSession target : targets) {
                    Player player = target.getPlayer();
                    ItemStack key = this.item.clone();

                    if (ItemUtils.verifyDungeonItem(this.item)) {
                        ItemMeta preMeta = key.getItemMeta();
                        if (preMeta != null) {
                            PersistentDataContainer data = preMeta.getPersistentDataContainer();
                            data.set(
                                                                        new NamespacedKey(RuntimeContext.plugin(), "DungeonItem"),
                                    PersistentDataType.INTEGER,
                                    1);
                            key.setItemMeta(preMeta);
                        } else {
                            this.logger()
                                    .warn(
                                            "GiveKeyFunction in dungeon '{}' at {} could not mark a dungeon key because the item has no metadata.",
                                            this.dungeonNameForLogs(),
                                            this.locationForLogs());
                        }
                    }

                    key.addUnsafeEnchantments(this.item.getEnchantments());
                    ItemUtils.giveOrDropSilently(player, key);
                    if (this.notify) {
                        this.instance.messagePlayers(
                                LangUtils.getMessage(
                                        "instance.play.functions.keys.key-player",
                                        LangUtils.placeholder("player", player.getName())));
                    }
                }

                if (this.trigger != null && !this.trigger.isAllowRetrigger()) {
                    this.disable();
                }
            } else {
                this.instance.getInstanceWorld().dropItem(this.location, this.item.clone());
                this.instance.messagePlayers(
                        LangUtils.getMessage("instance.play.functions.keys.key-ground"));
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.TRIPWIRE_HOOK);
        functionButton.setDisplayName("&aKey Dispenser");
        functionButton.addLore("&eGives or drops a key item to the");
        functionButton.addLore("&eplayer for use on &dKey Item");
        functionButton.addLore("&dDetector &etriggers.");
        functionButton.addLore("");
        functionButton.addLore("&8Similar to Item Dispenser, but");
        functionButton.addLore("&8informs the players about the");
        functionButton.addLore("&8key.");
        return functionButton;
    }
}
