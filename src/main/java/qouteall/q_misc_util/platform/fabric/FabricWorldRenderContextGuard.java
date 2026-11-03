package qouteall.q_misc_util.platform.fabric;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.WorldRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

@Environment(EnvType.CLIENT)
final class FabricWorldRenderContextGuard {
   private static final Logger LOGGER = LogManager.getLogger(FabricWorldRenderContextGuard.class);
   private static final String CONTEXT_CLASS = "net.fabricmc.fabric.impl.client.rendering.world.WorldRenderContextImpl";
   private static boolean resolved;
   private static boolean unavailable;
   private static Field contextField;
   private static Field[] stateFields;

   private FabricWorldRenderContextGuard() {
   }

   @Nullable
   static Object[] snapshot(WorldRenderer renderer) {
      if (!resolve()) {
         return null;
      } else {
         try {
            Object context = contextField.get(renderer);
            if (context == null) {
               return null;
            } else {
               Object[] values = new Object[stateFields.length + 1];
               values[0] = context;

               for (int i = 0; i < stateFields.length; i++) {
                  values[i + 1] = stateFields[i].get(context);
               }

               return values;
            }
         } catch (Throwable var4) {
            giveUp(var4);
            return null;
         }
      }
   }

   static void restore(@Nullable Object[] values) {
      if (values != null && !unavailable) {
         try {
            Object context = values[0];

            for (int i = 0; i < stateFields.length; i++) {
               stateFields[i].set(context, values[i + 1]);
            }
         } catch (Throwable var3) {
            giveUp(var3);
         }
      }
   }

   private static boolean resolve() {
      if (resolved) {
         return !unavailable;
      } else {
         resolved = true;

         try {
            for (Field field : WorldRenderer.class.getDeclaredFields()) {
               if (field.getType().getName().equals("net.fabricmc.fabric.impl.client.rendering.world.WorldRenderContextImpl")
                  && !Modifier.isStatic(field.getModifiers())) {
                  contextField = field;
                  break;
               }
            }

            if (contextField == null) {
               throw new IllegalStateException("LevelRenderer has no field of type net.fabricmc.fabric.impl.client.rendering.world.WorldRenderContextImpl");
            } else {
               contextField.setAccessible(true);
               List<Field> found = new ArrayList<>();

               for (Field fieldx : contextField.getType().getDeclaredFields()) {
                  if (!Modifier.isStatic(fieldx.getModifiers())) {
                     fieldx.setAccessible(true);
                     found.add(fieldx);
                  }
               }

               stateFields = found.toArray(new Field[0]);
               return true;
            }
         } catch (Throwable var5) {
            giveUp(var5);
            return false;
         }
      }
   }

   private static void giveUp(Throwable t) {
      if (!unavailable) {
         unavailable = true;
         LOGGER.warn(
            "Fabric API's world render context cannot be kept across portal renders (its internals differ from Fabric API 0.141); other mods' world render listeners may see a portal's camera after a same-dimension portal is drawn",
            t
         );
      }
   }
}
