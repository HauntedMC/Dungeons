package nl.hauntedmc.dungeons.api.parents.elements;

public enum FunctionCategory {
   DUNGEON("#fca103"),
   PLAYER("#0bfc03"),
   LOCATION("#fc03fc"),
   META("#38e5fc"),
   ROOM("#fc0303");

   private final String color;

   FunctionCategory(String hexColor) {
      this.color = hexColor;
   }

   public String getColor() {
      return this.color;
   }
}
