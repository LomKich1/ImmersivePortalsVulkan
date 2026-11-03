package qouteall.dimlib;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.registry.RegistryKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Environment(EnvType.CLIENT)
public class ClientDimensionInfo {
   private static final Logger LOGGER = LogManager.getLogger();
   public static ImmutableSet<RegistryKey<World>> dimensionIds;
   public static ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> dimensionIdToType;

   public static Set<RegistryKey<World>> getDimensionIds() {
      if (dimensionIds == null) {
         throw new IllegalStateException("The dimension info has not been synced yet");
      } else {
         return dimensionIds;
      }
   }

   public static Map<RegistryKey<World>, RegistryKey<DimensionType>> getDimensionIdToType() {
      if (dimensionIdToType == null) {
         throw new IllegalStateException("The dimension info has not been synced yet");
      } else {
         return dimensionIdToType;
      }
   }

   static void accept(ImmutableMap<RegistryKey<World>, RegistryKey<DimensionType>> m) {
      dimensionIdToType = m;
      dimensionIds = dimensionIdToType.keySet();
   }

   public static void cleanup() {
      LOGGER.info("Cleaning up client dimension info");
      dimensionIds = null;
      dimensionIdToType = null;
   }
}
