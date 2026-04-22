package nl.hauntedmc.dungeons.content.trigger;

import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.content.instance.play.BranchingInstance;
import nl.hauntedmc.dungeons.generation.room.InstanceRoom;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonTrigger;
import nl.hauntedmc.dungeons.model.element.TriggerCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import nl.hauntedmc.dungeons.util.text.ComponentUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;

/**
 * Trigger that reacts to matching chat messages from players in the instance.
 *
 * <p>Branching instances can optionally scope the trigger to the room that contains the trigger
 * location.
 */
@AutoRegister(id = "dungeons.trigger.chat")
@SerializableAs("dungeons.trigger.chat")
public class ChatTrigger extends DungeonTrigger {
    @PersistedField private String text = "DEFAULT";
    @PersistedField private boolean caseSensitive = false;
    @PersistedField private boolean exact = true;

    /**
     * Creates a new ChatTrigger instance.
     */
    public ChatTrigger(Map<String, Object> config) {
        super("Chat Message", config);
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Creates a new ChatTrigger instance.
     */
    public ChatTrigger() {
        super("Chat Message");
        this.setCategory(TriggerCategory.PLAYER);
        this.setHasTarget(true);
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton functionButton = new MenuButton(Material.NAME_TAG);
        functionButton.setDisplayName("&aChat Message");
        functionButton.addLore("&eTriggered when a player sends");
        functionButton.addLore("&ea message with matching or");
        functionButton.addLore("&easimilar text.");
        return functionButton;
    }

    /**
     * Performs on chat.
     */
    @EventHandler(priority = EventPriority.LOW)
    public void onChat(AsyncChatEvent event) {
        String message = ComponentUtils.plainText(event.originalMessage());
        if (!this.matchesMessage(message)) {
            return;
        }

        if (this.handleTriggerChat(event.getPlayer().getUniqueId(), event.isAsynchronous())) {
            event.setCancelled(true);
        }
    }

    /**
     * Performs matches room.
     */
    private boolean matchesRoom(Location origin) {
        BranchingInstance instance = this.instance.as(BranchingInstance.class);
        if (instance == null) {
            return true;
        } else if (!this.limitToRoom) {
            return true;
        } else {
            InstanceRoom originRoom = instance.getRoom(origin);
            InstanceRoom thisRoom = instance.getRoom(this.location);
            return thisRoom == originRoom;
        }
    }

    /**
     * Performs matches message.
     */
    private boolean matchesMessage(String message) {
        if (this.exact) {
            if (this.caseSensitive) {
                return message.equals(this.text);
            }

            return message.equalsIgnoreCase(this.text);
        }

        if (this.caseSensitive) {
            return message.contains(this.text);
        }

        return message
                .toLowerCase(java.util.Locale.ROOT)
                .contains(this.text.toLowerCase(java.util.Locale.ROOT));
    }

    /**
     * Performs handle trigger chat.
     */
    private boolean handleTriggerChat(UUID playerId, boolean asynchronous) {
        if (!asynchronous) {
            return this.handleTriggerChatSync(playerId);
        }

        try {
            // Chat can arrive off-thread on Paper, but dungeon state and Bukkit world interaction
            // must still be checked on the main thread.
            return Bukkit.getScheduler()
                    .callSyncMethod(
                            RuntimeContext.plugin(), () -> this.handleTriggerChatSync(playerId))
                    .get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        } catch (ExecutionException exception) {
            Throwable root = exception.getCause() == null ? exception : exception.getCause();
            RuntimeContext.logger()
                    .error(
                            "Failed to process chat trigger '{}' for player '{}'.",
                            this.getNamespace(),
                            playerId,
                            root);
            return false;
        }
    }

    /**
     * Performs handle trigger chat sync.
     */
    private boolean handleTriggerChatSync(UUID playerId) {
        DungeonPlayerSession session = RuntimeContext.playerSessions().get(playerId);
        if (session == null || session.getInstance() != this.instance) {
            return false;
        }

        Player player = session.getPlayer();
        if (player == null || !player.isOnline() || !this.matchesRoom(player.getLocation())) {
            return false;
        }

        player.playSound(player.getLocation(), "minecraft:entity.experience_orb.pickup", 0.5F, 1.2F);
        this.trigger(session);
        return true;
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
                        this.button = new MenuButton(Material.REDSTONE_TORCH);
                        this.button.setDisplayName("&d&lAllow Retrigger");
                        this.button.setEnchanted(ChatTrigger.this.allowRetrigger);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!ChatTrigger.this.allowRetrigger) {
                            LangUtils.sendMessage(player, "editor.trigger.chat.allow-repeat");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.chat.prevent-repeat");
                        }

                        ChatTrigger.this.allowRetrigger = !ChatTrigger.this.allowRetrigger;
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
                        this.button.setDisplayName("&d&lEdit Required Text");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.trigger.chat.ask-text");
                        LangUtils.sendMessage(
                                player,
                                "editor.trigger.chat.current-text",
                                LangUtils.placeholder("text", ChatTrigger.this.text));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        ChatTrigger.this.text = message;
                        LangUtils.sendMessage(
                                player, "editor.trigger.chat.text-set", LangUtils.placeholder("text", message));
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.NAME_TAG);
                        this.button.setDisplayName("&d&lExact Text");
                        this.button.setEnchanted(ChatTrigger.this.exact);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!ChatTrigger.this.exact) {
                            LangUtils.sendMessage(player, "editor.trigger.chat.match-exact");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.chat.match-contains");
                        }

                        ChatTrigger.this.exact = !ChatTrigger.this.exact;
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
                        this.button.setDisplayName("&d&lCase Sensitive");
                        this.button.setEnchanted(ChatTrigger.this.caseSensitive);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!ChatTrigger.this.caseSensitive) {
                            LangUtils.sendMessage(player, "editor.trigger.chat.case-sensitive");
                        } else {
                            LangUtils.sendMessage(player, "editor.trigger.chat.case-insensitive");
                        }

                        ChatTrigger.this.caseSensitive = !ChatTrigger.this.caseSensitive;
                    }
                });
        this.addRoomLimitToggleButton();
    }
}
