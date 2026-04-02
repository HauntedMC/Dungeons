package nl.hauntedmc.dungeons.util.tasks;

import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.util.HelperUtils;

public class ProcessTimer {
   public void run(String taskName, TrackedTask task) {
      long starttime = System.currentTimeMillis();
      task.run();
      long endtime = System.currentTimeMillis();
      long processTime = endtime - starttime;
      Dungeons.inst().getLogger().info(HelperUtils.fullColor("&d-= " + taskName + " processed in " + processTime + "ms. =-"));
   }
}
