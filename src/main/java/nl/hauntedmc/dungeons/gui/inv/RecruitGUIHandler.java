package nl.hauntedmc.dungeons.gui.inv;

import java.util.List;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.gui.window.GUIInventory;
import nl.hauntedmc.dungeons.api.gui.window.GUIWindow;
import nl.hauntedmc.dungeons.api.gui.buttons.Button;
import nl.hauntedmc.dungeons.player.party.partyfinder.RecruitmentListing;
import nl.hauntedmc.dungeons.util.entity.ItemUtils;
import nl.hauntedmc.dungeons.util.entity.LoreUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.entity.Player;

public class RecruitGUIHandler {
   public static void initPartyBrowser() {
      GUIWindow gui = new GUIWindow("partybrowser", 54, "&8Party Listings");
      gui.addOpenAction("populate", open -> {
         Player player = (Player)open.getPlayer();
         GUIInventory inv = gui.getPlayersGui(player);

         for (int i = 0; i < 45; i++) {
            inv.removeButton(i);
         }

         int slot = 0;

         for (RecruitmentListing listing : Dungeons.inst().getListingManager().getListings()) {
            Player host = listing.getHost().getPlayer();
            Button button = new Button(host.getName(), ItemUtils.getPlayerHead(host));
            button.setDisplayName("&e" + listing.getLabel());
            List<String> lore = LoreUtils.wrapLore(HelperUtils.fullColor("&f" + listing.getDescription()), true);
            if (!listing.getPassword().isEmpty()) {
               lore.add("");
               lore.add(HelperUtils.fullColor("&cPassword Required!"));
            }

            lore.addFirst("");
            lore.addFirst(HelperUtils.fullColor("&a" + listing.getParty().getPlayers().size() + "/" + listing.getPartySize() + " &7| " + HelperUtils.playerDisplayName(host)));
            button.setLore(lore);
            button.addAction("join", click -> {
               Player clicker = (Player)click.getWhoClicked();
               if (listing.join(Dungeons.inst().getDungeonPlayer(clicker))) {
                  clicker.closeInventory();
               }
            });
            inv.setButton(slot, button);
            if (++slot > 44) {
               break;
            }
         }
      });
   }
}
