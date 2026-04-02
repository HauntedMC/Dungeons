package nl.hauntedmc.dungeons.dungeons.variables;

import java.util.Map;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PlayerVariables {
   private Map<UUID, VariableHolder> playerVars;

   @Nullable
   public VariableHolder getPlayerVars(Player player) {
      return this.playerVars.get(player.getUniqueId());
   }

   public void addPlayer(Player player) {
      this.playerVars.put(player.getUniqueId(), new VariableHolder());
   }

   public String getString(Player player, String var) {
      VariableHolder holder = this.getPlayerVars(player);
      return holder == null ? "" : holder.getString(var);
   }

   public int getInt(Player player, String var) {
      VariableHolder holder = this.getPlayerVars(player);
      return holder == null ? 0 : holder.getInt(var);
   }

   public double getDouble(Player player, String var) {
      VariableHolder holder = this.getPlayerVars(player);
      return holder == null ? 0.0 : holder.getDouble(var);
   }

   public void set(Player player, String var, String value) {
      VariableHolder holder = this.getPlayerVars(player);
      if (holder != null) {
         holder.set(var, value);
      }
   }

   public void set(Player player, String var, int value) {
      VariableHolder holder = this.getPlayerVars(player);
      if (holder != null) {
         holder.set(var, value);
      }
   }

   public void set(Player player, String var, double value) {
      VariableHolder holder = this.getPlayerVars(player);
      if (holder != null) {
         holder.set(var, value);
      }
   }

   public void add(Player player, String var, double value) {
      VariableHolder holder = this.getPlayerVars(player);
      if (holder != null) {
         holder.add(var, value);
      }
   }

   public void subtract(Player player, String var, double value) {
      VariableHolder holder = this.getPlayerVars(player);
      if (holder != null) {
         holder.subtract(var, value);
      }
   }
}
