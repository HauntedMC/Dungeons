package nl.hauntedmc.dungeons.util.version;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public final class ReflectionUtils {
   public static void getAllFields(List<Field> fields, Class<?> type) {
      fields.addAll(Arrays.asList(type.getDeclaredFields()));
      if (type.getSuperclass() != null) {
         getAllFields(fields, type.getSuperclass());
      }
   }

   public static void getAnnotatedFields(List<Field> fields, Class<?> type, Class<? extends Annotation> annotation) {
      for (Field field : type.getDeclaredFields()) {
         if (field.getAnnotation(annotation) != null) {
            fields.add(field);
         }
      }

      if (type.getSuperclass() != null) {
         getAnnotatedFields(fields, type.getSuperclass(), annotation);
      }
   }

   public static void getAnnotatedMethods(List<Method> methods, Class<?> type, Class<? extends Annotation> annotation) {
      for (Method method : type.getDeclaredMethods()) {
         if (method.getAnnotation(annotation) != null) {
            methods.add(method);
         }
      }

      if (type.getSuperclass() != null) {
         getAnnotatedMethods(methods, type.getSuperclass(), annotation);
      }
   }
}
