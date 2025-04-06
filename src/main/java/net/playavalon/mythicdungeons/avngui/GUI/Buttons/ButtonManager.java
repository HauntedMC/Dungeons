package net.playavalon.mythicdungeons.avngui.GUI.Buttons;

import java.util.HashMap;

public class ButtonManager {
   private static final HashMap<String, Button> buttons = new HashMap<>();

   protected static void registerButton(Button button) {
      buttons.put(button.getId(), button);
   }

   public static Button getButton(String namespace) {
      return buttons.get(namespace);
   }

   protected static void put(Button button) {
      buttons.put(button.getId(), button);
   }
}
