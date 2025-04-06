package net.playavalon.mythicdungeons.utility;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.utility.helpers.Util;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Display.Billboard;
import org.bukkit.entity.TextDisplay.TextAlignment;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;

public class DisplayHandler {
   private Map<Location, TextDisplay> textHolograms = new HashMap<>();
   private Map<Location, TextDisplay> temporaryTexts = new HashMap<>();

   public TextDisplay setHologram(Location loc, float viewRange, String text, boolean isLabel) {
      TextDisplay display = this.textHolograms.get(loc);
      if (display == null) {
         display = this.createHologram(loc, viewRange, text, isLabel);
      }

      if (display == null) {
         return null;
      } else {
         display.setText(Util.fullColor(text.replace("\\n", "\n")));
         display.setViewRange(viewRange / 64.0F);
         return display;
      }
   }

   public TextDisplay createHologram(Location loc, float viewRange, String text, boolean isLabel) {
      if (loc.getWorld() == null) {
         return null;
      } else {
         TextDisplay display = (TextDisplay)loc.getWorld().spawn(loc, TextDisplay.class);
         display.setAlignment(TextAlignment.CENTER);
         display.setBillboard(Billboard.CENTER);
         display.setText(Util.fullColor(text));
         display.setViewRange(viewRange / 64.0F);
         if (isLabel) {
            display.getPersistentDataContainer().set(new NamespacedKey(MythicDungeons.inst(), "dungeonhologram"), PersistentDataType.BOOLEAN, true);
         }

         this.textHolograms.put(loc, display);
         return display;
      }
   }

   public TextDisplay cloneHologram(Location loc, TextDisplay display) {
      if (loc.getWorld() == null) {
         return null;
      } else {
         TextDisplay newDisplay = (TextDisplay)loc.getWorld().spawn(loc, TextDisplay.class);
         newDisplay.setAlignment(TextAlignment.CENTER);
         newDisplay.setBillboard(Billboard.CENTER);
         newDisplay.setText(display.getText());
         newDisplay.setViewRange(display.getViewRange());
         newDisplay.getPersistentDataContainer().set(new NamespacedKey(MythicDungeons.inst(), "dungeonhologram"), PersistentDataType.BOOLEAN, true);
         this.textHolograms.put(loc, newDisplay);
         return newDisplay;
      }
   }

   public void hideHologram(Location loc) {
      TextDisplay display = this.textHolograms.get(loc);
      if (display != null) {
         this.hideHologram(display);
      }
   }

   public void hideHologram(TextDisplay display) {
      if (display != null) {
         display.setViewRange(0.0F);
      }
   }

   public void showHologram(Location loc, float range) {
      TextDisplay display = this.textHolograms.get(loc);
      if (display != null) {
         this.showHologram(display, range);
      }
   }

   public void showHologram(TextDisplay display, float range) {
      if (display != null) {
         display.setViewRange(range / 64.0F);
      }
   }

   @Nullable
   public TextDisplay getHologram(Location loc) {
      return this.textHolograms.get(loc);
   }

   public void removeHologram(Location loc) {
      TextDisplay display = this.textHolograms.remove(loc);
      if (display != null) {
         display.remove();
      }
   }

   public void clear() {
      for (TextDisplay display : this.textHolograms.values()) {
         display.remove();
      }

      this.textHolograms.clear();
   }

   public void temporaryClear() {
      this.temporaryTexts = new HashMap<>(this.textHolograms);
      this.clear();
   }

   public void restore() {
      for (Entry<Location, TextDisplay> pair : this.temporaryTexts.entrySet()) {
         Location loc = pair.getKey();
         TextDisplay display = pair.getValue();
         this.cloneHologram(loc, display);
      }
   }
}
