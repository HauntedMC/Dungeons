package net.playavalon.mythicdungeons.compatibility.citizens;

import java.util.Iterator;
import java.util.UUID;
import net.citizensnpcs.Settings.Setting;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.event.DespawnReason;
import net.citizensnpcs.api.event.NPCCloneEvent;
import net.citizensnpcs.api.event.NPCCreateEvent;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.api.npc.NPCRegistry;
import net.citizensnpcs.api.npc.NPC.Metadata;
import net.citizensnpcs.api.persistence.PersistenceLoader;
import net.citizensnpcs.api.trait.Trait;
import net.citizensnpcs.api.trait.trait.MobType;
import net.citizensnpcs.api.util.DataKey;
import net.citizensnpcs.api.util.MemoryDataKey;
import net.citizensnpcs.npc.CitizensNPC;
import net.citizensnpcs.npc.EntityControllers;
import net.citizensnpcs.trait.ArmorStandTrait;
import net.citizensnpcs.trait.LookClose;
import net.citizensnpcs.trait.MountTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class MythicNPCRegistry implements NPCRegistry {
   public NPC createNPC(EntityType type, String name) {
      return this.createNPC(type, UUID.randomUUID(), 0, name);
   }

   public NPC createNPC(EntityType type, String name, Location location) {
      return null;
   }

   public NPC createNPC(EntityType type, UUID uuid, int id, String name) {
      NPC npc = new CitizensNPC(uuid, id, name, EntityControllers.createForType(type), this);
      Bukkit.getPluginManager().callEvent(new NPCCreateEvent(npc));
      if (type == EntityType.ARMOR_STAND && !npc.hasTrait(ArmorStandTrait.class)) {
         npc.addTrait(ArmorStandTrait.class);
      }

      if (Setting.DEFAULT_LOOK_CLOSE.asBoolean()) {
         npc.addTrait(LookClose.class);
      }

      npc.addTrait(MountTrait.class);
      return npc;
   }

   public NPC createNPCUsingItem(EntityType entityType, String s, ItemStack itemStack) {
      return null;
   }

   public void deregister(NPC npc) {
      CitizensAPI.getNPCRegistry().deregister(npc);
   }

   public void deregisterAll() {
      CitizensAPI.getNPCRegistry().deregisterAll();
   }

   public void despawnNPCs(DespawnReason despawnReason) {
      CitizensAPI.getNPCRegistry().despawnNPCs(DespawnReason.REMOVAL);
   }

   public NPC getById(int id) {
      return CitizensAPI.getNPCRegistry().getById(id);
   }

   public NPC getByUniqueId(UUID uuid) {
      return CitizensAPI.getNPCRegistry().getByUniqueId(uuid);
   }

   public NPC getByUniqueIdGlobal(UUID uuid) {
      return CitizensAPI.getNPCRegistry().getByUniqueIdGlobal(uuid);
   }

   public String getName() {
      return "MDNPCRegistry";
   }

   public NPC getNPC(Entity entity) {
      return CitizensAPI.getNPCRegistry().getNPC(entity);
   }

   public boolean isNPC(Entity entity) {
      return CitizensAPI.getNPCRegistry().isNPC(entity);
   }

   public void saveToStore() {
      CitizensAPI.getNPCRegistry().saveToStore();
   }

   public Iterable<NPC> sorted() {
      return CitizensAPI.getNPCRegistry().sorted();
   }

   @NotNull
   public Iterator<NPC> iterator() {
      return CitizensAPI.getNPCRegistry().iterator();
   }

   public NPC cloneNPC(int id) {
      NPC original = CitizensAPI.getNPCRegistry().getById(id);
      NPC clone = this.createNPC(((MobType)original.getOrAddTrait(MobType.class)).getType(), UUID.randomUUID(), id, original.getFullName());
      DataKey key = new MemoryDataKey();
      this.save(original, key);
      clone.load(key);

      for (Trait trait : clone.getTraits()) {
         trait.onCopy();
      }

      Bukkit.getPluginManager().callEvent(new NPCCloneEvent(original, clone));
      return clone;
   }

   public void save(NPC npc, DataKey key) {
      if ((Boolean)npc.data().get(Metadata.SHOULD_SAVE, true)) {
         npc.data().saveTo(key.getRelative("metadata"));
         key.setString("name", npc.getFullName());
         key.setString("uuid", npc.getUniqueId().toString());
         StringBuilder traitNames = new StringBuilder();

         for (Trait trait : npc.getTraits()) {
            DataKey traitKey = key.getRelative("traits." + trait.getName());
            trait.save(traitKey);
            PersistenceLoader.save(trait, traitKey);
            traitNames.append(trait.getName() + ",");
         }

         if (traitNames.length() > 0) {
            key.setString("traitnames", traitNames.substring(0, traitNames.length() - 1));
         } else {
            key.setString("traitnames", "");
         }
      }
   }
}
