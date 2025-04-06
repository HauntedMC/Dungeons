package net.playavalon.mythicdungeons.gui;

import java.util.List;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.avngui.GUI.GUIInventory;
import net.playavalon.mythicdungeons.avngui.GUI.Window;
import net.playavalon.mythicdungeons.avngui.GUI.Buttons.Button;
import net.playavalon.mythicdungeons.player.party.partyfinder.RecruitmentListing;
import net.playavalon.mythicdungeons.utility.helpers.ItemUtils;
import net.playavalon.mythicdungeons.utility.helpers.LoreUtils;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.entity.Player;

public class RecruitGUIHandler {
   public static void initPartyBrowser() {
      Window gui = new Window("partybrowser", 54, "&8Party Listings");
      gui.addOpenAction("populate", open -> {
         Player player = (Player)open.getPlayer();
         GUIInventory inv = gui.getPlayersGui(player);

         for (int i = 0; i < 45; i++) {
            inv.removeButton(i);
         }

         int slot = 0;

         for (RecruitmentListing listing : MythicDungeons.inst().getListingManager().getListings()) {
            Player host = listing.getHost().getPlayer();
            Button button = new Button(host.getName(), ItemUtils.getPlayerHead(host));
            button.setDisplayName("&e" + listing.getLabel());
            List<String> lore = LoreUtils.wrapLore(Util.fullColor("&f" + listing.getDescription()), true);
            if (!listing.getPassword().isEmpty()) {
               lore.add("");
               lore.add(Util.fullColor("&cPassword Required!"));
            }

            lore.add(0, "");
            lore.add(0, Util.fullColor("&a" + listing.getParty().getPlayers().size() + "/" + listing.getPartySize() + " &7| " + host.getDisplayName()));
            button.setLore(lore);
            button.addAction("join", click -> {
               Player clicker = (Player)click.getWhoClicked();
               if (listing.join(MythicDungeons.inst().getMythicPlayer(clicker))) {
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
