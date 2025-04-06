package net.playavalon.mythicdungeons.api.config;

import java.lang.reflect.*;
import java.util.*;
import javax.annotation.Nullable;
import net.playavalon.mythicdungeons.MythicDungeons;
import net.playavalon.mythicdungeons.api.annotations.SavedField;
import net.playavalon.mythicdungeons.objenesis.Objenesis;
import net.playavalon.mythicdungeons.objenesis.ObjenesisStd;
import net.playavalon.mythicdungeons.objenesis.instantiator.ObjectInstantiator;
import net.playavalon.mythicdungeons.utility.helpers.ReflectionUtils;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemorySection;
import org.bukkit.plugin.Plugin;
import org.reflections.Reflections;

public interface AvalonSerializable {
   List<Class<? extends AvalonSerializable>> pluginSerializables = new ArrayList<>();
   Objenesis objenesis = new ObjenesisStd();

   static void register(Plugin plugin) {
      register(plugin.getClass().getPackage().getName());
   }

   static void register(String packagePath) {
      Reflections refl = new Reflections(packagePath);
      Set<Class<? extends AvalonSerializable>> subtypes = refl.getSubTypesOf(AvalonSerializable.class);
      pluginSerializables.addAll(subtypes);
   }

   default void initFields(Map<String, Object> values) {
      try {
         List<Field> fields = new ArrayList<>();
         ReflectionUtils.getAnnotatedFields(fields, this.getClass(), SavedField.class);
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

                  boolean keyMythic = AvalonSerializable.class.isAssignableFrom(keyClass);
                  boolean valueMythic = AvalonSerializable.class.isAssignableFrom(valueClass);
                  Map<Object, Object> newMap = new HashMap<>();

                  for (Map.Entry<?, ?> pair : rawMap.entrySet()) {
                     Object newKey = keyMythic && pair.getKey() instanceof Map<?, ?> map
                             ? deserialize(keyClass, castMap(map))
                             : pair.getKey();
                     Object newValue = valueMythic && pair.getValue() instanceof Map<?, ?> map
                             ? deserialize(valueClass, castMap(map))
                             : pair.getValue();
                     newMap.put(newKey, newValue);
                  }
                  configValue = newMap;
               } catch (ClassNotFoundException e) {
                  MythicDungeons.inst().getLogger().warning("Failed to load field '" + field.getName() + "' on class " + this.getClass().getSimpleName());
                  e.printStackTrace();
                  continue;
               }
            } else if (AvalonSerializable.class.isAssignableFrom(field.getType()) && configValue instanceof Map<?, ?> map) {
               configValue = deserialize((Class<?>) field.getType(), castMap(map));
            } else if (List.class.isAssignableFrom(field.getType()) && configValue instanceof List<?> baseList) {
               ParameterizedType listType = (ParameterizedType) field.getGenericType();
               try {
                  Class<?> itemClass = Class.forName(listType.getActualTypeArguments()[0].getTypeName());
                  if (AvalonSerializable.class.isAssignableFrom(itemClass)) {
                     List<Object> newList = new ArrayList<>();
                     for (Object entry : baseList) {
                        if (entry instanceof Map<?, ?> map) {
                           newList.add(deserialize(itemClass, castMap(map)));
                        }
                     }
                     configValue = newList;
                  }
               } catch (ClassNotFoundException e) {
                  MythicDungeons.inst().getLogger().warning("Failed to load field '" + field.getName() + "' on class " + this.getClass().getSimpleName());
                  e.printStackTrace();
                  continue;
               }
            }

            if (configValue != null) {
               field.set(this, configValue);
            }
         }
      } catch (IllegalAccessException e) {
         e.printStackTrace();
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
         ReflectionUtils.getAnnotatedFields(fields, this.getClass(), SavedField.class);
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
                  if (value instanceof AvalonSerializable serializable) {
                     value = serializable.serialize();
                  }
                  map.put(pair.getKey(), value);
               }
               values.put(configVar, map);
            } else if (fieldValue instanceof Collection<?> collection) {
               List<Object> list = new ArrayList<>();
               for (Object item : collection) {
                  if (item instanceof AvalonSerializable serializable) {
                     list.add(serializable.serialize());
                  }
               }
               values.put(configVar, list);
            } else if (fieldValue instanceof AvalonSerializable serializable) {
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
         e.printStackTrace();
      }
      return values;
   }

   @SuppressWarnings("unchecked")
   static <T extends AvalonSerializable> T deserialize(Class<?> serializable, @Nullable Map<String, Object> config) {
      if (config == null) return null;

      Map<String, Class<? extends T>> subclasses = new HashMap<>();
      for (Class<? extends AvalonSerializable> clazz : pluginSerializables) {
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

   @SuppressWarnings("unchecked")
   static <T extends AvalonSerializable> T deserialize(Class<T> serializable, @Nullable ConfigurationSection config) {
      if (config == null) {
         MythicDungeons.inst().getLogger().warning("Attempted to deserialize the object '" + serializable.getName() + "' but the ConfigurationSection is null!");
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
