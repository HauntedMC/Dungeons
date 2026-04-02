package nl.hauntedmc.dungeons.api.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;
import org.bukkit.Bukkit;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public class AsyncConfiguration extends Configuration {
   private final Plugin plugin;
   private final Map<File, Boolean> locks;
   private final Map<File, List<Runnable>> queues;

   public AsyncConfiguration(Plugin plugin) {
      this.plugin = plugin;
      this.locks = new HashMap<>();
      this.queues = new HashMap<>();
   }

   private void setLocked(File file, boolean locked) {
      this.locks.put(file, locked);
      if (!locked) {
         List<Runnable> queue = this.queues.get(file);
         if (queue == null) {
            return;
         }

         if (queue.isEmpty()) {
            return;
         }

         queue.getFirst().run();
         queue.removeFirst();
      }
   }

   public void save(@NotNull File file) {
      if (this.locks.getOrDefault(file, false)) {
         List<Runnable> queue = this.queues.computeIfAbsent(file, k -> new ArrayList<>());
         queue.add(() -> this.save(file));
      } else {
         this.setLocked(file, true);
         Runnable save = () -> {
            try {
               super.save(file);
               this.setLocked(file, false);
            } catch (IOException var3x) {
               Dungeons.inst().getLogger().info(HelperUtils.colorize("&cFailed to asynchronously save config '" + file.getName() + "'!"));
               Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var3x.getMessage());
               this.setLocked(file, false);
            }
         };
         if (!this.plugin.isEnabled()) {
            save.run();
         } else {
            Bukkit.getScheduler().runTaskAsynchronously(this.plugin, save);
         }
      }
   }

   public void load(@NotNull File file) {
      if (this.locks.getOrDefault(file, false)) {
         List<Runnable> queue = this.queues.computeIfAbsent(file, k -> new ArrayList<>());
         queue.add(() -> this.load(file));
      } else {
         this.setLocked(file, true);

         try {
            super.load(file);
            this.setLocked(file, false);
         } catch (InvalidConfigurationException | IOException var3) {
            Dungeons.inst().getLogger().info(HelperUtils.colorize("&cFailed to load config '" + file.getName() + "'!"));
            Dungeons.inst().getLogger().severe(Dungeons.logPrefix + var3.getMessage());
            this.setLocked(file, false);
         }
      }
   }
}
