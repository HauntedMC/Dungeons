package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.PlayerFinishDungeonEvent;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.dungeon.DungeonDefinition;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.element.FunctionTargetType;
import nl.hauntedmc.dungeons.model.instance.EditableInstance;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that marks a run as finished for the targeted players.
 */
@AutoRegister(id = "dungeons.function.finish_dungeon")
@SerializableAs("dungeons.function.finish_dungeon")
public class FinishDungeonFunction extends DungeonFunction {
    @PersistedField private boolean leave = true;

    /**
     * Creates a new FinishDungeonFunction instance.
     */
    public FinishDungeonFunction(Map<String, Object> config) {
        super("Finish Dungeon", config);
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Creates a new FinishDungeonFunction instance.
     */
    public FinishDungeonFunction() {
        super("Finish Dungeon");
        this.targetType = FunctionTargetType.PLAYER;
        this.setCategory(FunctionCategory.DUNGEON);
        this.setAllowRetriggerByDefault(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.ENCHANTING_TABLE);
        functionButton.setDisplayName("&6Finish Dungeon");
        functionButton.addLore("&eFormally finishes the dungeon,");
        functionButton.addLore("&emarking the dungeon as finished");
        functionButton.addLore("&efor the player.");
        functionButton.addLore("&eAlso puts the dungeon on cooldown");
        functionButton.addLore("&eif there is an access cooldown.");
        return functionButton;
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (!(this.instance instanceof EditableInstance)) {
            PlayableInstance instance = (PlayableInstance) this.instance;

            for (DungeonPlayerSession playerSession : targets) {
                PlayerFinishDungeonEvent finishEvent =
                                                new PlayerFinishDungeonEvent(instance, playerSession);
                Bukkit.getPluginManager().callEvent(finishEvent);
                instance.setDungeonFinished(true);
                Player player = playerSession.getPlayer();
                LangUtils.sendMessage(
                        player,
                        "instance.play.functions.finish-dungeon",
                        LangUtils.placeholder("dungeon", instance.getDungeon().getDisplayName()));
                instance.applyLootCooldowns(player);
                instance.pushPlayerRewards(player);
                DungeonDefinition dungeon = instance.getDungeon();
                if (!dungeon.hasPlayerCompletedDungeon(player)) {
                    dungeon.setPlayerCompletedDungeon(player);
                }

                if (dungeon.isAccessCooldownEnabled()
                        && dungeon.isCooldownOnFinish()
                        && dungeon.shouldApplyAccessCooldown(instance, player.getUniqueId())) {
                    instance.getDungeon().addAccessCooldown(player);
                }

                if (this.leave) {
                    Location exitLoc = dungeon.getExitLoc();
                    if (exitLoc != null) {
                        playerSession.setSavedPosition(exitLoc);
                    }

                    instance.removePlayer(playerSession);
                }
            }
        }
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.ENCHANTING_TABLE);
                        this.button.setDisplayName("&d&lLeave Dungeon");
                        this.button.setEnchanted(FinishDungeonFunction.this.leave);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!FinishDungeonFunction.this.leave) {
                            LangUtils.sendMessage(player, "editor.function.finish-dungeon.leave-after");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.finish-dungeon.stay-after");
                        }

                        FinishDungeonFunction.this.leave = !FinishDungeonFunction.this.leave;
                    }
                });
    }

    /**
     * Returns whether leave.
     */
    public boolean isLeave() {
        return this.leave;
    }

    /**
     * Sets the leave.
     */
    public void setLeave(boolean leave) {
        this.leave = leave;
    }
}
