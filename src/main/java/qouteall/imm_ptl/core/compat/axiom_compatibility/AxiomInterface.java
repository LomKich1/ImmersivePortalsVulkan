package qouteall.imm_ptl.core.compat.axiom_compatibility;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.WorldRenderer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Environment(EnvType.CLIENT)
public final class AxiomInterface {
   private static final Logger LOGGER = LogManager.getLogger(AxiomInterface.class);
   private static final boolean PRESENT = FabricLoader.getInstance().isModLoaded("axiom");
   private static boolean resolved;
   private static boolean unavailable;
   @Nullable
   private static Field renderContextField;
   @Nullable
   private static Field lastProjectionField;
   @Nullable
   private static Method getInstance;
   @Nullable
   private static Method dimensionChanged;

   private AxiomInterface() {
   }

   public static boolean isPresent() {
      return PRESENT;
   }

   public static boolean shouldBeInactive() {
      return PortalRendering.isRendering() || isHandlingAnotherLevel();
   }

   public static boolean isHandlingAnotherLevel() {
      MinecraftClient client = MinecraftClient.getInstance();
      ClientPlayerEntity player = client.player;
      return player != null && client.world != null && client.world != player.getEntityWorld();
   }

   @Nullable
   public static Object[] snapshotRenderStatics() {
      if (!resolve()) {
         return null;
      } else {
         try {
            return new Object[]{
               renderContextField == null ? null : renderContextField.get(null), lastProjectionField == null ? null : lastProjectionField.get(null)
            };
         } catch (Throwable var1) {
            giveUp(var1);
            return null;
         }
      }
   }

   public static void restoreRenderStatics(@Nullable Object[] values) {
      if (values != null && !unavailable) {
         try {
            if (renderContextField != null) {
               renderContextField.set(null, values[0]);
            }

            if (lastProjectionField != null) {
               lastProjectionField.set(null, values[1]);
            }
         } catch (Throwable var2) {
            giveUp(var2);
         }
      }
   }

   public static void onDimensionChanged(RegistryKey<World> to) {
      if (resolve() && getInstance != null && dimensionChanged != null) {
         try {
            Object axiom = getInstance.invoke(null);
            if (axiom != null) {
               dimensionChanged.invoke(axiom, to);
            }
         } catch (Throwable var2) {
            giveUp(var2);
         }
      }
   }

   private static boolean resolve() {
      if (resolved) {
         return !unavailable;
      } else {
         resolved = true;
         if (!PRESENT) {
            unavailable = true;
            return false;
         } else {
            ClassLoader loader = AxiomInterface.class.getClassLoader();

            try {
               for (Field field : WorldRenderer.class.getDeclaredFields()) {
                  if (Modifier.isStatic(field.getModifiers()) && field.getType().getSimpleName().equals("AxiomWorldRenderContext")) {
                     field.setAccessible(true);
                     renderContextField = field;
                     break;
                  }
               }

               if (renderContextField == null) {
                  LOGGER.warn(
                     "Axiom's render context field was not found on LevelRenderer; its overlays may use a portal's camera after portal views are drawn"
                  );
               }

               lastProjectionField = Class.forName("com.moulberry.axiom.editor.EditorUI", false, loader).getField("lastProjectionMatrix");
               Class<?> axiom = Class.forName("com.moulberry.axiom.Axiom", false, loader);
               getInstance = axiom.getMethod("getInstance");
               dimensionChanged = axiom.getMethod("dimensionChanged", RegistryKey.class);
               return true;
            } catch (Throwable var5) {
               giveUp(var5);
               return false;
            }
         }
      }
   }

   private static void giveUp(Throwable t) {
      if (!unavailable) {
         unavailable = true;
         LOGGER.warn("Axiom's internals differ from Axiom 6.1.3; its render context and dimension changes are left to it", t);
      }
   }
}
