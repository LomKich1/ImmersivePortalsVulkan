package qouteall.q_misc_util.platform;

import com.google.common.base.Suppliers;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public final class EarlyLoaderDetection {
   private static final Map<String, Boolean> MOD_PRESENCE = new ConcurrentHashMap<>();
   private static final Supplier<Boolean> MINECRAFT_FORGE = Suppliers.memoize(() -> {
      try {
         Class.forName("net.minecraftforge.fml.loading.FMLLoader");
         return true;
      } catch (Throwable var1) {
         return false;
      }
   });
   private static final Supplier<Boolean> NEO_FORGE = Suppliers.memoize(() -> {
      try {
         Class.forName("net.neoforged.fml.loading.FMLLoader");
         return true;
      } catch (Throwable var1) {
         return false;
      }
   });

   private EarlyLoaderDetection() {
   }

   public static boolean isMinecraftForge() {
      return MINECRAFT_FORGE.get();
   }

   public static boolean isNeoForge() {
      return NEO_FORGE.get();
   }

   public static boolean isForgeLike() {
      return isMinecraftForge() || isNeoForge();
   }

   public static boolean isModLoaded(String modId) {
      return MOD_PRESENCE.computeIfAbsent(modId, EarlyLoaderDetection::detect);
   }

   private static boolean detect(String modId) {
      try {
         Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
         Object instance = loaderClass.getMethod("getInstance").invoke(null);
         Object result = loaderClass.getMethod("isModLoaded", String.class).invoke(instance, modId);
         return result instanceof Boolean && (Boolean)result;
      } catch (Throwable var9) {
         for (String lmlName : new String[]{"net.neoforged.fml.loading.LoadingModList", "net.minecraftforge.fml.loading.LoadingModList"}) {
            try {
               Class<?> lmlClass = Class.forName(lmlName);
               Object lml = lmlClass.getMethod("get").invoke(null);
               Method byId = lmlClass.getMethod("getModFileById", String.class);
               return byId.invoke(lml, modId) != null;
            } catch (Throwable var8) {
            }
         }

         return false;
      }
   }
}
