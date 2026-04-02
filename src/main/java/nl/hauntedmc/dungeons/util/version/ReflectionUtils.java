package nl.hauntedmc.dungeons.util.version;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import nl.hauntedmc.dungeons.util.reflection.ClassReflectionUtils;

@Deprecated(forRemoval = false)
public final class ReflectionUtils {
   private ReflectionUtils() {
   }

   public static void getAllFields(List<Field> fields, Class<?> type) {
      ClassReflectionUtils.collectAllFields(fields, type);
   }

   public static void getAnnotatedFields(List<Field> fields, Class<?> type, Class<? extends Annotation> annotation) {
      ClassReflectionUtils.collectAnnotatedFields(fields, type, annotation);
   }

   public static void getAnnotatedMethods(List<Method> methods, Class<?> type, Class<? extends Annotation> annotation) {
      ClassReflectionUtils.collectAnnotatedMethods(methods, type, annotation);
   }
}
