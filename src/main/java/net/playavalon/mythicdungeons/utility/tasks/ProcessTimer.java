package net.playavalon.mythicdungeons.utility.tasks;

import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.utility.helpers.Util;

public class ProcessTimer {
   public void run(String taskName, TrackedTask task) {
      long starttime = System.currentTimeMillis();
      task.run();
      long endtime = System.currentTimeMillis();
      long processTime = endtime - starttime;
      MythicDungeons.inst().getLogger().info(Util.fullColor("&d-= " + taskName + " processed in " + processTime + "ms. =-"));
   }
}
