package nl.hauntedmc.dungeons.util.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class ClassReflectionUtils {
   private ClassReflectionUtils() {
   }

   public static void collectAllFields(List<Field> fields, Class<?> type) {
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      if (type.getSuperclass() != null) {
         collectAllFields(fields, type.getSuperclass());
      }
   }

   public static void collectAnnotatedFields(List<Field> fields, Class<?> type, Class<? extends Annotation> annotation) {
      for (Field field : type.getDeclaredFields()) {
         if (field.getAnnotation(annotation) != null) {
            fields.add(field);
         }
      }

      if (type.getSuperclass() != null) {
         collectAnnotatedFields(fields, type.getSuperclass(), annotation);
      }
   }

   public static void collectAnnotatedMethods(List<Method> methods, Class<?> type, Class<? extends Annotation> annotation) {
      for (Method method : type.getDeclaredMethods()) {
         if (method.getAnnotation(annotation) != null) {
            methods.add(method);
         }
      }

      if (type.getSuperclass() != null) {
         collectAnnotatedMethods(methods, type.getSuperclass(), annotation);
      }
   }
}
