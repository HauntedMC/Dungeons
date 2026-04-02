package nl.hauntedmc.dungeons.dungeons.dungeontypes;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.exceptions.DungeonInitException;
import nl.hauntedmc.dungeons.api.parents.DungeonDifficulty;
import nl.hauntedmc.dungeons.api.parents.dungeons.AbstractDungeon;
import nl.hauntedmc.dungeons.api.parents.elements.DungeonFunction;
import nl.hauntedmc.dungeons.api.parents.instances.InstanceEditable;
import nl.hauntedmc.dungeons.api.parents.instances.InstancePlayable;
import nl.hauntedmc.dungeons.api.party.IDungeonParty;
import nl.hauntedmc.dungeons.dungeons.instancetypes.play.InstanceClassic;
import nl.hauntedmc.dungeons.player.DungeonPlayer;
import nl.hauntedmc.dungeons.util.file.LangUtils;
import nl.hauntedmc.dungeons.util.tasks.ProcessTimer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

public class DungeonClassic extends AbstractDungeon {
   private final HashMap<Location, DungeonFunction> functions;
   private boolean saving;
   private boolean markedForDelete;
   private boolean functionsChanged = false;

   public DungeonClassic(@NotNull File folder, @Nullable YamlConfiguration loadedConfig) throws DungeonInitException {
      super(folder, loadedConfig);
      this.functions = new HashMap<>();
      this.loadFunctions();
   }

   @Override
   public InstancePlayable createPlaySession(CountDownLatch latch) {
      InstancePlayable instance = new InstanceClassic(this, latch);
      instance.init();
      return instance;
   }

   @Override
   public InstanceEditable createEditSession(CountDownLatch latch) {
      InstanceEditable instance = new InstanceEditable(this, latch);
      instance.init();
      return instance;
   }

   @Override
   public boolean prepInstance(Player player, String difficultyName) {
      DungeonPlayer aPlayer = Dungeons.inst().getDungeonPlayer(player);
      LangUtils.sendMessage(player, "instance.loading");
      DungeonDifficulty difficulty = this.difficultyLevels.get(difficultyName);
      Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> new ProcessTimer().run("Loading Dungeon " + this.getWorldName(), () -> {
         CountDownLatch latch = new CountDownLatch(1);
         InstancePlayable inst = this.createPlaySession(latch);
         inst.setDifficulty(difficulty);
         this.instances.add(inst);
         Dungeons.inst().getActiveInstances().add(inst);

         try {
            boolean loaded = latch.await(5L, TimeUnit.SECONDS);
            if (!loaded) {
               this.removeInstance(inst);
               LangUtils.sendMessage(player, "instance.timed-out");
               IDungeonParty party = aPlayer.getiDungeonParty();
               if (party != null) {
                  party.setAwaitingDungeon(false);
                  return;
               }

               aPlayer.setAwaitingDungeon(false);
               return;
            }

            Bukkit.getScheduler().runTask(Dungeons.inst(), () -> {
               inst.addPlayer(aPlayer);
               aPlayer.setAwaitingDungeon(false);
               if (Dungeons.inst().isPartiesEnabled() && aPlayer.getiDungeonParty() != null) {
                  for (Player partyPlayer : aPlayer.getiDungeonParty().getPlayers()) {
                     DungeonPlayer enteringPlayer = Dungeons.inst().getDungeonPlayer(partyPlayer);
                     if (enteringPlayer != aPlayer) {
                        inst.addPlayer(enteringPlayer);
                        enteringPlayer.setAwaitingDungeon(false);
                     }
                  }
               }

               LangUtils.sendMessage(player, "instance.loaded");
            });
         } catch (InterruptedException var8) {
            LangUtils.sendMessage(player, "instance.failed");
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var8.getMessage());
         }
      }));
      return true;
   }

   @Override
   public void addFunction(Location loc, DungeonFunction function) {
      loc.setWorld(null);
      loc = loc.clone();
      function.setLocation(loc);
      this.functions.put(loc, function);
   }

   @Override
   public void removeFunction(Location loc) {
      loc.setWorld(null);
      this.functions.remove(loc);
   }

   @Override
   public Map<Location, DungeonFunction> getFunctions() {
      return this.functions;
   }

   @Override
   public void saveFunctions() {
      YamlConfiguration functionsYaml = new YamlConfiguration();
      File saveFile = new File(this.folder, "functions.yml");
      functionsYaml.set("Version", 1);
      List<DungeonFunction> functions = new ArrayList<>(this.functions.values());
      functionsYaml.set("Functions", functions);
      if (Dungeons.inst().isEnabled()) {
         Bukkit.getScheduler().runTaskAsynchronously(Dungeons.inst(), () -> {
            try {
               functionsYaml.save(saveFile);
            } catch (IOException var3x) {
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var3x.getMessage());

            }
         });
      } else {
         try {
            functionsYaml.save(saveFile);
         } catch (IOException var5) {
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var5.getMessage());
         }
      }
   }

   public void loadFunctions() throws DungeonInitException {
      YamlConfiguration functionsYaml = new YamlConfiguration();
      File loadFile = new File(this.folder, "functions.yml");
      if (loadFile.exists()) {
         try {
            functionsYaml.load(loadFile);
            List<DungeonFunction> functions = (List<DungeonFunction>) functionsYaml.getList("Functions");
            if (functions != null) {
               for (DungeonFunction function : functions) {
                  function.init();
                  this.addFunction(function.getLocation(), function);
               }
            }
         } catch (IOException var6) {
            throw new DungeonInitException(
               "Access of functions.yml file failed!", false, "There may be another process accessing the file, or we may not have permission."
            );
         } catch (InvalidConfigurationException | YAMLException var7) {
             throw getDungeonInitException(var7);
         }
      }
   }

   private static @NotNull DungeonInitException getDungeonInitException(Exception var7) {
      DungeonInitException ex = new DungeonInitException(
         "Functions list has invalid YAML! See error below...",
         false,
         "Contains an unsupported element! (Function, trigger, or condition!)",
         "You may need to change or delete this function!"
      );
      if (var7.getCause() != null) {
         ex.addMessage("Error: " + var7.getCause().getMessage());
         if (!var7.getCause().getMessage().contains("FunctionLootTableRewards")) {
            ex.addMessage("This usually happens if the element belonged to another plugin that is no longer present!");
         }
      } else {
         ex.addMessage("&c├─ Error: " + var7.getMessage());
         if (!var7.getMessage().contains("FunctionLootTableRewards")) {
            ex.addMessage("&c└─ This usually happens if the element belonged to another plugin that is no longer present!");
         }
      }
      return ex;
   }

   @Override
   public boolean isSaving() {
      return this.saving;
   }

   @Override
   public void setSaving(boolean saving) {
      this.saving = saving;
   }

   @Override
   public boolean isMarkedForDelete() {
      return this.markedForDelete;
   }

   @Override
   public void setMarkedForDelete(boolean markedForDelete) {
      this.markedForDelete = markedForDelete;
   }

   public boolean isFunctionsChanged() {
      return this.functionsChanged;
   }

   public void setFunctionsChanged(boolean functionsChanged) {
      this.functionsChanged = functionsChanged;
   }
}
