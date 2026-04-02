package nl.hauntedmc.dungeons.api.config;

import java.lang.reflect.*;
import java.util.*;
import javax.annotation.Nullable;
import nl.hauntedmc.dungeons.Dungeons;
import nl.hauntedmc.dungeons.api.annotations.SavedField;
import nl.hauntedmc.dungeons.util.reflection.ClassReflectionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.plugin.Plugin;
import org.objenesis.Objenesis;
import org.objenesis.ObjenesisStd;
import org.objenesis.instantiator.ObjectInstantiator;
import org.reflections.Reflections;

public interface SerializableFile {
   List<Class<? extends SerializableFile>> pluginSerializables = new ArrayList<>();
   Objenesis objenesis = new ObjenesisStd();

   static void register(Plugin plugin) {
      register(plugin.getClass().getPackage().getName());
   }

   static void register(String packagePath) {
      Reflections refl = new Reflections(packagePath);
      Set<Class<? extends SerializableFile>> subtypes = refl.getSubTypesOf(SerializableFile.class);
      pluginSerializables.addAll(subtypes);
   }

   default void initFields(Map<String, Object> values) {
      try {
         List<Field> fields = new ArrayList<>();
         ClassReflectionUtils.collectAnnotatedFields(fields, this.getClass(), SavedField.class);
         for (Field field : fields) {
            field.setAccessible(true);
            SavedField saveData = field.getAnnotation(SavedField.class);
            String configVar = field.getName();
            Object configValue = values.get(configVar);
            if (configValue == null) {
               for (String name : saveData.legacyNames()) {
                  configValue = values.get(name);
                  if (configValue != null) break;
               }
            }

            if (field.getType().isEnum() && configValue instanceof String str) {
               try {
                  configValue = Enum.valueOf((Class<Enum>) field.getType(), str);
               } catch (IllegalArgumentException ignored) {}
            } else if (Map.class.isAssignableFrom(field.getType()) && configValue instanceof Map<?, ?> rawMap) {
               ParameterizedType mapType = (ParameterizedType) field.getGenericType();
               Type keyType = mapType.getActualTypeArguments()[0];
               Type valueType = mapType.getActualTypeArguments()[1];
               try {
                  Class<?> keyClass = Class.forName(keyType.getTypeName());
                  Class<?> valueClass = Class.forName(valueType.getTypeName());

                  boolean keyDungeon = SerializableFile.class.isAssignableFrom(keyClass);
                  boolean valDungeon = SerializableFile.class.isAssignableFrom(valueClass);
                  Map<Object, Object> newMap = new HashMap<>();

                  for (Map.Entry<?, ?> pair : rawMap.entrySet()) {
                     Object newKey = keyDungeon && pair.getKey() instanceof Map<?, ?> map
                             ? deserialize(keyClass, castMap(map))
                             : pair.getKey();
                     Object newValue = valDungeon && pair.getValue() instanceof Map<?, ?> map
                             ? deserialize(valueClass, castMap(map))
                             : pair.getValue();
                     newMap.put(newKey, newValue);
                  }
                  configValue = newMap;
               } catch (ClassNotFoundException e) {
                  Dungeons.inst().getLogger().warning("Failed to load field '" + field.getName() + "' on class " + this.getClass().getSimpleName());
                  Dungeons.inst().getLogger().severe(Dungeons.logPrefix + e.getMessage());
                  continue;
               }
            } else if (SerializableFile.class.isAssignableFrom(field.getType()) && configValue instanceof Map<?, ?> map) {
               configValue = deserialize(field.getType(), castMap(map));
            } else if (List.class.isAssignableFrom(field.getType()) && configValue instanceof List<?> baseList) {
               ParameterizedType listType = (ParameterizedType) field.getGenericType();
               try {
                  Class<?> itemClass = Class.forName(listType.getActualTypeArguments()[0].getTypeName());
                  if (SerializableFile.class.isAssignableFrom(itemClass)) {
                     List<Object> newList = new ArrayList<>();
                     for (Object entry : baseList) {
                        if (entry instanceof Map<?, ?> map) {
                           newList.add(deserialize(itemClass, castMap(map)));
                        }
                     }
                     configValue = newList;
                  }
               } catch (ClassNotFoundException e) {
                  Dungeons.inst().getLogger().warning("Failed to load field '" + field.getName() + "' on class " + this.getClass().getSimpleName());
                  Dungeons.inst().getLogger().severe(Dungeons.logPrefix + e.getMessage());
                  continue;
               }
            }

            if (configValue != null) {
               field.set(this, configValue);
            }
         }
      } catch (IllegalAccessException e) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + e.getMessage());
      }
   }

   static Map<String, Object> castMap(Map<?, ?> input) {
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<?, ?> entry : input.entrySet()) {
         if (entry.getKey() instanceof String key) {
            result.put(key, entry.getValue());
         }
      }
      return result;
   }

   default Map<String, Object> serialize() {
      Map<String, Object> values = new HashMap<>();
      try {
         List<Field> fields = new ArrayList<>();
         ClassReflectionUtils.collectAnnotatedFields(fields, this.getClass(), SavedField.class);
         for (Field field : fields) {
            field.setAccessible(true);
            String configVar = field.getName();
            Object fieldValue = field.get(this);
            if (field.getType().isEnum()) {
               values.put(configVar, ((Enum<?>) fieldValue).name());
            } else if (fieldValue instanceof Map<?, ?> baseMap) {
               Map<Object, Object> map = new HashMap<>();
               for (Map.Entry<?, ?> pair : baseMap.entrySet()) {
                  Object value = pair.getValue();
                  if (value instanceof SerializableFile serializable) {
                     value = serializable.serialize();
                  }
                  map.put(pair.getKey(), value);
               }
               values.put(configVar, map);
            } else if (fieldValue instanceof Collection<?> collection) {
               List<Object> list = new ArrayList<>();
               for (Object item : collection) {
                  if (item instanceof SerializableFile serializable) {
                     list.add(serializable.serialize());
                  }
               }
               values.put(configVar, list);
            } else if (fieldValue instanceof SerializableFile serializable) {
               Map<String, Object> serialized = serializable.serialize();
               if (!serializable.getClass().equals(field.getType())) {
                  serialized.put("TYPE--", serializable.getClass().getSimpleName());
               }
               values.put(configVar, serialized);
            } else {
               values.put(configVar, fieldValue);
            }
         }
      } catch (IllegalAccessException e) {
         Dungeons.inst().getLogger().severe(Dungeons.logPrefix + e.getMessage());
      }
      return values;
   }

   @SuppressWarnings("unchecked")
   static <T extends SerializableFile> T deserialize(Class<?> serializable, @Nullable Map<String, Object> config) {
      if (config == null) return null;

      Map<String, Class<? extends T>> subclasses = new HashMap<>();
      for (Class<? extends SerializableFile> clazz : pluginSerializables) {
         if (serializable.isAssignableFrom(clazz)) {
            subclasses.put(clazz.getSimpleName(), (Class<? extends T>) clazz);
         }
      }

      String entryTypeName = (String) config.getOrDefault("TYPE--", "");
      if (!entryTypeName.isEmpty()) {
         Class<? extends T> sub = subclasses.get(entryTypeName);
         if (sub != null) {
            serializable = sub;
         }
      }

      T instance;
      try {
         Constructor<?> constructor = serializable.getDeclaredConstructor();
         constructor.setAccessible(true);
         instance = (T) constructor.newInstance();
      } catch (Exception e) {
         ObjectInstantiator<T> instantiator = objenesis.getInstantiatorOf((Class<T>) serializable);
         instance = instantiator.newInstance();
      }

      if (instance != null) {
         instance.initFields(config);
      }

      return instance;
   }

   static <T extends SerializableFile> T deserialize(Class<T> serializable, @Nullable ConfigurationSection config) {
      if (config == null) {
         Dungeons.inst().getLogger().warning("Attempted to deserialize the object '" + serializable.getName() + "' but the ConfigurationSection is null!");
         return null;
      }

      Map<String, Object> configMap = new HashMap<>();
      for (Map.Entry<String, Object> entry : config.getValues(false).entrySet()) {
         Object value = entry.getValue();
         if (value instanceof MemorySection section) {
            value = section.getValues(false);
         }
         configMap.put(entry.getKey(), value);
      }
      return deserialize(serializable, configMap);
   }
}
