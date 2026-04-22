package nl.hauntedmc.dungeons.content.function;

import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.model.instance.PlayableInstance;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.instance.InstanceUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;

/**
 * Function that sends a configured chat message to its targets.
 */
@AutoRegister(id = "dungeons.function.message")
@SerializableAs("dungeons.function.message")
public class MessageFunction extends DungeonFunction {
    @PersistedField private String message = "DEFAULT";
    @PersistedField private int messageType = 0;

    /**
     * Creates a new MessageFunction instance.
     */
    public MessageFunction(Map<String, Object> config) {
        super("Message", config);
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Creates a new MessageFunction instance.
     */
    public MessageFunction() {
        super("Message");
        this.setCategory(FunctionCategory.PLAYER);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        PlayableInstance instance = this.instance.asPlayInstance();
        if (instance != null) {
            for (DungeonPlayerSession dPlayer : targets) {
                Player player = dPlayer.getPlayer();
                String message = this.message;
                message = InstanceUtils.parseVars(instance, message);
                switch (this.messageType) {
                    case 0:
                        player.sendMessage(ComponentUtils.component(message));
                        break;
                    case 1:
                        player.sendActionBar(ComponentUtils.component(message));
                }
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.PAPER);
        functionButton.setDisplayName("&aMessage Sender");
        functionButton.addLore("&eSends a chat or action bar");
        functionButton.addLore("&emessage to the target player(s).");
        return functionButton;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new MenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
                        this.button.setDisplayName("&d&lMessage Type");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(PlayerEvent event) {
                        Player player = event.getPlayer();
                        MessageFunction.this.messageType++;
                        if (MessageFunction.this.messageType >= MessageType.values().length) {
                            MessageFunction.this.messageType = 0;
                        }

                        MessageType type = MessageType.intToType(MessageFunction.this.messageType);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.message.type-set",
                                LangUtils.placeholder("type", type.toString()));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.PAPER);
                        this.button.setDisplayName("&d&lEdit Message");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.message.ask-message");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.message.current-message",
                                LangUtils.placeholder("message", MessageFunction.this.message));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        MessageFunction.this.message = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.message.message-set",
                                LangUtils.placeholder("message", message));
                    }
                });
    }

    /**
     * Sets the message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Sets the message type.
     */
    public void setMessageType(int messageType) {
        this.messageType = messageType;
    }

    private enum MessageType {
        CHAT,
        ACTION_BAR;

        /**
         * Performs int to type.
         */
        public static MessageType intToType(int index) {
                        return switch (index) {
                case 1 -> ACTION_BAR;
                default -> CHAT;
            };
        }
    }
}
