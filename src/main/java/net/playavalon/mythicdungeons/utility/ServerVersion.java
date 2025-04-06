package net.playavalon.mythicdungeons.utility;

import org.bukkit.Bukkit;

public enum ServerVersion {
   v1_16,
   v1_16_1,
   v1_16_2,
   v1_16_3,
   v1_16_4,
   v1_16_5,
   v1_17,
   v1_17_1,
   v1_18,
   v1_18_1,
   v1_18_2,
   v1_19,
   v1_19_1,
   v1_19_2,
   v1_19_3,
   v1_19_4,
   v1_20,
   v1_20_1,
   v1_20_2,
   v1_20_3,
   v1_20_4,
   v1_20_5,
   v1_20_6,
   v1_21,
   v1_21_1,
   v1_21_3,
   v1_21_4;

   private static final ServerVersion VERSION;
   private static final ServerVersion.Platform PLATFORM;
   private final int major;
   private final int minor;
   private final int revision;

   public static ServerVersion get() {
      return VERSION;
   }

   private ServerVersion() {
      String[] split = this.name().substring(1).split("_");
      this.major = Integer.parseInt(split[0]);
      this.minor = Integer.parseInt(split[1]);
      if (split.length > 2) {
         this.revision = Integer.parseInt(split[2]);
      } else {
         this.revision = 0;
      }
   }

   public static boolean isPaper() {
      return PLATFORM == ServerVersion.Platform.PAPER;
   }

   public boolean isBefore(ServerVersion other) {
      return this.major < other.major || this.minor < other.minor || this.revision < other.revision;
   }

   public boolean isAfter(ServerVersion other) {
      return this.major > other.major || this.minor > other.minor || this.revision > other.revision;
   }

   public boolean isAfterOrEqual(ServerVersion other) {
      if (this.major == other.major) {
         return this.minor == other.minor ? this.revision >= other.revision : this.minor > other.minor;
      } else {
         return this.major > other.major;
      }
   }

   @Override
   public String toString() {
      return String.format("%d.%d.%d", this.major, this.minor, this.revision);
   }

   public static ServerVersion oldest() {
      return values()[0];
   }

   public static ServerVersion newest() {
      return values()[values().length - 1];
   }

   static {
      ServerVersion.Platform platform = ServerVersion.Platform.OTHER;

      try {
         Class.forName("com.destroystokyo.paper.PaperConfig");
         Class.forName("io.papermc.paper.configuration.Configuration");
         platform = ServerVersion.Platform.PAPER;
      } catch (ClassNotFoundException var3) {
      }

      try {
         VERSION = valueOf("v" + Bukkit.getServer().getBukkitVersion().split("-")[0].replace('.', '_'));
      } catch (IllegalArgumentException var2) {
         throw new IllegalStateException("Unsupported server version: " + Bukkit.getServer().getBukkitVersion());
      }

      PLATFORM = platform;
   }

   public static enum Platform {
      PAPER,
      OTHER;
   }
}
