package net.playavalon.mythicdungeons.api.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

public class AvalonConfiguration extends YamlConfiguration {

   @Override
   public void set(@NotNull String path, Object obj) {
      if (obj instanceof AvalonSerializable serializable) {
         super.set(path, serializable.serialize());
      } else if (obj instanceof Collection<?> col) {
         List<Object> contents = new ArrayList<>();
         for (Object entry : col) {
            if (entry instanceof AvalonSerializable serializableEntry) {
               contents.add(serializableEntry.serialize());
            } else {
               contents.add(entry);
            }
         }
         super.set(path, contents);
      } else if (obj instanceof Map<?, ?> rawMap) {
         Map<Object, Object> contents = new HashMap<>();
         for (Entry<?, ?> pair : rawMap.entrySet()) {
            Object key = pair.getKey();
            Object value = pair.getValue();
            if (key instanceof AvalonSerializable serializableKey) {
               key = serializableKey.serialize();
            }
            if (value instanceof AvalonSerializable serializableValue) {
               value = serializableValue.serialize();
            }
            contents.put(key, value);
         }
         super.set(path, contents);
      } else {
         super.set(path, obj);
      }
   }

   public <T extends AvalonSerializable> T get(Class<T> type, @NotNull String path) {
      return AvalonSerializable.deserialize(type, this.getConfigurationSection(path));
   }

   public <T extends AvalonSerializable> List<T> getListOf(Class<T> type, @NotNull String path) {
      List<T> list = new ArrayList<>();
      List<?> origin = this.getList(path);
      if (origin == null) return list;

      Map<String, Class<? extends T>> subclasses = new HashMap<>();

      @SuppressWarnings("unchecked")
      List<Class<? extends AvalonSerializable>> all = (List<Class<? extends AvalonSerializable>>) AvalonSerializable.pluginSerializables;

      for (Class<? extends AvalonSerializable> clazz : all) {
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
               T obj = AvalonSerializable.deserialize(subclass, map);
               list.add(obj);
            }
         }
      }

      return list;
   }
}
