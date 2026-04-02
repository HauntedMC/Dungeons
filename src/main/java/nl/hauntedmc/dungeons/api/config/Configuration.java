package nl.hauntedmc.dungeons.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

public class Configuration extends YamlConfiguration {

   @Override
   public void set(@NotNull String path, Object obj) {
       switch (obj) {
           case SerializableFile serializable -> super.set(path, serializable.serialize());
           case Collection<?> col -> {
               List<Object> contents = new ArrayList<>();
               for (Object entry : col) {
                   if (entry instanceof SerializableFile serializableEntry) {
                       contents.add(serializableEntry.serialize());
                   } else {
                       contents.add(entry);
                   }
               }
               super.set(path, contents);
           }
           case Map<?, ?> rawMap -> {
               Map<Object, Object> contents = new HashMap<>();
               for (Entry<?, ?> pair : rawMap.entrySet()) {
                   Object key = pair.getKey();
                   Object value = pair.getValue();
                   if (key instanceof SerializableFile serializableKey) {
                       key = serializableKey.serialize();
                   }
                   if (value instanceof SerializableFile serializableValue) {
                       value = serializableValue.serialize();
                   }
                   contents.put(key, value);
               }
               super.set(path, contents);
           }
           case null, default -> super.set(path, obj);
       }
   }

   public <T extends SerializableFile> T get(Class<T> type, @NotNull String path) {
      return SerializableFile.deserialize(type, this.getConfigurationSection(path));
   }

   public <T extends SerializableFile> List<T> getListOf(Class<T> type, @NotNull String path) {
      List<T> list = new ArrayList<>();
      List<?> origin = this.getList(path);
      if (origin == null) return list;

      Map<String, Class<? extends T>> subclasses = new HashMap<>();

      List<Class<? extends SerializableFile>> all = SerializableFile.pluginSerializables;

      for (Class<? extends SerializableFile> clazz : all) {
         if (type.isAssignableFrom(clazz)) {
            @SuppressWarnings("unchecked")
            Class<? extends T> typedClazz = (Class<? extends T>) clazz;
            subclasses.put(clazz.getSimpleName(), typedClazz);
         }
      }

      for (Object entry : origin) {
         if (entry instanceof Map<?, ?> rawMap) {
            Map<String, Object> map = new HashMap<>();
            for (Map.Entry<?, ?> e : rawMap.entrySet()) {
               if (e.getKey() instanceof String key) {
                  map.put(key, e.getValue());
               }
            }

            String entryTypeName = (String) map.getOrDefault("TYPE--", "");
            if (entryTypeName == null || entryTypeName.isEmpty()) {
               entryTypeName = type.getSimpleName();
            }

            Class<? extends T> subclass = subclasses.get(entryTypeName);
            if (subclass != null) {
               T obj = SerializableFile.deserialize(subclass, map);
               list.add(obj);
            }
         }
      }

      return list;
   }
}
