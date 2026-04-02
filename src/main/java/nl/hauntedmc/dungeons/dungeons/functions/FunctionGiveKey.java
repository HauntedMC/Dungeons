package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.file.LangUtils;
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
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      if (this.item != null) {
         if (!targets.isEmpty() && !this.drop) {
            ItemStack key = this.item;

             if (ItemUtils.verifyDungeonItem(this.item)) {
                 ItemMeta preMeta = key.getItemMeta();
                 PersistentDataContainer data = preMeta.getPersistentDataContainer();
                 data.set(new NamespacedKey(Dungeons.inst(), "DungeonItem"), PersistentDataType.INTEGER, 1);
                 key.setItemMeta(preMeta);
             }

             key.addUnsafeEnchantments(this.item.getEnchantments());

             IDungeonParty party = this.instance.getPlayers().getFirst().getiDungeonParty();
            if (targets.size() == 1) {
               Player player = targets.getFirst().getPlayer();
               ItemUtils.giveOrDropSilently(player, key);
               if (this.notify) {
                  if (party == null) {
                     this.instance.messagePlayers(LangUtils.getMessage("instance.functions.keys.key-player", player.getName()));
                  } else {
                     party.partyMessage(LangUtils.getMessage("instance.functions.keys.key-player", player.getName()));
                  }
               }

            } else {
               OfflinePlayer oLeader = party.getLeader();
               Player leader;
               if (oLeader.isOnline()) {
                  leader = oLeader.getPlayer();
               } else {
                  leader = party.getPlayers().getFirst();
               }

               if (this.notify) {
                  ItemUtils.giveOrDrop(leader, key);
                  this.instance.messagePlayers(LangUtils.getMessage("instance.functions.keys.key-leader", leader.getName()));
               } else {
                  ItemUtils.giveOrDropSilently(leader, key);
               }

            }
             if (this.trigger != null && !this.trigger.isAllowRetrigger()) {
                this.disable();
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
