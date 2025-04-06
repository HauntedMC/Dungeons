package net.playavalon.mythicdungeons.dungeons.functions;

import io.lumine.mythic.bukkit.BukkitAdapter;
import io.lumine.mythic.core.items.MythicItem;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.DeclaredFunction;
import net.playavalon.mythicdungeons.api.events.dungeon.TriggerFireEvent;
import net.playavalon.mythicdungeons.api.party.IDungeonParty;
import net.playavalon.mythicdungeons.menu.MenuButton;
import net.playavalon.mythicdungeons.player.MythicPlayer;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LangUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

@DeclaredFunction
public class FunctionGiveKey extends FunctionGiveItem {
   public FunctionGiveKey(Map<String, Object> config) {
      super("Key Dispenser", config);
      this.item = ItemUtils.getDefaultKeyItem();
   }

   public FunctionGiveKey() {
      super("Key Dispenser");
      this.item = ItemUtils.getDefaultKeyItem();
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<MythicPlayer> targets) {
      if (this.item != null) {
         if (!targets.isEmpty() && !this.drop) {
            ItemStack key = this.item;
            if (MythicDungeons.inst().getMythicApi() != null) {
               String mythicItem = ItemUtils.getMythicItemType(this.item);
               if (mythicItem != null) {
                  Optional<MythicItem> mItem = MythicDungeons.inst().getMythicApi().getItemManager().getItem(mythicItem);
                  if (mItem.isPresent()) {
                     key = BukkitAdapter.adapt(mItem.get().generateItemStack(key.getAmount()));
                  }
               }
            }

            if (key != null) {
               if (ItemUtils.verifyDungeonItem(this.item)) {
                  ItemMeta preMeta = key.getItemMeta();
                  PersistentDataContainer data = preMeta.getPersistentDataContainer();
                  data.set(new NamespacedKey(MythicDungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
                  key.setItemMeta(preMeta);
               }

               key.addUnsafeEnchantments(this.item.getEnchantments());
            }

            IDungeonParty party = this.instance.getPlayers().get(0).getDungeonParty();
            if (targets.size() == 1) {
               Player player = targets.get(0).getPlayer();
               ItemUtils.giveOrDropSilently(player, key);
               if (this.notify) {
                  if (party == null) {
                     this.instance.messagePlayers(LangUtils.getMessage("instance.functions.keys.key-player", player.getName()));
                  } else {
                     party.partyMessage(LangUtils.getMessage("instance.functions.keys.key-player", player.getName()));
                  }
               }

               if (this.trigger != null && !this.trigger.isAllowRetrigger()) {
                  this.disable();
               }
            } else {
               OfflinePlayer oLeader = party.getLeader();
               Player leader;
               if (oLeader.isOnline()) {
                  leader = oLeader.getPlayer();
               } else {
                  leader = party.getPlayers().get(0);
               }

               if (this.notify) {
                  ItemUtils.giveOrDrop(leader, key);
                  this.instance.messagePlayers(LangUtils.getMessage("instance.functions.keys.key-leader", leader.getName()));
               } else {
                  ItemUtils.giveOrDropSilently(leader, key);
               }

               if (this.trigger != null && !this.trigger.isAllowRetrigger()) {
                  this.disable();
               }
            }
         } else {
            this.instance.getInstanceWorld().dropItem(this.location, this.item);
            this.instance.messagePlayers(LangUtils.getMessage("instance.functions.keys.key-ground"));
         }
      }
   }

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
