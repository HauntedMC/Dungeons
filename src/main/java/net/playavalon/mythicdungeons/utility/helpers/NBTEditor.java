package net.playavalon.mythicdungeons.utility.helpers;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class NBTEditor {
   private static final Set<NBTEditor.ReflectionTarget> reflectionTargets;
   private static final Map<NBTEditor.ClassId, Class<?>> classCache;
   private static final Map<NBTEditor.MethodId, Method> methodCache;
   private static final Map<NBTEditor.ClassId, Constructor<?>> constructorCache;
   private static final Map<Class<?>, Constructor<?>> NBTConstructors;
   private static final Map<Class<?>, Class<?>> NBTClasses;
   private static final Map<Class<?>, Field> NBTTagFieldCache;
   private static Field NBTListData;
   private static Field NBTCompoundMap;
   private static Field skullProfile;
   private static final String VERSION;
   private static final NBTEditor.MinecraftVersion LOCAL_VERSION;
   private static final String BUKKIT_VERSION;
   public static final NBTEditor.Type COMPOUND = NBTEditor.Type.COMPOUND;
   public static final NBTEditor.Type LIST = NBTEditor.Type.LIST;
   public static final NBTEditor.Type NEW_ELEMENT = NBTEditor.Type.NEW_ELEMENT;
   public static final NBTEditor.Type DELETE = NBTEditor.Type.DELETE;
   public static final NBTEditor.Type CUSTOM_DATA = NBTEditor.Type.CUSTOM_DATA;
   public static final NBTEditor.Type ITEMSTACK_COMPONENTS = NBTEditor.Type.ITEMSTACK_COMPONENTS;

   private static Constructor<?> getNBTTagConstructor(Class<?> primitiveType) {
      return NBTConstructors.get(getNBTTag(primitiveType));
   }

   private static Class<?> getNBTTag(Class<?> primitiveType) {
      return NBTClasses.containsKey(primitiveType) ? NBTClasses.get(primitiveType) : primitiveType;
   }

   private static Object getNBTVar(Object object) {
      if (object == null) {
         return null;
      } else {
         Class<?> clazz = object.getClass();

         try {
            if (NBTTagFieldCache.containsKey(clazz)) {
               return NBTTagFieldCache.get(clazz).get(object);
            }
         } catch (Exception var3) {
            var3.printStackTrace();
         }

         return null;
      }
   }

   private static Method getMethod(NBTEditor.MethodId name) {
      if (methodCache.containsKey(name)) {
         return methodCache.get(name);
      } else {
         for (NBTEditor.ReflectionTarget target : reflectionTargets) {
            if (target.getVersion().lessThanOrEqualTo(LOCAL_VERSION)) {
               try {
                  Method method = target.fetchMethod(name);
                  if (method != null) {
                     methodCache.put(name, method);
                     return method;
                  }
               } catch (NoSuchMethodException | SecurityException | ClassNotFoundException var4) {
                  var4.printStackTrace();
               }
            }
         }

         methodCache.put(name, null);
         return null;
      }
   }

   private static Constructor<?> getConstructor(NBTEditor.ClassId id) {
      if (constructorCache.containsKey(id)) {
         return constructorCache.get(id);
      } else {
         for (NBTEditor.ReflectionTarget target : reflectionTargets) {
            if (target.getVersion().lessThanOrEqualTo(LOCAL_VERSION)) {
               try {
                  Constructor<?> cons = target.fetchConstructor(id);
                  if (cons != null) {
                     constructorCache.put(id, cons);
                     return cons;
                  }
               } catch (NoSuchMethodException | SecurityException | ClassNotFoundException var4) {
                  var4.printStackTrace();
               }
            }
         }

         return null;
      }
   }

   private static Class<?> getNMSClass(NBTEditor.ClassId id) {
      if (classCache.containsKey(id)) {
         return classCache.get(id);
      } else {
         for (NBTEditor.ReflectionTarget target : reflectionTargets) {
            if (target.getVersion().lessThanOrEqualTo(LOCAL_VERSION)) {
               try {
                  Class<?> clazz = target.fetchClass(id);
                  if (clazz != null) {
                     classCache.put(id, clazz);
                     return clazz;
                  }
               } catch (ClassNotFoundException var4) {
                  var4.printStackTrace();
               }
            }
         }

         throw new IllegalArgumentException("No such class exists: " + id);
      }
   }

   private static String getMatch(String string, String regex) {
      Pattern pattern = Pattern.compile(regex);
      Matcher matcher = pattern.matcher(string);
      return matcher.find() ? matcher.group(1) : null;
   }

   private static Object createItemStack(Object compound) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException, InstantiationException {
      if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
         Method createStack = getMethod(NBTEditor.MethodId.createStack);
         return createStack.getParameterCount() == 2
            ? getMethod(NBTEditor.MethodId.createStack).invoke(null, registryAccess(), compound)
            : getMethod(NBTEditor.MethodId.createStack).invoke(null, compound);
      } else {
         return LOCAL_VERSION != NBTEditor.MinecraftVersion.v1_11 && LOCAL_VERSION != NBTEditor.MinecraftVersion.v1_12
            ? getMethod(NBTEditor.MethodId.createStack).invoke(null, compound)
            : getConstructor(NBTEditor.ClassId.ItemStack).newInstance(compound);
      }
   }

   public static String getVersion() {
      return VERSION;
   }

   public static NBTEditor.MinecraftVersion getMinecraftVersion() {
      return LOCAL_VERSION;
   }

   public static ItemStack getHead(String skinURL) {
      Material material = Material.getMaterial("SKULL_ITEM");
      if (material == null) {
         material = Material.getMaterial("PLAYER_HEAD");
      }

      ItemStack head = new ItemStack(material, 1, (short)3);
      if (skinURL != null && !skinURL.isEmpty()) {
         ItemMeta headMeta = head.getItemMeta();
         Object profile = null;

         try {
            profile = getConstructor(NBTEditor.ClassId.GameProfile).newInstance(UUID.fromString("069a79f4-44e9-4726-a5be-fca90e38aaf5"), "Notch");
            Object propertyMap = getMethod(NBTEditor.MethodId.getProperties).invoke(profile);
            Object textureProperty = getConstructor(NBTEditor.ClassId.Property)
               .newInstance("textures", new String(Base64.getEncoder().encode(String.format("{textures:{SKIN:{\"url\":\"%s\"}}}", skinURL).getBytes())));
            getMethod(NBTEditor.MethodId.putProperty).invoke(propertyMap, "textures", textureProperty);
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var11) {
            var11.printStackTrace();
         }

         Method setSkullResolvableProfile = getMethod(NBTEditor.MethodId.setCraftMetaSkullResolvableProfile);
         Method setSkullProfile = getMethod(NBTEditor.MethodId.setCraftMetaSkullProfile);
         if (setSkullResolvableProfile != null) {
            try {
               setSkullResolvableProfile.invoke(headMeta, getConstructor(NBTEditor.ClassId.ResolvableProfile).newInstance(profile));
            } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var10) {
               var10.printStackTrace();
            }
         } else if (setSkullProfile != null) {
            try {
               setSkullProfile.invoke(headMeta, profile);
            } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var9) {
               var9.printStackTrace();
            }
         } else {
            try {
               skullProfile.set(headMeta, profile);
            } catch (IllegalAccessException | IllegalArgumentException var8) {
               var8.printStackTrace();
            }
         }

         head.setItemMeta(headMeta);
         return head;
      } else {
         return head;
      }
   }

   public static String getTexture(ItemStack head) {
      ItemMeta meta = head.getItemMeta();
      if (!(meta instanceof SkullMeta)) {
         throw new IllegalArgumentException("Item is not a player skull!");
      } else {
         try {
            Object profile = skullProfile.get(meta);
            Class<?> resolvableProfile = getNMSClass(NBTEditor.ClassId.ResolvableProfile);
            if (resolvableProfile != null && resolvableProfile.isInstance(profile)) {
               Method getGameProfile = getMethod(NBTEditor.MethodId.getResolvableProfileGameProfile);
               profile = getGameProfile.invoke(profile);
            }

            if (profile == null) {
               return null;
            } else {
               for (Object prop : (Collection)getMethod(NBTEditor.MethodId.propertyValues).invoke(getMethod(NBTEditor.MethodId.getProperties).invoke(profile))) {
                  if ("textures".equals(getMethod(NBTEditor.MethodId.getPropertyName).invoke(prop))) {
                     String texture = new String(Base64.getDecoder().decode((String)getMethod(NBTEditor.MethodId.getPropertyValue).invoke(prop)));
                     return getMatch(texture, "\\{\"url\":\"(.*?)\"\\}");
                  }
               }

               return null;
            }
         } catch (IllegalAccessException | SecurityException | InvocationTargetException | IllegalArgumentException var8) {
            var8.printStackTrace();
            return null;
         }
      }
   }

   private static Object getItemTag(ItemStack item, Object... keys) {
      try {
         Object compound = getCompound(item);
         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)
            && !BUKKIT_VERSION.startsWith("1.20.4")
            && !BUKKIT_VERSION.startsWith("1.20.5")) {
            compound = getMethod(NBTEditor.MethodId.compoundGet).invoke(compound, "components");
         }

         return getTag(compound, keys);
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var3) {
         var3.printStackTrace();
         return null;
      }
   }

   private static Object getCompound(ItemStack item) {
      if (item == null) {
         return null;
      } else {
         try {
            Object stack = null;
            stack = getMethod(NBTEditor.MethodId.asNMSCopy).invoke(null, item);
            Object tag = null;
            Method saveOptional = null;
            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
               saveOptional = getMethod(NBTEditor.MethodId.saveOptional);
            }

            if (saveOptional != null) {
               tag = saveOptional.invoke(stack, registryAccess());
            } else if (getMethod(NBTEditor.MethodId.itemHasTag).invoke(stack).equals(true)) {
               tag = getMethod(NBTEditor.MethodId.getItemTag).invoke(stack);
            } else {
               tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            }

            return tag;
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var4) {
            var4.printStackTrace();
            return null;
         }
      }
   }

   private static NBTEditor.NBTCompound getItemNBTTag(ItemStack item, Object... keys) {
      if (item == null) {
         return null;
      } else {
         try {
            Object stack = null;
            stack = getMethod(NBTEditor.MethodId.asNMSCopy).invoke(null, item);
            Object tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            Method saveOptional = null;
            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
               saveOptional = getMethod(NBTEditor.MethodId.saveOptional);
            }

            if (saveOptional != null) {
               tag = saveOptional.invoke(stack, registryAccess());
            } else {
               tag = getMethod(NBTEditor.MethodId.itemSave).invoke(stack, tag);
            }

            return getNBTTag(tag, keys);
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var5) {
            var5.printStackTrace();
            return null;
         }
      }
   }

   private static ItemStack setItemTag(ItemStack item, Object value, Object... keys) {
      if (item == null) {
         return null;
      } else {
         try {
            Object stack = getMethod(NBTEditor.MethodId.asNMSCopy).invoke(null, item);
            Object tag = null;
            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)
               && !BUKKIT_VERSION.startsWith("1.20.4")
               && !BUKKIT_VERSION.startsWith("1.20.5")) {
               tag = getMethod(NBTEditor.MethodId.saveOptional).invoke(stack, registryAccess());
            } else if (getMethod(NBTEditor.MethodId.itemHasTag).invoke(stack).equals(true)) {
               tag = getMethod(NBTEditor.MethodId.getItemTag).invoke(stack);
            } else {
               tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            }

            if (keys.length == 0 && value instanceof NBTEditor.NBTCompound) {
               tag = ((NBTEditor.NBTCompound)value).tag;
            } else {
               if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)
                  && !BUKKIT_VERSION.startsWith("1.20.4")
                  && !BUKKIT_VERSION.startsWith("1.20.5")) {
                  List<Object> keyList = new ArrayList<>(Arrays.asList(keys));
                  keyList.add(0, "components");
                  keys = keyList.toArray();
               }

               setTag(tag, value, keys);
            }

            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4) && BUKKIT_VERSION.startsWith("1.20.6")) {
               return (ItemStack)getMethod(NBTEditor.MethodId.asBukkitCopy).invoke(null, createItemStack(tag));
            } else {
               getMethod(NBTEditor.MethodId.setItemTag).invoke(stack, tag);
               return (ItemStack)getMethod(NBTEditor.MethodId.asBukkitCopy).invoke(null, stack);
            }
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var6) {
            var6.printStackTrace();
            return null;
         }
      }
   }

   public static ItemStack getItemFromTag(NBTEditor.NBTCompound compound) {
      if (compound == null) {
         return null;
      } else {
         try {
            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)
               && !BUKKIT_VERSION.startsWith("1.20.4")
               && !BUKKIT_VERSION.startsWith("1.20.5")) {
               return (ItemStack)getMethod(NBTEditor.MethodId.asBukkitCopy).invoke(null, createItemStack(compound.tag));
            } else {
               Object tag = compound.tag;
               Object count = getTag(tag, "Count");
               Object id = getTag(tag, "id");
               if (count == null || id == null) {
                  return null;
               } else {
                  return count instanceof Byte && id instanceof String
                     ? (ItemStack)getMethod(NBTEditor.MethodId.asBukkitCopy).invoke(null, createItemStack(tag))
                     : null;
               }
            }
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var4) {
            var4.printStackTrace();
            return null;
         }
      }
   }

   private static Object getEntityTag(Entity entity, Object... keys) {
      try {
         return getTag(getCompound(entity), keys);
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var3) {
         var3.printStackTrace();
         return null;
      }
   }

   private static Object getCompound(Entity entity) {
      if (entity == null) {
         return entity;
      } else {
         try {
            Object NMSEntity = getMethod(NBTEditor.MethodId.getEntityHandle).invoke(entity);
            Object tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            getMethod(NBTEditor.MethodId.getEntityTag).invoke(NMSEntity, tag);
            return tag;
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var3) {
            var3.printStackTrace();
            return null;
         }
      }
   }

   private static NBTEditor.NBTCompound getEntityNBTTag(Entity entity, Object... keys) {
      if (entity == null) {
         return null;
      } else {
         try {
            Object NMSEntity = getMethod(NBTEditor.MethodId.getEntityHandle).invoke(entity);
            Object tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            getMethod(NBTEditor.MethodId.getEntityTag).invoke(NMSEntity, tag);
            return getNBTTag(tag, keys);
         } catch (InstantiationException | IllegalArgumentException | InvocationTargetException | IllegalAccessException var4) {
            var4.printStackTrace();
            return null;
         }
      }
   }

   private static void setEntityTag(Entity entity, Object value, Object... keys) {
      if (entity != null) {
         try {
            Object NMSEntity = getMethod(NBTEditor.MethodId.getEntityHandle).invoke(entity);
            Object tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            getMethod(NBTEditor.MethodId.getEntityTag).invoke(NMSEntity, tag);
            if (keys.length == 0 && value instanceof NBTEditor.NBTCompound) {
               tag = ((NBTEditor.NBTCompound)value).tag;
            } else {
               setTag(tag, value, keys);
            }

            getMethod(NBTEditor.MethodId.setEntityTag).invoke(NMSEntity, tag);
         } catch (IllegalArgumentException | InvocationTargetException | InstantiationException | IllegalAccessException var5) {
            var5.printStackTrace();
         }
      }
   }

   private static Object getBlockTag(Block block, Object... keys) {
      try {
         return getTag(getCompound(block), keys);
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var3) {
         var3.printStackTrace();
         return null;
      }
   }

   private static Object getCompound(Block block) {
      try {
         if (block != null && getNMSClass(NBTEditor.ClassId.CraftBlockState).isInstance(block.getState())) {
            Location location = block.getLocation();
            Object blockPosition = getConstructor(NBTEditor.ClassId.BlockPosition)
               .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object nmsWorld = getMethod(NBTEditor.MethodId.getWorldHandle).invoke(location.getWorld());
            Object tileEntity = getMethod(NBTEditor.MethodId.getTileEntity).invoke(nmsWorld, blockPosition);
            if (tileEntity == null) {
               throw new IllegalArgumentException(block + " is not a tile entity!");
            } else {
               Object tag;
               if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                  Method getTileTag = getMethod(NBTEditor.MethodId.getTileTag);
                  if (getTileTag.getParameterCount() == 1) {
                     tag = getTileTag.invoke(tileEntity, registryAccess());
                  } else {
                     tag = getTileTag.invoke(tileEntity);
                  }
               } else if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_18_R1)) {
                  tag = getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity);
               } else {
                  tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
                  getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity, tag);
               }

               return tag;
            }
         } else {
            return null;
         }
      } catch (InstantiationException | IllegalArgumentException | InvocationTargetException | IllegalAccessException var7) {
         var7.printStackTrace();
         return null;
      }
   }

   private static NBTEditor.NBTCompound getBlockNBTTag(Block block, Object... keys) {
      try {
         if (block != null && getNMSClass(NBTEditor.ClassId.CraftBlockState).isInstance(block.getState())) {
            Location location = block.getLocation();
            Object blockPosition = getConstructor(NBTEditor.ClassId.BlockPosition)
               .newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            Object nmsWorld = getMethod(NBTEditor.MethodId.getWorldHandle).invoke(location.getWorld());
            Object tileEntity = getMethod(NBTEditor.MethodId.getTileEntity).invoke(nmsWorld, blockPosition);
            if (tileEntity == null) {
               return null;
            } else {
               Object tag;
               if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                  Method getTileTag = getMethod(NBTEditor.MethodId.getTileTag);
                  if (getTileTag.getParameterCount() == 1) {
                     tag = getTileTag.invoke(tileEntity, registryAccess());
                  } else {
                     tag = getTileTag.invoke(tileEntity);
                  }
               } else if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_18_R1)) {
                  tag = getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity);
               } else {
                  tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
                  getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity, tag);
               }

               return getNBTTag(tag, keys);
            }
         } else {
            return null;
         }
      } catch (InstantiationException | IllegalArgumentException | InvocationTargetException | IllegalAccessException var8) {
         var8.printStackTrace();
         return null;
      }
   }

   private static void setBlockTag(Block block, Object value, Object... keys) {
      try {
         if (block == null || !getNMSClass(NBTEditor.ClassId.CraftBlockState).isInstance(block.getState())) {
            return;
         }

         Location location = block.getLocation();
         Object blockPosition = getConstructor(NBTEditor.ClassId.BlockPosition).newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
         Object nmsWorld = getMethod(NBTEditor.MethodId.getWorldHandle).invoke(location.getWorld());
         Object tileEntity = getMethod(NBTEditor.MethodId.getTileEntity).invoke(nmsWorld, blockPosition);
         if (tileEntity == null) {
            return;
         }

         Object tag;
         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
            Method getTileTag = getMethod(NBTEditor.MethodId.getTileTag);
            if (getTileTag.getParameterCount() == 1) {
               tag = getTileTag.invoke(tileEntity, registryAccess());
            } else {
               tag = getTileTag.invoke(tileEntity);
            }
         } else if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_18_R1)) {
            tag = getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity);
         } else {
            tag = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            getMethod(NBTEditor.MethodId.getTileTag).invoke(tileEntity, tag);
         }

         if (keys.length == 0 && value instanceof NBTEditor.NBTCompound) {
            tag = ((NBTEditor.NBTCompound)value).tag;
         } else {
            setTag(tag, value, keys);
         }

         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
            Method setTileTag = getMethod(NBTEditor.MethodId.setTileTag);
            if (setTileTag.getParameterCount() == 2) {
               setTileTag.invoke(tileEntity, tag, registryAccess());
            } else {
               setTileTag.invoke(tileEntity, tag);
            }
         } else if (LOCAL_VERSION == NBTEditor.MinecraftVersion.v1_16) {
            getMethod(NBTEditor.MethodId.setTileTag).invoke(tileEntity, getMethod(NBTEditor.MethodId.getTileType).invoke(nmsWorld, blockPosition), tag);
         } else {
            getMethod(NBTEditor.MethodId.setTileTag).invoke(tileEntity, tag);
         }
      } catch (InstantiationException | IllegalArgumentException | InvocationTargetException | IllegalAccessException var9) {
         var9.printStackTrace();
      }
   }

   public static void setSkullTexture(Block block, String texture) {
      try {
         Object profile = getConstructor(NBTEditor.ClassId.GameProfile).newInstance(UUID.randomUUID(), null);
         Object propertyMap = getMethod(NBTEditor.MethodId.getProperties).invoke(profile);
         Object textureProperty = getConstructor(NBTEditor.ClassId.Property)
            .newInstance("textures", new String(Base64.getEncoder().encode(String.format("{textures:{SKIN:{\"url\":\"%s\"}}}", texture).getBytes())));
         getMethod(NBTEditor.MethodId.putProperty).invoke(propertyMap, "textures", textureProperty);
         Location location = block.getLocation();
         Object blockPosition = getConstructor(NBTEditor.ClassId.BlockPosition).newInstance(location.getBlockX(), location.getBlockY(), location.getBlockZ());
         Object nmsWorld = getMethod(NBTEditor.MethodId.getWorldHandle).invoke(location.getWorld());
         Object tileEntity = getMethod(NBTEditor.MethodId.getTileEntity).invoke(nmsWorld, blockPosition);
         if (!getNMSClass(NBTEditor.ClassId.TileEntitySkull).isInstance(tileEntity)) {
            throw new IllegalArgumentException(block + " is not a skull!");
         }

         getMethod(NBTEditor.MethodId.setGameProfile).invoke(tileEntity, profile);
      } catch (InvocationTargetException | InstantiationException | IllegalAccessException var9) {
         var9.printStackTrace();
      }
   }

   private static Object getValue(Object object, Object... keys) {
      if (object instanceof ItemStack) {
         return getItemTag((ItemStack)object, keys);
      } else if (object instanceof Entity) {
         return getEntityTag((Entity)object, keys);
      } else if (object instanceof Block) {
         return getBlockTag((Block)object, keys);
      } else if (object instanceof NBTEditor.NBTCompound) {
         try {
            return getTag(((NBTEditor.NBTCompound)object).tag, keys);
         } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var3) {
            var3.printStackTrace();
            return null;
         }
      } else {
         throw new IllegalArgumentException("Object provided must be of type ItemStack, Entity, Block, or NBTCompound!");
      }
   }

   public static NBTEditor.NBTCompound getNBTCompound(Object object, Object... keys) {
      if (object == null) {
         throw new NullPointerException("Provided object was null!");
      } else if (object instanceof ItemStack) {
         return getItemNBTTag((ItemStack)object, keys);
      } else if (object instanceof Entity) {
         return getEntityNBTTag((Entity)object, keys);
      } else if (object instanceof Block) {
         return getBlockNBTTag((Block)object, keys);
      } else if (object instanceof NBTEditor.NBTCompound) {
         try {
            return getNBTTag(((NBTEditor.NBTCompound)object).tag, keys);
         } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var3) {
            var3.printStackTrace();
            return null;
         }
      } else if (getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(object)) {
         try {
            return getNBTTag(object, keys);
         } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var4) {
            var4.printStackTrace();
            return null;
         }
      } else {
         throw new IllegalArgumentException("Object provided must be of type ItemStack, Entity, Block, or NBTCompound!");
      }
   }

   public static String getString(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof String ? (String)result : null;
   }

   public static int getInt(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Integer ? (Integer)result : 0;
   }

   public static double getDouble(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Double ? (Double)result : 0.0;
   }

   public static long getLong(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Long ? (Long)result : 0L;
   }

   public static float getFloat(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Float ? (Float)result : 0.0F;
   }

   public static short getShort(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Short ? (Short)result : 0;
   }

   public static byte getByte(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof Byte ? (Byte)result : 0;
   }

   public static boolean getBoolean(Object object, Object... keys) {
      return getByte(object, keys) == 1;
   }

   public static byte[] getByteArray(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof byte[] ? (byte[])result : null;
   }

   public static int[] getIntArray(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result instanceof int[] ? (int[])result : null;
   }

   public static boolean contains(Object object, Object... keys) {
      Object result = getValue(object, keys);
      return result != null;
   }

   public static Collection<String> getKeys(Object object, Object... keys) {
      Object compound;
      if (object instanceof ItemStack) {
         compound = getCompound((ItemStack)object);
      } else if (object instanceof Entity) {
         compound = getCompound((Entity)object);
      } else if (object instanceof Block) {
         compound = getCompound((Block)object);
      } else {
         if (!(object instanceof NBTEditor.NBTCompound)) {
            throw new IllegalArgumentException("Object provided must be of type ItemStack, Entity, Block, or NBTCompound!");
         }

         compound = ((NBTEditor.NBTCompound)object).tag;
      }

      try {
         NBTEditor.NBTCompound nbtCompound = getNBTTag(compound, keys);
         Object tag = nbtCompound.tag;
         return getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(tag) ? (Collection)getMethod(NBTEditor.MethodId.compoundKeys).invoke(tag) : null;
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var5) {
         var5.printStackTrace();
         return null;
      }
   }

   public static int getSize(Object object, Object... keys) {
      Object compound;
      if (object instanceof ItemStack) {
         compound = getCompound((ItemStack)object);
      } else if (object instanceof Entity) {
         compound = getCompound((Entity)object);
      } else if (object instanceof Block) {
         compound = getCompound((Block)object);
      } else {
         if (!(object instanceof NBTEditor.NBTCompound)) {
            throw new IllegalArgumentException("Object provided must be of type ItemStack, Entity, Block, or NBTCompound!");
         }

         compound = ((NBTEditor.NBTCompound)object).tag;
      }

      try {
         NBTEditor.NBTCompound nbtCompound = getNBTTag(compound, keys);
         if (getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(nbtCompound.tag)) {
            return getKeys(nbtCompound).size();
         }

         if (getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(nbtCompound.tag)) {
            return (Integer)getMethod(NBTEditor.MethodId.listSize).invoke(nbtCompound.tag);
         }
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var4) {
         var4.printStackTrace();
         return 0;
      }

      throw new IllegalArgumentException("Value is not a compound or list!");
   }

   public static <T> T set(T object, Object value, Object... keys) {
      if (object instanceof ItemStack) {
         return (T)setItemTag((ItemStack)object, value, keys);
      } else {
         if (object instanceof Entity) {
            setEntityTag((Entity)object, value, keys);
         } else if (object instanceof Block) {
            setBlockTag((Block)object, value, keys);
         } else {
            if (!(object instanceof NBTEditor.NBTCompound)) {
               throw new IllegalArgumentException("Object provided must be of type ItemStack, Entity, Block, or NBTCompound!");
            }

            try {
               setTag(((NBTEditor.NBTCompound)object).tag, value, keys);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | InstantiationException var4) {
               var4.printStackTrace();
            }
         }

         return object;
      }
   }

   public static NBTEditor.NBTCompound getNBTCompound(String json) {
      return NBTEditor.NBTCompound.fromJson(json);
   }

   public static NBTEditor.NBTCompound getEmptyNBTCompound() {
      try {
         return new NBTEditor.NBTCompound(getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance());
      } catch (IllegalAccessException | InstantiationException var1) {
         var1.printStackTrace();
         return null;
      }
   }

   private static void setTag(Object tag, Object value, Object... keys) throws InstantiationException, IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      Object wrappedValue;
      if (value == null || value == NBTEditor.Type.DELETE) {
         wrappedValue = NBTEditor.Type.DELETE;
      } else if (value instanceof NBTEditor.NBTCompound) {
         wrappedValue = ((NBTEditor.NBTCompound)value).tag;
      } else if (getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(value) || getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(value)) {
         wrappedValue = value;
      } else if (value == NBTEditor.Type.COMPOUND) {
         wrappedValue = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
      } else if (value == NBTEditor.Type.LIST) {
         wrappedValue = getNMSClass(NBTEditor.ClassId.NBTTagList).newInstance();
      } else {
         if (value instanceof Boolean) {
            value = (byte)((Boolean)value ? 1 : 0);
         }

         Constructor<?> cons = getNBTTagConstructor(value.getClass());
         if (cons == null) {
            throw new IllegalArgumentException("Provided value type(" + value.getClass() + ") is not supported!");
         }

         wrappedValue = cons.newInstance(value);
      }

      Object compound = tag;

      for (int index = 0; index < keys.length - 1; index++) {
         Object key = keys[index];
         Object prevCompound = compound;
         if (key == NBTEditor.Type.CUSTOM_DATA) {
            if (!LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
               continue;
            }

            key = "minecraft:custom_data";
         } else if (key == NBTEditor.Type.ITEMSTACK_COMPONENTS) {
            if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
               key = "components";
            } else {
               key = "tag";
            }
         }

         if (key instanceof Integer) {
            int keyIndex = (Integer)key;
            List<?> tagList = (List<?>)NBTListData.get(compound);
            if (keyIndex >= 0 && keyIndex < tagList.size()) {
               compound = tagList.get(keyIndex);
            } else {
               compound = null;
            }
         } else if (key != null && key != NBTEditor.Type.NEW_ELEMENT) {
            compound = getMethod(NBTEditor.MethodId.compoundGet).invoke(compound, (String)key);
         }

         if (compound == null || key == null || key == NBTEditor.Type.NEW_ELEMENT) {
            if (keys[index + 1] != null && !(keys[index + 1] instanceof Integer) && keys[index + 1] != NBTEditor.Type.NEW_ELEMENT) {
               compound = getNMSClass(NBTEditor.ClassId.NBTTagCompound).newInstance();
            } else {
               compound = getNMSClass(NBTEditor.ClassId.NBTTagList).newInstance();
            }

            if (getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(prevCompound)) {
               if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_14)) {
                  getMethod(NBTEditor.MethodId.listAdd).invoke(prevCompound, getMethod(NBTEditor.MethodId.listSize).invoke(prevCompound), compound);
               } else {
                  getMethod(NBTEditor.MethodId.listAdd).invoke(prevCompound, compound);
               }
            } else {
               getMethod(NBTEditor.MethodId.compoundSet).invoke(prevCompound, (String)key, compound);
            }
         }
      }

      if (keys.length > 0) {
         Object lastKey = keys[keys.length - 1];
         if (lastKey != null && lastKey != NBTEditor.Type.NEW_ELEMENT) {
            if (lastKey instanceof Integer) {
               if (wrappedValue == NBTEditor.Type.DELETE) {
                  getMethod(NBTEditor.MethodId.listRemove).invoke(compound, (Integer)lastKey);
               } else {
                  getMethod(NBTEditor.MethodId.listSet).invoke(compound, (Integer)lastKey, wrappedValue);
               }
            } else if (wrappedValue == NBTEditor.Type.DELETE) {
               getMethod(NBTEditor.MethodId.compoundRemove).invoke(compound, (String)lastKey);
            } else {
               getMethod(NBTEditor.MethodId.compoundSet).invoke(compound, (String)lastKey, wrappedValue);
            }
         } else if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_14)) {
            getMethod(NBTEditor.MethodId.listAdd).invoke(compound, getMethod(NBTEditor.MethodId.listSize).invoke(compound), wrappedValue);
         } else {
            getMethod(NBTEditor.MethodId.listAdd).invoke(compound, wrappedValue);
         }
      } else if (wrappedValue != null
         && getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(wrappedValue)
         && getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(compound)) {
         for (String keyx : getKeys(wrappedValue)) {
            getMethod(NBTEditor.MethodId.compoundSet).invoke(compound, keyx, getMethod(NBTEditor.MethodId.compoundGet).invoke(wrappedValue, keyx));
         }
      }
   }

   private static NBTEditor.NBTCompound getNBTTag(Object tag, Object... keys) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      Object compound = tag;

      for (Object key : keys) {
         if (compound == null) {
            return null;
         }

         if (getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(compound)) {
            if (key == NBTEditor.Type.CUSTOM_DATA) {
               if (!LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                  continue;
               }

               key = "minecraft:custom_data";
            } else if (key == NBTEditor.Type.ITEMSTACK_COMPONENTS) {
               if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                  key = "components";
               } else {
                  key = "tag";
               }
            }

            compound = getMethod(NBTEditor.MethodId.compoundGet).invoke(compound, (String)key);
         } else if (getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(compound)) {
            int keyIndex = (Integer)key;
            List<?> tagList = (List<?>)NBTListData.get(compound);
            if (keyIndex >= 0 && keyIndex < tagList.size()) {
               compound = tagList.get(keyIndex);
            } else {
               compound = null;
            }
         }
      }

      return new NBTEditor.NBTCompound(compound);
   }

   private static Object getTag(Object tag, Object... keys) throws IllegalAccessException, IllegalArgumentException, InvocationTargetException {
      if (keys.length == 0) {
         return getTags(tag);
      } else {
         Object nbtObj = tag;

         for (Object key : keys) {
            if (nbtObj == null) {
               return null;
            }

            if (getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(nbtObj)) {
               if (key == NBTEditor.Type.CUSTOM_DATA) {
                  if (!LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                     continue;
                  }

                  key = "minecraft:custom_data";
               } else if (key == NBTEditor.Type.ITEMSTACK_COMPONENTS) {
                  if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_20_R4)) {
                     key = "components";
                  } else {
                     key = "tag";
                  }
               }

               if (!(key instanceof String)) {
                  throw new IllegalArgumentException("Key " + key + " is not a string! Must provide a valid key for an NBT Tag Compound");
               }

               nbtObj = getMethod(NBTEditor.MethodId.compoundGet).invoke(nbtObj, (String)key);
            } else {
               if (!getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(nbtObj)) {
                  return getNBTVar(nbtObj);
               }

               if (!(key instanceof Integer)) {
                  throw new IllegalArgumentException("Key " + key + " is not an integer! Must provide a valid index for an NBT Tag List");
               }

               int keyIndex = (Integer)key;
               List<?> tagList = (List<?>)NBTListData.get(nbtObj);
               if (keyIndex >= 0 && keyIndex < tagList.size()) {
                  nbtObj = tagList.get(keyIndex);
               } else {
                  nbtObj = null;
               }
            }
         }

         if (nbtObj == null) {
            return null;
         } else if (getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(nbtObj)) {
            return getTags(nbtObj);
         } else {
            return getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(nbtObj) ? getTags(nbtObj) : getNBTVar(nbtObj);
         }
      }
   }

   private static Object getTags(Object tag) {
      Map<Object, Object> tags = new HashMap<>();

      try {
         if (getNMSClass(NBTEditor.ClassId.NBTTagCompound).isInstance(tag)) {
            Map<String, Object> tagCompound = (Map<String, Object>)NBTCompoundMap.get(tag);

            for (String key : tagCompound.keySet()) {
               Object value = tagCompound.get(key);
               if (!getNMSClass(NBTEditor.ClassId.NBTTagEnd).isInstance(value)) {
                  tags.put(key, getTag(value));
               }
            }
         } else {
            if (!getNMSClass(NBTEditor.ClassId.NBTTagList).isInstance(tag)) {
               return getNBTVar(tag);
            }

            List<Object> tagList = (List<Object>)NBTListData.get(tag);

            for (int index = 0; index < tagList.size(); index++) {
               Object value = tagList.get(index);
               if (!getNMSClass(NBTEditor.ClassId.NBTTagEnd).isInstance(value)) {
                  tags.put(index, getTag(value));
               }
            }
         }

         return tags;
      } catch (Exception var6) {
         var6.printStackTrace();
         return tags;
      }
   }

   private static Object registryAccess() {
      try {
         return getMethod(NBTEditor.MethodId.registryAccess).invoke(getMethod(NBTEditor.MethodId.getServer).invoke(Bukkit.getServer()));
      } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var1) {
         var1.printStackTrace();
         return null;
      }
   }

   static {
      String cbPackage = Bukkit.getServer().getClass().getPackage().getName();
      String detectedVersion = cbPackage.substring(cbPackage.lastIndexOf(46) + 1);
      String bukkitVersion = Bukkit.getServer().getBukkitVersion();
      if (!detectedVersion.startsWith("v")) {
         detectedVersion = bukkitVersion;
      }

      VERSION = detectedVersion;
      LOCAL_VERSION = NBTEditor.MinecraftVersion.get(VERSION);
      BUKKIT_VERSION = bukkitVersion;
      classCache = new HashMap<>();
      methodCache = new HashMap<>();
      constructorCache = new HashMap<>();
      reflectionTargets = new TreeSet<>();
      reflectionTargets.addAll(
         Arrays.asList(
            new NBTEditor.ReflectionTarget.v1_8().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_9().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_11().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_12().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_13().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_15().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_16().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_17().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_18_R1().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_18_R2().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_19_R1().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_19_R2().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_20_R1().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_20_R2().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_20_R3().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_20_R4().setClassFetcher(NBTEditor::getNMSClass),
            new NBTEditor.ReflectionTarget.v1_21_R1().setClassFetcher(NBTEditor::getNMSClass)
         )
      );
      NBTClasses = new HashMap<>();

      try {
         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_17)) {
            NBTClasses.put(Byte.class, Class.forName("net.minecraft.nbt.NBTTagByte"));
            NBTClasses.put(Boolean.class, Class.forName("net.minecraft.nbt.NBTTagByte"));
            NBTClasses.put(String.class, Class.forName("net.minecraft.nbt.NBTTagString"));
            NBTClasses.put(Double.class, Class.forName("net.minecraft.nbt.NBTTagDouble"));
            NBTClasses.put(Integer.class, Class.forName("net.minecraft.nbt.NBTTagInt"));
            NBTClasses.put(Long.class, Class.forName("net.minecraft.nbt.NBTTagLong"));
            NBTClasses.put(Short.class, Class.forName("net.minecraft.nbt.NBTTagShort"));
            NBTClasses.put(Float.class, Class.forName("net.minecraft.nbt.NBTTagFloat"));
            NBTClasses.put(Class.forName("[B"), Class.forName("net.minecraft.nbt.NBTTagByteArray"));
            NBTClasses.put(Class.forName("[I"), Class.forName("net.minecraft.nbt.NBTTagIntArray"));
         } else {
            NBTClasses.put(Byte.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagByte"));
            NBTClasses.put(Boolean.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagByte"));
            NBTClasses.put(String.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagString"));
            NBTClasses.put(Double.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagDouble"));
            NBTClasses.put(Integer.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagInt"));
            NBTClasses.put(Long.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagLong"));
            NBTClasses.put(Short.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagShort"));
            NBTClasses.put(Float.class, Class.forName("net.minecraft.server." + VERSION + ".NBTTagFloat"));
            NBTClasses.put(Class.forName("[B"), Class.forName("net.minecraft.server." + VERSION + ".NBTTagByteArray"));
            NBTClasses.put(Class.forName("[I"), Class.forName("net.minecraft.server." + VERSION + ".NBTTagIntArray"));
         }
      } catch (ClassNotFoundException var7) {
         var7.printStackTrace();
      }

      NBTConstructors = new HashMap<>();

      try {
         NBTConstructors.put(getNBTTag(Byte.class), getNBTTag(Byte.class).getDeclaredConstructor(byte.class));
         NBTConstructors.put(getNBTTag(Boolean.class), getNBTTag(Boolean.class).getDeclaredConstructor(byte.class));
         NBTConstructors.put(getNBTTag(String.class), getNBTTag(String.class).getDeclaredConstructor(String.class));
         NBTConstructors.put(getNBTTag(Double.class), getNBTTag(Double.class).getDeclaredConstructor(double.class));
         NBTConstructors.put(getNBTTag(Integer.class), getNBTTag(Integer.class).getDeclaredConstructor(int.class));
         NBTConstructors.put(getNBTTag(Long.class), getNBTTag(Long.class).getDeclaredConstructor(long.class));
         NBTConstructors.put(getNBTTag(Float.class), getNBTTag(Float.class).getDeclaredConstructor(float.class));
         NBTConstructors.put(getNBTTag(Short.class), getNBTTag(Short.class).getDeclaredConstructor(short.class));
         NBTConstructors.put(getNBTTag(Class.forName("[B")), getNBTTag(Class.forName("[B")).getDeclaredConstructor(Class.forName("[B")));
         NBTConstructors.put(getNBTTag(Class.forName("[I")), getNBTTag(Class.forName("[I")).getDeclaredConstructor(Class.forName("[I")));

         for (Constructor<?> cons : NBTConstructors.values()) {
            cons.setAccessible(true);
         }
      } catch (NoSuchMethodException | SecurityException | ClassNotFoundException var9) {
         var9.printStackTrace();
      }

      NBTTagFieldCache = new HashMap<>();

      try {
         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_17)) {
            NBTTagFieldCache.put(NBTClasses.get(Byte.class), NBTClasses.get(Byte.class).getDeclaredField("x"));
            NBTTagFieldCache.put(NBTClasses.get(Boolean.class), NBTClasses.get(Boolean.class).getDeclaredField("x"));
            NBTTagFieldCache.put(NBTClasses.get(String.class), NBTClasses.get(String.class).getDeclaredField("A"));
            NBTTagFieldCache.put(NBTClasses.get(Double.class), NBTClasses.get(Double.class).getDeclaredField("w"));
            NBTTagFieldCache.put(NBTClasses.get(Integer.class), NBTClasses.get(Integer.class).getDeclaredField("c"));
            NBTTagFieldCache.put(NBTClasses.get(Long.class), NBTClasses.get(Long.class).getDeclaredField("c"));
            NBTTagFieldCache.put(NBTClasses.get(Float.class), NBTClasses.get(Float.class).getDeclaredField("w"));
            NBTTagFieldCache.put(NBTClasses.get(Short.class), NBTClasses.get(Short.class).getDeclaredField("c"));
            NBTTagFieldCache.put(NBTClasses.get(Class.forName("[B")), NBTClasses.get(Class.forName("[B")).getDeclaredField("c"));
            NBTTagFieldCache.put(NBTClasses.get(Class.forName("[I")), NBTClasses.get(Class.forName("[I")).getDeclaredField("c"));

            for (Field field : NBTTagFieldCache.values()) {
               field.setAccessible(true);
            }
         } else {
            for (Class<?> clazz : NBTClasses.values()) {
               Field data = clazz.getDeclaredField("data");
               data.setAccessible(true);
               NBTTagFieldCache.put(clazz, data);
            }
         }
      } catch (NoSuchFieldException | SecurityException | ClassNotFoundException var8) {
         var8.printStackTrace();
      }

      try {
         if (LOCAL_VERSION.greaterThanOrEqualTo(NBTEditor.MinecraftVersion.v1_17)) {
            NBTListData = getNMSClass(NBTEditor.ClassId.NBTTagList).getDeclaredField("c");
            NBTCompoundMap = getNMSClass(NBTEditor.ClassId.NBTTagCompound).getDeclaredField("x");
         } else {
            NBTListData = getNMSClass(NBTEditor.ClassId.NBTTagList).getDeclaredField("list");
            NBTCompoundMap = getNMSClass(NBTEditor.ClassId.NBTTagCompound).getDeclaredField("map");
         }

         NBTListData.setAccessible(true);
         NBTCompoundMap.setAccessible(true);
         skullProfile = getNMSClass(NBTEditor.ClassId.CraftMetaSkull).getDeclaredField("profile");
         skullProfile.setAccessible(true);
      } catch (Exception var6) {
         var6.printStackTrace();
      }
   }

   private static enum ClassId {
      NBTBase,
      NBTTagCompound,
      NBTTagList,
      NBTTagEnd,
      MojangsonParser,
      ItemStack,
      Entity,
      EntityLiving,
      BlockPosition,
      IBlockData,
      World,
      TileEntity,
      TileEntitySkull,
      CraftItemStack,
      CraftMetaSkull,
      CraftEntity,
      CraftWorld,
      CraftBlockState,
      GameProfile,
      Property,
      PropertyMap,
      CraftServer,
      MinecraftServer,
      RegistryAccess,
      ResolvableProfile,
      IRegistryCustomDimension;
   }

   private static enum MethodId {
      compoundGet,
      compoundSet,
      compoundHasKey,
      listSet,
      listAdd,
      listSize,
      listRemove,
      compoundRemove,
      compoundKeys,
      itemHasTag,
      getItemTag,
      setItemTag,
      itemSave,
      asNMSCopy,
      asBukkitCopy,
      getEntityHandle,
      getWorldHandle,
      getTileEntity,
      getTileType,
      getEntityTag,
      setEntityTag,
      createStack,
      setTileTag,
      getTileTag,
      getProperties,
      setGameProfile,
      setCraftMetaSkullProfile,
      propertyValues,
      putProperty,
      getPropertyName,
      getPropertyValue,
      loadNBTTagCompound,
      getServer,
      registryAccess,
      saveOptional,
      setCraftMetaSkullResolvableProfile,
      getResolvableProfileGameProfile;
   }

   public static enum MinecraftVersion {
      v1_8,
      v1_9,
      v1_10,
      v1_11,
      v1_12,
      v1_13,
      v1_14,
      v1_15,
      v1_16,
      v1_17,
      v1_18_R1,
      v1_18_R2,
      v1_19_R1,
      v1_19_R2,
      v1_19_R3,
      v1_20_R1,
      v1_20(false),
      v1_20_1(false),
      v1_20_R2,
      v1_20_2(false),
      v1_20_R3,
      v1_20_3(false),
      v1_20_R4,
      v1_20_4(false),
      v1_20_5(false),
      v1_20_6(false),
      v1_21_R1,
      v1_21(false),
      v1_21_R2,
      v1_21_3(false),
      v1_21_R3,
      v1_21_4(false),
      v1_22;

      private boolean implemented = true;

      private MinecraftVersion() {
         this(true);
      }

      private MinecraftVersion(boolean implemented) {
         this.implemented = implemented;
      }

      public boolean greaterThanOrEqualTo(NBTEditor.MinecraftVersion other) {
         return this.ordinal() >= other.ordinal();
      }

      public boolean lessThanOrEqualTo(NBTEditor.MinecraftVersion other) {
         return this.ordinal() <= other.ordinal();
      }

      public static NBTEditor.MinecraftVersion get(String v) {
         v = v.replace('.', '_');

         for (int i = values().length; i > 0; i--) {
            NBTEditor.MinecraftVersion k = values()[i - 1];
            if (v.contains(k.name().substring(1))) {
               return getLastImplemented(k);
            }
         }

         return values()[values().length - 1];
      }

      private static NBTEditor.MinecraftVersion getLastImplemented(NBTEditor.MinecraftVersion v) {
         for (int i = v.ordinal(); i >= 0; i--) {
            NBTEditor.MinecraftVersion version = values()[i];
            if (version.implemented) {
               return version;
            }
         }

         return null;
      }
   }

   public static final class NBTCompound {
      protected final Object tag;

      protected NBTCompound(Object tag) {
         this.tag = tag;
      }

      public void set(Object value, Object... keys) {
         try {
            NBTEditor.setTag(this.tag, value, keys);
         } catch (Exception var4) {
            var4.printStackTrace();
         }
      }

      public String toJson() {
         return this.tag.toString();
      }

      public static NBTEditor.NBTCompound fromJson(String json) {
         try {
            return new NBTEditor.NBTCompound(NBTEditor.getMethod(NBTEditor.MethodId.loadNBTTagCompound).invoke(null, json));
         } catch (IllegalArgumentException | InvocationTargetException | IllegalAccessException var2) {
            var2.printStackTrace();
            return null;
         }
      }

      @Override
      public String toString() {
         return this.tag.toString();
      }

      @Override
      public int hashCode() {
         return this.tag.hashCode();
      }

      @Override
      public boolean equals(Object obj) {
         if (this == obj) {
            return true;
         } else if (obj == null) {
            return false;
         } else if (this.getClass() != obj.getClass()) {
            return false;
         } else {
            NBTEditor.NBTCompound other = (NBTEditor.NBTCompound)obj;
            if (this.tag == null) {
               if (other.tag != null) {
                  return false;
               }
            } else if (!this.tag.equals(other.tag)) {
               return false;
            }

            return true;
         }
      }
   }

   private abstract static class ReflectionTarget implements Comparable<NBTEditor.ReflectionTarget> {
      private final NBTEditor.MinecraftVersion version;
      private Function<NBTEditor.ClassId, Class<?>> classFetcher;
      private final Map<NBTEditor.ClassId, String> classTargets = new HashMap<>();
      private final Map<NBTEditor.MethodId, NBTEditor.ReflectionTarget.ConstructorTarget> methodTargets = new HashMap<>();
      private final Map<NBTEditor.ClassId, NBTEditor.ReflectionTarget.ConstructorTarget> constructorTargets = new HashMap<>();

      protected ReflectionTarget(NBTEditor.MinecraftVersion version) {
         this.version = version;
      }

      protected NBTEditor.MinecraftVersion getVersion() {
         return this.version;
      }

      protected final NBTEditor.ReflectionTarget setClassFetcher(Function<NBTEditor.ClassId, Class<?>> func) {
         this.classFetcher = func;
         return this;
      }

      protected final void addClass(NBTEditor.ClassId name, String path) {
         this.classTargets.put(name, path);
      }

      protected final NBTEditor.ReflectionTarget.MethodTarget addMethod(NBTEditor.MethodId name, NBTEditor.ClassId clazz, String methodName, Object... params) {
         NBTEditor.ReflectionTarget.MethodTarget newTarget = new NBTEditor.ReflectionTarget.MethodTarget(clazz, methodName, params);
         this.methodTargets.put(name, newTarget);
         return newTarget;
      }

      protected final NBTEditor.ReflectionTarget.ReturnMethodTarget addMethod(
         NBTEditor.MethodId name, NBTEditor.ClassId clazz, NBTEditor.ClassId returnType, Object... params
      ) {
         NBTEditor.ReflectionTarget.ReturnMethodTarget newTarget = new NBTEditor.ReflectionTarget.ReturnMethodTarget(clazz, returnType, params);
         this.methodTargets.put(name, newTarget);
         return newTarget;
      }

      protected final void addConstructor(NBTEditor.ClassId clazz, Object... params) {
         this.constructorTargets.put(clazz, new NBTEditor.ReflectionTarget.ConstructorTarget(clazz, params));
      }

      protected final Class<?> fetchClass(NBTEditor.ClassId name) throws ClassNotFoundException {
         String className = this.classTargets.get(name);
         return className != null ? Class.forName(className) : null;
      }

      protected final Method fetchMethod(NBTEditor.MethodId name) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
         NBTEditor.ReflectionTarget.ConstructorTarget constructorTarget = this.methodTargets.get(name);
         if (constructorTarget instanceof NBTEditor.ReflectionTarget.MethodTarget target) {
            Class<?> clazz = this.findClass(target.clazz);
            Class<?>[] params = this.convert(target.params);

            try {
               return clazz.getMethod(target.name, params);
            } catch (NoSuchMethodException var12) {
               try {
                  Method method = clazz.getDeclaredMethod(target.name, params);
                  method.setAccessible(true);
                  return method;
               } catch (NoSuchMethodException var11) {
                  if (target.silent) {
                     return null;
                  } else {
                     throw var12;
                  }
               }
            }
         } else if (constructorTarget instanceof NBTEditor.ReflectionTarget.ReturnMethodTarget target) {
            Class<?> clazz = this.findClass(target.clazz);
            Class<?> returnClazz = this.findClass(target.returnType);
            Class<?>[] params = this.convert(target.params);

            for (Method method : clazz.getDeclaredMethods()) {
               if (method.getReturnType().equals(returnClazz) && this.matches(method.getParameterTypes(), params)) {
                  method.setAccessible(true);
                  return method;
               }
            }

            return null;
         } else {
            return null;
         }
      }

      protected final Constructor<?> fetchConstructor(NBTEditor.ClassId name) throws NoSuchMethodException, SecurityException, ClassNotFoundException {
         NBTEditor.ReflectionTarget.ConstructorTarget target = this.constructorTargets.get(name);
         return target != null ? this.findClass(target.clazz).getConstructor(this.convert(target.params)) : null;
      }

      private final boolean matches(Class<?>[] params, Class<?>[] find) {
         if (params.length != find.length) {
            return false;
         } else {
            for (int i = 0; i < params.length; i++) {
               if (!find[i].isAssignableFrom(params[i])) {
                  return false;
               }
            }

            return true;
         }
      }

      private final Class<?>[] convert(Object[] objects) throws ClassNotFoundException {
         Class<?>[] params = new Class[objects.length];

         for (int i = 0; i < objects.length; i++) {
            Object obj = objects[i];
            if (obj instanceof Class) {
               params[i] = (Class<?>)obj;
            } else {
               if (!(obj instanceof NBTEditor.ClassId)) {
                  throw new IllegalArgumentException("Invalid parameter type: " + obj);
               }

               params[i] = this.findClass((NBTEditor.ClassId)obj);
            }
         }

         return params;
      }

      private final Class<?> findClass(NBTEditor.ClassId name) throws ClassNotFoundException {
         return this.classFetcher != null ? this.classFetcher.apply(name) : this.fetchClass(name);
      }

      public int compareTo(NBTEditor.ReflectionTarget o) {
         return o.version.compareTo(this.version);
      }

      private static class ConstructorTarget {
         final NBTEditor.ClassId clazz;
         final Object[] params;

         public ConstructorTarget(NBTEditor.ClassId clazz, Object... params) {
            this.clazz = clazz;
            this.params = params;
         }
      }

      private static class MethodTarget extends NBTEditor.ReflectionTarget.ConstructorTarget {
         final String name;
         boolean silent = false;

         public MethodTarget(NBTEditor.ClassId clazz, String name, Object... params) {
            super(clazz, params);
            this.name = name;
         }

         public NBTEditor.ReflectionTarget.MethodTarget failSilently(boolean silent) {
            this.silent = silent;
            return this;
         }
      }

      private static class ReturnMethodTarget extends NBTEditor.ReflectionTarget.ConstructorTarget {
         final NBTEditor.ClassId returnType;

         public ReturnMethodTarget(NBTEditor.ClassId clazz, NBTEditor.ClassId returnType, Object... params) {
            super(clazz, params);
            this.returnType = returnType;
         }
      }

      private static class v1_11 extends NBTEditor.ReflectionTarget {
         protected v1_11() {
            super(NBTEditor.MinecraftVersion.v1_11);
            this.addConstructor(NBTEditor.ClassId.ItemStack, new Object[]{NBTEditor.ClassId.NBTTagCompound});
         }
      }

      private static class v1_12 extends NBTEditor.ReflectionTarget {
         protected v1_12() {
            super(NBTEditor.MinecraftVersion.v1_12);
            this.addMethod(NBTEditor.MethodId.setTileTag, NBTEditor.ClassId.TileEntity, "load", new Object[]{NBTEditor.ClassId.NBTTagCompound});
         }
      }

      private static class v1_13 extends NBTEditor.ReflectionTarget {
         protected v1_13() {
            super(NBTEditor.MinecraftVersion.v1_13);
            this.addMethod(NBTEditor.MethodId.compoundKeys, NBTEditor.ClassId.NBTTagCompound, "getKeys", new Object[0]);
            this.addMethod(NBTEditor.MethodId.createStack, NBTEditor.ClassId.ItemStack, "a", new Object[]{NBTEditor.ClassId.NBTTagCompound});
         }
      }

      private static class v1_15 extends NBTEditor.ReflectionTarget {
         protected v1_15() {
            super(NBTEditor.MinecraftVersion.v1_15);
            this.addMethod(
                  NBTEditor.MethodId.setCraftMetaSkullProfile, NBTEditor.ClassId.CraftMetaSkull, "setProfile", new Object[]{NBTEditor.ClassId.GameProfile}
               )
               .failSilently(NBTEditor.MinecraftVersion.v1_21_R1.lessThanOrEqualTo(NBTEditor.LOCAL_VERSION));
         }
      }

      private static class v1_16 extends NBTEditor.ReflectionTarget {
         protected v1_16() {
            super(NBTEditor.MinecraftVersion.v1_16);
            this.addMethod(NBTEditor.MethodId.getEntityTag, NBTEditor.ClassId.Entity, "save", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.setEntityTag, NBTEditor.ClassId.Entity, "load", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.getTileType, NBTEditor.ClassId.World, "getType", new Object[]{NBTEditor.ClassId.BlockPosition});
            this.addMethod(
               NBTEditor.MethodId.setTileTag,
               NBTEditor.ClassId.TileEntity,
               "load",
               new Object[]{NBTEditor.ClassId.IBlockData, NBTEditor.ClassId.NBTTagCompound}
            );
         }
      }

      private static class v1_17 extends NBTEditor.ReflectionTarget {
         protected v1_17() {
            super(NBTEditor.MinecraftVersion.v1_17);
            this.addClass(NBTEditor.ClassId.NBTBase, "net.minecraft.nbt.NBTBase");
            this.addClass(NBTEditor.ClassId.NBTTagCompound, "net.minecraft.nbt.NBTTagCompound");
            this.addClass(NBTEditor.ClassId.NBTTagList, "net.minecraft.nbt.NBTTagList");
            this.addClass(NBTEditor.ClassId.NBTTagEnd, "net.minecraft.nbt.NBTTagEnd");
            this.addClass(NBTEditor.ClassId.MojangsonParser, "net.minecraft.nbt.MojangsonParser");
            this.addClass(NBTEditor.ClassId.ItemStack, "net.minecraft.world.item.ItemStack");
            this.addClass(NBTEditor.ClassId.Entity, "net.minecraft.world.entity.Entity");
            this.addClass(NBTEditor.ClassId.EntityLiving, "net.minecraft.world.entity.EntityLiving");
            this.addClass(NBTEditor.ClassId.BlockPosition, "net.minecraft.core.BlockPosition");
            this.addClass(NBTEditor.ClassId.IBlockData, "net.minecraft.world.level.block.state.IBlockData");
            this.addClass(NBTEditor.ClassId.World, "net.minecraft.world.level.World");
            this.addClass(NBTEditor.ClassId.TileEntity, "net.minecraft.world.level.block.entity.TileEntity");
            this.addClass(NBTEditor.ClassId.TileEntitySkull, "net.minecraft.world.level.block.entity.TileEntitySkull");
            this.addMethod(NBTEditor.MethodId.listSet, NBTEditor.ClassId.NBTTagList, "set", new Object[]{int.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.setTileTag, NBTEditor.ClassId.TileEntity, "load", new Object[]{NBTEditor.ClassId.NBTTagCompound});
         }
      }

      private static class v1_18_R1 extends NBTEditor.ReflectionTarget {
         protected v1_18_R1() {
            super(NBTEditor.MinecraftVersion.v1_18_R1);
            this.addMethod(NBTEditor.MethodId.compoundGet, NBTEditor.ClassId.NBTTagCompound, "c", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.compoundSet, NBTEditor.ClassId.NBTTagCompound, "a", new Object[]{String.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.compoundHasKey, NBTEditor.ClassId.NBTTagCompound, "e", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.listSet, NBTEditor.ClassId.NBTTagList, "d", new Object[]{int.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.listAdd, NBTEditor.ClassId.NBTTagList, "c", new Object[]{int.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.listRemove, NBTEditor.ClassId.NBTTagList, "c", new Object[]{int.class});
            this.addMethod(NBTEditor.MethodId.compoundRemove, NBTEditor.ClassId.NBTTagCompound, "r", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.compoundKeys, NBTEditor.ClassId.NBTTagCompound, "d", new Object[0]);
            this.addMethod(NBTEditor.MethodId.itemHasTag, NBTEditor.ClassId.ItemStack, "r", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getItemTag, NBTEditor.ClassId.ItemStack, "s", new Object[0]);
            this.addMethod(NBTEditor.MethodId.setItemTag, NBTEditor.ClassId.ItemStack, "c", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.itemSave, NBTEditor.ClassId.ItemStack, "b", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.getEntityTag, NBTEditor.ClassId.Entity, "f", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.setEntityTag, NBTEditor.ClassId.Entity, "g", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.setTileTag, NBTEditor.ClassId.TileEntity, "a", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.getTileTag, NBTEditor.ClassId.TileEntity, "m", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getTileEntity, NBTEditor.ClassId.World, "c_", new Object[]{NBTEditor.ClassId.BlockPosition});
            this.addMethod(NBTEditor.MethodId.setGameProfile, NBTEditor.ClassId.TileEntitySkull, "a", new Object[]{NBTEditor.ClassId.GameProfile});
            this.addMethod(NBTEditor.MethodId.loadNBTTagCompound, NBTEditor.ClassId.MojangsonParser, "a", new Object[]{String.class});
         }
      }

      private static class v1_18_R2 extends NBTEditor.ReflectionTarget {
         protected v1_18_R2() {
            super(NBTEditor.MinecraftVersion.v1_18_R2);
            this.addMethod(NBTEditor.MethodId.itemHasTag, NBTEditor.ClassId.ItemStack, "s", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getItemTag, NBTEditor.ClassId.ItemStack, "t", new Object[0]);
         }
      }

      private static class v1_19_R1 extends NBTEditor.ReflectionTarget {
         protected v1_19_R1() {
            super(NBTEditor.MinecraftVersion.v1_19_R1);
            this.addMethod(NBTEditor.MethodId.itemHasTag, NBTEditor.ClassId.ItemStack, "t", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getItemTag, NBTEditor.ClassId.ItemStack, "u", new Object[0]);
         }
      }

      private static class v1_19_R2 extends NBTEditor.ReflectionTarget {
         protected v1_19_R2() {
            super(NBTEditor.MinecraftVersion.v1_19_R2);
            this.addMethod(NBTEditor.MethodId.compoundKeys, NBTEditor.ClassId.NBTTagCompound, "e", new Object[0]);
         }
      }

      private static class v1_20_R1 extends NBTEditor.ReflectionTarget {
         protected v1_20_R1() {
            super(NBTEditor.MinecraftVersion.v1_20_R1);
            this.addMethod(NBTEditor.MethodId.itemHasTag, NBTEditor.ClassId.ItemStack, "u", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getItemTag, NBTEditor.ClassId.ItemStack, "v", new Object[0]);
         }
      }

      private static class v1_20_R2 extends NBTEditor.ReflectionTarget {
         protected v1_20_R2() {
            super(NBTEditor.MinecraftVersion.v1_20_R2);
            this.addMethod(NBTEditor.MethodId.getPropertyName, NBTEditor.ClassId.Property, "name", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getPropertyValue, NBTEditor.ClassId.Property, "value", new Object[0]);
         }
      }

      private static class v1_20_R3 extends NBTEditor.ReflectionTarget {
         protected v1_20_R3() {
            super(NBTEditor.MinecraftVersion.v1_20_R3);
            this.addMethod(NBTEditor.MethodId.getTileTag, NBTEditor.ClassId.TileEntity, "o", new Object[0]);
         }
      }

      private static class v1_20_R4 extends NBTEditor.ReflectionTarget {
         protected v1_20_R4() {
            super(NBTEditor.MinecraftVersion.v1_20_R4);
            if (!NBTEditor.BUKKIT_VERSION.startsWith("1.20.4") && !NBTEditor.BUKKIT_VERSION.startsWith("1.20.5")) {
               this.addClass(NBTEditor.ClassId.MinecraftServer, "net.minecraft.server.MinecraftServer");
               this.addClass(NBTEditor.ClassId.RegistryAccess, "net.minecraft.core.HolderLookup$a");
               this.addClass(NBTEditor.ClassId.ResolvableProfile, "net.minecraft.world.item.component.ResolvableProfile");
               this.addMethod(NBTEditor.MethodId.getServer, NBTEditor.ClassId.CraftServer, "getServer", new Object[0]);
               this.addMethod(NBTEditor.MethodId.registryAccess, NBTEditor.ClassId.MinecraftServer, "bc", new Object[0]);
               this.addMethod(NBTEditor.MethodId.saveOptional, NBTEditor.ClassId.ItemStack, "a", new Object[]{NBTEditor.ClassId.RegistryAccess});
               this.addMethod(
                  NBTEditor.MethodId.createStack,
                  NBTEditor.ClassId.ItemStack,
                  "a",
                  new Object[]{NBTEditor.ClassId.RegistryAccess, NBTEditor.ClassId.NBTTagCompound}
               );
               this.addMethod(NBTEditor.MethodId.getTileTag, NBTEditor.ClassId.TileEntity, "b", new Object[]{NBTEditor.ClassId.RegistryAccess});
               this.addMethod(
                  NBTEditor.MethodId.setTileTag,
                  NBTEditor.ClassId.TileEntity,
                  "a",
                  new Object[]{NBTEditor.ClassId.NBTTagCompound, NBTEditor.ClassId.RegistryAccess}
               );
            }
         }
      }

      private static class v1_21_R1 extends NBTEditor.ReflectionTarget {
         protected v1_21_R1() {
            super(NBTEditor.MinecraftVersion.v1_21_R1);
            this.addClass(NBTEditor.ClassId.ResolvableProfile, "net.minecraft.world.item.component.ResolvableProfile");
            this.addMethod(
                  NBTEditor.MethodId.setCraftMetaSkullResolvableProfile,
                  NBTEditor.ClassId.CraftMetaSkull,
                  "setProfile",
                  new Object[]{NBTEditor.ClassId.ResolvableProfile}
               )
               .failSilently(true);
            this.addMethod(NBTEditor.MethodId.getResolvableProfileGameProfile, NBTEditor.ClassId.ResolvableProfile, "f", new Object[0]);
            this.addMethod(NBTEditor.MethodId.registryAccess, NBTEditor.ClassId.MinecraftServer, NBTEditor.ClassId.IRegistryCustomDimension, new Object[0]);
            this.addConstructor(NBTEditor.ClassId.ResolvableProfile, new Object[]{NBTEditor.ClassId.GameProfile});
         }
      }

      private static class v1_8 extends NBTEditor.ReflectionTarget {
         protected v1_8() {
            super(NBTEditor.MinecraftVersion.v1_8);
            String craftbukkitPackage = Bukkit.getServer().getClass().getPackage().getName();
            this.addClass(NBTEditor.ClassId.NBTBase, "net.minecraft.server." + NBTEditor.VERSION + ".NBTBase");
            this.addClass(NBTEditor.ClassId.NBTTagCompound, "net.minecraft.server." + NBTEditor.VERSION + ".NBTTagCompound");
            this.addClass(NBTEditor.ClassId.NBTTagList, "net.minecraft.server." + NBTEditor.VERSION + ".NBTTagList");
            this.addClass(NBTEditor.ClassId.NBTTagEnd, "net.minecraft.server." + NBTEditor.VERSION + ".NBTTagEnd");
            this.addClass(NBTEditor.ClassId.MojangsonParser, "net.minecraft.server." + NBTEditor.VERSION + ".MojangsonParser");
            this.addClass(NBTEditor.ClassId.ItemStack, "net.minecraft.server." + NBTEditor.VERSION + ".ItemStack");
            this.addClass(NBTEditor.ClassId.Entity, "net.minecraft.server." + NBTEditor.VERSION + ".Entity");
            this.addClass(NBTEditor.ClassId.EntityLiving, "net.minecraft.server." + NBTEditor.VERSION + ".EntityLiving");
            this.addClass(NBTEditor.ClassId.BlockPosition, "net.minecraft.server." + NBTEditor.VERSION + ".BlockPosition");
            this.addClass(NBTEditor.ClassId.IBlockData, "net.minecraft.server." + NBTEditor.VERSION + ".IBlockData");
            this.addClass(NBTEditor.ClassId.World, "net.minecraft.server." + NBTEditor.VERSION + ".World");
            this.addClass(NBTEditor.ClassId.TileEntity, "net.minecraft.server." + NBTEditor.VERSION + ".TileEntity");
            this.addClass(NBTEditor.ClassId.TileEntitySkull, "net.minecraft.server." + NBTEditor.VERSION + ".TileEntitySkull");
            this.addClass(NBTEditor.ClassId.CraftServer, craftbukkitPackage + ".CraftServer");
            this.addClass(NBTEditor.ClassId.CraftItemStack, craftbukkitPackage + ".inventory.CraftItemStack");
            this.addClass(NBTEditor.ClassId.CraftMetaSkull, craftbukkitPackage + ".inventory.CraftMetaSkull");
            this.addClass(NBTEditor.ClassId.CraftEntity, craftbukkitPackage + ".entity.CraftEntity");
            this.addClass(NBTEditor.ClassId.CraftWorld, craftbukkitPackage + ".CraftWorld");
            this.addClass(NBTEditor.ClassId.CraftBlockState, craftbukkitPackage + ".block.CraftBlockState");
            this.addClass(NBTEditor.ClassId.GameProfile, "com.mojang.authlib.GameProfile");
            this.addClass(NBTEditor.ClassId.Property, "com.mojang.authlib.properties.Property");
            this.addClass(NBTEditor.ClassId.PropertyMap, "com.mojang.authlib.properties.PropertyMap");
            this.addMethod(NBTEditor.MethodId.compoundGet, NBTEditor.ClassId.NBTTagCompound, "get", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.compoundSet, NBTEditor.ClassId.NBTTagCompound, "set", new Object[]{String.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.compoundHasKey, NBTEditor.ClassId.NBTTagCompound, "hasKey", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.listSet, NBTEditor.ClassId.NBTTagList, "a", new Object[]{int.class, NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.listAdd, NBTEditor.ClassId.NBTTagList, "add", new Object[]{NBTEditor.ClassId.NBTBase});
            this.addMethod(NBTEditor.MethodId.listSize, NBTEditor.ClassId.NBTTagList, "size", new Object[0]);
            this.addMethod(NBTEditor.MethodId.listRemove, NBTEditor.ClassId.NBTTagList, "a", new Object[]{int.class});
            this.addMethod(NBTEditor.MethodId.compoundRemove, NBTEditor.ClassId.NBTTagCompound, "remove", new Object[]{String.class});
            this.addMethod(NBTEditor.MethodId.compoundKeys, NBTEditor.ClassId.NBTTagCompound, "c", new Object[0]);
            this.addMethod(NBTEditor.MethodId.itemHasTag, NBTEditor.ClassId.ItemStack, "hasTag", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getItemTag, NBTEditor.ClassId.ItemStack, "getTag", new Object[0]);
            this.addMethod(NBTEditor.MethodId.setItemTag, NBTEditor.ClassId.ItemStack, "setTag", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.itemSave, NBTEditor.ClassId.ItemStack, "save", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.asNMSCopy, NBTEditor.ClassId.CraftItemStack, "asNMSCopy", new Object[]{ItemStack.class});
            this.addMethod(NBTEditor.MethodId.asBukkitCopy, NBTEditor.ClassId.CraftItemStack, "asBukkitCopy", new Object[]{NBTEditor.ClassId.ItemStack});
            this.addMethod(NBTEditor.MethodId.getEntityHandle, NBTEditor.ClassId.CraftEntity, "getHandle", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getEntityTag, NBTEditor.ClassId.Entity, "c", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.setEntityTag, NBTEditor.ClassId.Entity, "f", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.createStack, NBTEditor.ClassId.ItemStack, "createStack", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.setTileTag, NBTEditor.ClassId.TileEntity, "a", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.getTileTag, NBTEditor.ClassId.TileEntity, "b", new Object[]{NBTEditor.ClassId.NBTTagCompound});
            this.addMethod(NBTEditor.MethodId.getWorldHandle, NBTEditor.ClassId.CraftWorld, "getHandle", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getTileEntity, NBTEditor.ClassId.World, "getTileEntity", new Object[]{NBTEditor.ClassId.BlockPosition});
            this.addMethod(NBTEditor.MethodId.getProperties, NBTEditor.ClassId.GameProfile, "getProperties", new Object[0]);
            this.addMethod(NBTEditor.MethodId.setGameProfile, NBTEditor.ClassId.TileEntitySkull, "setGameProfile", new Object[]{NBTEditor.ClassId.GameProfile});
            this.addMethod(NBTEditor.MethodId.propertyValues, NBTEditor.ClassId.PropertyMap, "values", new Object[0]);
            this.addMethod(NBTEditor.MethodId.putProperty, NBTEditor.ClassId.PropertyMap, "put", new Object[]{Object.class, Object.class});
            this.addMethod(NBTEditor.MethodId.getPropertyName, NBTEditor.ClassId.Property, "getName", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getPropertyValue, NBTEditor.ClassId.Property, "getValue", new Object[0]);
            this.addMethod(NBTEditor.MethodId.loadNBTTagCompound, NBTEditor.ClassId.MojangsonParser, "parse", new Object[]{String.class});
            this.addConstructor(NBTEditor.ClassId.BlockPosition, new Object[]{int.class, int.class, int.class});
            this.addConstructor(NBTEditor.ClassId.GameProfile, new Object[]{UUID.class, String.class});
            this.addConstructor(NBTEditor.ClassId.Property, new Object[]{String.class, String.class});
         }
      }

      private static class v1_9 extends NBTEditor.ReflectionTarget {
         protected v1_9() {
            super(NBTEditor.MinecraftVersion.v1_9);
            this.addMethod(NBTEditor.MethodId.listRemove, NBTEditor.ClassId.NBTTagList, "remove", new Object[]{int.class});
            this.addMethod(NBTEditor.MethodId.compoundKeys, NBTEditor.ClassId.NBTTagCompound, "c", new Object[0]);
            this.addMethod(NBTEditor.MethodId.getTileTag, NBTEditor.ClassId.TileEntity, "save", new Object[]{NBTEditor.ClassId.NBTTagCompound});
         }
      }
   }

   private static enum Type {
      COMPOUND,
      LIST,
      NEW_ELEMENT,
      DELETE,
      CUSTOM_DATA,
      ITEMSTACK_COMPONENTS;
   }
}
