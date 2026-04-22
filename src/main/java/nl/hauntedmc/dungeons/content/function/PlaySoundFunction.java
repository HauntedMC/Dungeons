package nl.hauntedmc.dungeons.content.function;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import nl.hauntedmc.dungeons.annotation.AutoRegister;
import nl.hauntedmc.dungeons.annotation.PersistedField;
import nl.hauntedmc.dungeons.event.TriggerFireEvent;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ChatMenuItem;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.MenuButton;
import nl.hauntedmc.dungeons.gui.hotbar.menuitems.ToggleMenuItem;
import nl.hauntedmc.dungeons.model.element.DungeonFunction;
import nl.hauntedmc.dungeons.model.element.FunctionCategory;
import nl.hauntedmc.dungeons.runtime.RuntimeContext;
import nl.hauntedmc.dungeons.runtime.player.DungeonPlayerSession;
import nl.hauntedmc.dungeons.util.command.InputUtils;
import nl.hauntedmc.dungeons.util.lang.LangUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.configuration.serialization.SerializableAs;
import org.bukkit.entity.Player;

/**
 * Function that plays a configured sound cue for the selected targets.
 */
@AutoRegister(id = "dungeons.function.play_sound")
@SerializableAs("dungeons.function.play_sound")
public class PlaySoundFunction extends DungeonFunction {
    @PersistedField private String sound = "";
    @PersistedField private String soundCategory = "MASTER";
    @PersistedField private double pitch = 1.0;
    @PersistedField private double volume = 1.0;
    @PersistedField private boolean playAtLocation = false;

    /**
     * Creates a new PlaySoundFunction instance.
     */
    public PlaySoundFunction(Map<String, Object> config) {
        super("Sound", config);
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Creates a new PlaySoundFunction instance.
     */
    public PlaySoundFunction() {
        super("Sound");
        this.setCategory(FunctionCategory.LOCATION);
    }

    /**
     * Performs run function.
     */
    @Override
    public void runFunction(TriggerFireEvent triggerEvent, List<DungeonPlayerSession> targets) {
        if (!this.sound.isEmpty()) {
            Location loc = this.location;
            SoundCategory category = SoundCategory.MASTER;

            try {
                category = SoundCategory.valueOf(this.soundCategory);
            } catch (IllegalArgumentException exception) {
                RuntimeContext.plugin()
                        .getSLF4JLogger()
                        .warn(
                                "Invalid sound category '{}' on PlaySoundFunction at {}. Falling back to MASTER.",
                                this.soundCategory,
                                this.location == null
                                        ? "<unknown>"
                                        : this.location.getBlockX()
                                                + ","
                                                + this.location.getBlockY()
                                                + ","
                                                + this.location.getBlockZ(),
                                exception);
            }

            for (DungeonPlayerSession playerSession : targets) {
                Player player = playerSession.getPlayer();
                if (!this.playAtLocation) {
                    loc = player.getLocation();
                }
                player.playSound(loc, this.sound, category, (float) this.volume, (float) this.pitch);
            }
        }
    }

    /**
     * Builds menu button.
     */
    @Override
    public MenuButton buildMenuButton() {
        MenuButton button = new MenuButton(Material.NOTE_BLOCK);
        button.setDisplayName("&dSound Player");
        button.addLore("&ePlays a sound to the player(s).");
        return button;
    }

    /**
     * Builds hotbar menu.
     */
    @Override
    public void buildHotbarMenu() {
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.JUKEBOX);
                        this.button.setDisplayName("&d&lSet Sound");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.play-sound.ask-sound");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.current-sound",
                                LangUtils.placeholder("sound", PlaySoundFunction.this.sound));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        PlaySoundFunction.this.sound = message;
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.sound-set",
                                LangUtils.placeholder("sound", PlaySoundFunction.this.sound));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.KNOWLEDGE_BOOK);
                        this.button.setDisplayName("&d&lSet Category");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.play-sound.ask-category");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.current-category",
                                LangUtils.placeholder("category", PlaySoundFunction.this.soundCategory.toString()));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        try {
                            SoundCategory category = SoundCategory.valueOf(message.toUpperCase());
                            PlaySoundFunction.this.soundCategory = category.toString();
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.play-sound.category-set",
                                    LangUtils.placeholder(
                                            "category", PlaySoundFunction.this.soundCategory.toString()));
                        } catch (IllegalArgumentException exception) {
                            LangUtils.sendMessage(player, "editor.function.play-sound.invalid-category");
                            LangUtils.sendMessage(
                                    player,
                                    "editor.function.play-sound.valid-categories",
                                    LangUtils.placeholder("categories", Arrays.toString(SoundCategory.values())));
                        }
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.NOTE_BLOCK);
                        this.button.setDisplayName("&d&lSet Pitch");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.play-sound.ask-pitch");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.current-pitch",
                                LangUtils.placeholder("pitch", String.valueOf(PlaySoundFunction.this.pitch)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        PlaySoundFunction.this.pitch = (float) value.orElse(1.0).doubleValue();
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.pitch-set",
                                LangUtils.placeholder("pitch", String.valueOf(PlaySoundFunction.this.pitch)));
                    }
                });
        this.menu.addMenuItem(
                                new ChatMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.REPEATER);
                        this.button.setDisplayName("&d&lSet Volume");
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        LangUtils.sendMessage(player, "editor.function.play-sound.ask-volume");
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.current-volume",
                                LangUtils.placeholder("volume", String.valueOf(PlaySoundFunction.this.volume)));
                    }

                    /**
                     * Performs on input.
                     */
                    @Override
                    public void onInput(Player player, String message) {
                        Optional<Double> value = InputUtils.readDoubleInput(player, message);
                        PlaySoundFunction.this.volume = value.orElse(1.0);
                        LangUtils.sendMessage(
                                player,
                                "editor.function.play-sound.volume-set",
                                LangUtils.placeholder("volume", String.valueOf(PlaySoundFunction.this.volume)));
                    }
                });
        this.menu.addMenuItem(
                                new ToggleMenuItem() {
                    /**
                     * Builds button.
                     */
                    @Override
                    public void buildButton() {
                        this.button = new MenuButton(Material.COMPASS);
                        this.button.setDisplayName("&d&lToggle Sound Location");
                        this.button.setEnchanted(PlaySoundFunction.this.playAtLocation);
                    }

                    /**
                     * Performs on select.
                     */
                    @Override
                    public void onSelect(Player player) {
                        if (!PlaySoundFunction.this.playAtLocation) {
                            LangUtils.sendMessage(player, "editor.function.play-sound.location-function");
                        } else {
                            LangUtils.sendMessage(player, "editor.function.play-sound.location-player");
                        }

                        PlaySoundFunction.this.playAtLocation = !PlaySoundFunction.this.playAtLocation;
                    }
                });
    }

    /**
     * Sets the sound.
     */
    public void setSound(String sound) {
        this.sound = sound;
    }

    /**
     * Sets the sound category.
     */
    public void setSoundCategory(String soundCategory) {
        this.soundCategory = soundCategory;
    }

    /**
     * Sets the pitch.
     */
    public void setPitch(double pitch) {
        this.pitch = pitch;
    }

    /**
     * Sets the volume.
     */
    public void setVolume(double volume) {
        this.volume = volume;
    }

    /**
     * Sets the play at location.
     */
    public void setPlayAtLocation(boolean playAtLocation) {
        this.playAtLocation = playAtLocation;
    }
}
