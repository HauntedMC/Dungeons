package nl.hauntedmc.dungeons.dungeons.functions;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.DeclaredFunction;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.api.events.TriggerFireEvent;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.elements.FunctionCategory;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.StringUtils;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Axis;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.block.data.Rotatable;
import org.bukkit.entity.Player;

@DeclaredFunction
public class FunctionBlockEditor extends DungeonFunction {
   @SavedField
   private String blockType = "AIR";
   @SavedField
   private boolean remove = false;
   @SavedField
   private String direction = "NORTH";

   public FunctionBlockEditor(Map<String, Object> config) {
      super("Block Editor", config);
      this.setCategory(FunctionCategory.LOCATION);
   }

   public FunctionBlockEditor() {
      super("Block Editor");
      this.setCategory(FunctionCategory.LOCATION);
   }

   @Override
   public void init() {
      super.init();
      if (this.remove) {
         this.setDisplayName(this.blockType + " Remover");
      } else {
         this.setDisplayName(this.blockType + " Placer");
      }
   }

   @Override
   public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayer> targets) {
      Block block = this.location.getBlock();
      if (this.remove) {
         if (this.blockType.equalsIgnoreCase("ANY") || block.getType() == Material.valueOf(this.blockType)) {
            block.setType(Material.AIR);
         }
      } else {
         Material mat = Material.valueOf(this.blockType);
         block.setType(mat);
         BlockData data = block.getBlockData();
         BlockState state = block.getState();
         switch (data) {
              case Directional dirData -> {
                  try {
                      BlockFace dir = BlockFace.valueOf(this.direction);
                      dirData.setFacing(dir);
                      state.setBlockData(dirData);
                      state.update(true);
                  } catch (IllegalArgumentException var11) {
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cERROR :: Block editor function has an invalid direction '"
                                                      + this.direction
                                                      + "' in dungeon '"
                                                      + this.instance.getDungeon().getWorldName()
                                                      + "'"
                                      )
                              );
                      Dungeons.inst().getLogger().info(HelperUtils.colorize("&cValid values are: " + Arrays.toString(BlockFace.values())));
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cFunction is at X: " + this.location.getBlockX() + ", Y: " + this.location.getBlockY() + ", Z: " + this.location.getBlockZ()
                                      )
                              );
                  }
              }
              case Rotatable rotData -> {
                  try {
                      BlockFace dir = BlockFace.valueOf(this.direction);
                      rotData.setRotation(dir);
                      state.setBlockData(rotData);
                      state.update(true);
                  } catch (IllegalArgumentException var10) {
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cERROR :: Block editor function has an invalid direction '"
                                                      + this.direction
                                                      + "' in dungeon '"
                                                      + this.instance.getDungeon().getWorldName()
                                                      + "'"
                                      )
                              );
                      Dungeons.inst().getLogger().info(HelperUtils.colorize("&cValid values are: " + Arrays.toString(BlockFace.values())));
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cFunction is at X: " + this.location.getBlockX() + ", Y: " + this.location.getBlockY() + ", Z: " + this.location.getBlockZ()
                                      )
                              );
                  }
              }
              case Orientable rotData -> {
                  try {
                      Axis dir = Axis.valueOf(this.direction);
                      rotData.setAxis(dir);
                      state.setBlockData(rotData);
                      state.update(true);
                  } catch (IllegalArgumentException var9) {
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cERROR :: Block editor function has an invalid direction '"
                                                      + this.direction
                                                      + "' in dungeon '"
                                                      + this.instance.getDungeon().getWorldName()
                                                      + "'"
                                      )
                              );
                      Dungeons.inst().getLogger().info(HelperUtils.colorize("&cValid values are: " + Arrays.toString(Axis.values())));
                      Dungeons.inst()
                              .getLogger()
                              .info(
                                      HelperUtils.colorize(
                                              "&cFunction is at X: " + this.location.getBlockX() + ", Y: " + this.location.getBlockY() + ", Z: " + this.location.getBlockZ()
                                      )
                              );
                  }
              }
              default -> {
              }
          }
      }
   }

   @Override
   public MenuButton buildMenuButton() {
      MenuButton functionButton = new MenuButton(Material.OBSERVER);
      functionButton.setDisplayName("&dBlock Editor");
      functionButton.addLore("&ePlaces or removes a specified");
      functionButton.addLore("&eblock at the function's location.");
      return functionButton;
   }

   @Override
   public void buildHotbarMenu() {
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.GRASS_BLOCK);
                  this.button.setDisplayName("&d&lBlock Type");
               }

               @Override
               public void onSelect(Player player) {
                  if (!FunctionBlockEditor.this.remove) {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat kind of block should be placed here?"));
                  } else {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat kind of block will be removed? ('ANY' for any block.)"));
                  }

                  player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent block is: &6" + FunctionBlockEditor.this.blockType));
               }

               @Override
               public void onInput(Player player, String message) {
                  String matString = message.toUpperCase();

                  try {
                     if (FunctionBlockEditor.this.remove && matString.equals("ANY")) {
                        FunctionBlockEditor.this.blockType = matString;
                        FunctionBlockEditor.this.setDisplayName("Block Remover");
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet block to '&6" + FunctionBlockEditor.this.blockType + "&a'"));
                        return;
                     }

                     Material mat = Material.valueOf(matString);
                     if (!mat.isBlock()) {
                        throw new IllegalArgumentException("Invalid block");
                     }

                     FunctionBlockEditor.this.blockType = matString;
                     if (!FunctionBlockEditor.this.remove) {
                        FunctionBlockEditor.this.setDisplayName(FunctionBlockEditor.this.blockType + " Placer");
                     } else {
                        FunctionBlockEditor.this.setDisplayName(FunctionBlockEditor.this.blockType + " Remover");
                     }

                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet block to '&6" + FunctionBlockEditor.this.blockType + "&a'"));
                  } catch (IllegalArgumentException var5) {
                     player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou must specify a valid block material!"));
                     StringUtils.sendClickableLink(
                        player,
                        Dungeons.logPrefix + HelperUtils.colorize("&bClick here to view a list of valid materials."),
                        "https://hub.spigotmc.org/javadocs/bukkit/org/bukkit/Material.html"
                     );
                  }
               }
            }
         );
      this.menu.addMenuItem(new ToggleMenuItem() {
         @Override
         public void buildButton() {
            this.button = new MenuButton(Material.BARRIER);
            this.button.setDisplayName("&d&lToggle Remover");
         }

         @Override
         public void onSelect(Player player) {
            if (!FunctionBlockEditor.this.remove) {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6function &cremoves &6the block&a'"));
               FunctionBlockEditor.this.setDisplayName(FunctionBlockEditor.this.blockType + " Remover");
            } else {
               player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet to '&6function &bplaces &6the specified block&a'"));
               FunctionBlockEditor.this.setDisplayName(FunctionBlockEditor.this.blockType + " Placer");
            }

            FunctionBlockEditor.this.remove = !FunctionBlockEditor.this.remove;
         }
      });
      this.menu
         .addMenuItem(
            new ChatMenuItem() {
               @Override
               public void buildButton() {
                  this.button = new MenuButton(Material.MAGENTA_GLAZED_TERRACOTTA);
                  this.button.setDisplayName("&d&lBlock Direction");
               }

               @Override
               public void onSelect(Player player) {
                  Material mat = Material.valueOf(FunctionBlockEditor.this.blockType);
                  BlockData data = mat.createBlockData();
                  if (FunctionBlockEditor.this.remove) {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&cYou can't set a direction when removing a block!"));
                     this.setCancelled(true);
                  } else if (!(data instanceof Directional) && !(data instanceof Rotatable) && !(data instanceof Orientable)) {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&cYou can't set a direction for this block type!"));
                     this.setCancelled(true);
                  } else {
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eWhat direction should the block face?"));
                     player.sendMessage(HelperUtils.colorize(Dungeons.logPrefix + "&eCurrent direction is: &6" + FunctionBlockEditor.this.direction));
                  }
               }

               @Override
               public void onInput(Player player, String message) {
                  String directionString = message.toUpperCase();
                  Material mat = Material.valueOf(FunctionBlockEditor.this.blockType);
                  BlockData data = mat.createBlockData();
                  if (data instanceof Directional) {
                     try {
                        FunctionBlockEditor.this.direction = directionString;
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet direction to '&6" + FunctionBlockEditor.this.direction + "&a'"));
                     } catch (IllegalArgumentException var9) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou must specify a valid direction!"));
                        StringUtils.sendClickableLink(
                           player,
                           Dungeons.logPrefix + HelperUtils.colorize("&bClick here to view a list of valid directions."),
                           "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/BlockFace.html"
                        );
                     }
                  } else if (data instanceof Rotatable) {
                     try {
                        FunctionBlockEditor.this.direction = directionString;
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet direction to '&6" + FunctionBlockEditor.this.direction + "&a'"));
                     } catch (IllegalArgumentException var8) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou must specify a valid direction!"));
                        StringUtils.sendClickableLink(
                           player,
                           Dungeons.logPrefix + HelperUtils.colorize("&bClick here to view a list of valid directions."),
                           "https://hub.spigotmc.org/javadocs/spigot/org/bukkit/block/BlockFace.html"
                        );
                     }
                  } else {
                     try {
                        FunctionBlockEditor.this.direction = directionString;
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&aSet axis to '&6" + FunctionBlockEditor.this.direction + "&a'"));
                     } catch (IllegalArgumentException var7) {
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&cYou must specify a valid direction!"));
                        player.sendMessage(Dungeons.logPrefix + HelperUtils.colorize("&bValid directions for this block type are: X, Y, Z"));
                     }
                  }
               }
            }
         );
   }

   public String getBlockType() {
      return this.blockType;
   }

   public void setBlockType(String blockType) {
      this.blockType = blockType;
   }

   public boolean isRemove() {
      return this.remove;
   }

   public String getDirection() {
      return this.direction;
   }
}
