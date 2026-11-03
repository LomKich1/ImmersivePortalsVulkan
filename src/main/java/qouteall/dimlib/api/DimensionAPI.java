package qouteall.dimlib.api;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.util.Identifier;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.server.MinecraftServer;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.dimlib.ClientDimensionInfo;
import qouteall.dimlib.DimensionImpl;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.DynamicDimensionsImpl;
import qouteall.dimlib.ducks.IMinecraftServer;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class DimensionAPI {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final Event<DimensionAPI.ServerDimensionsLoadCallback> SERVER_DIMENSIONS_LOAD_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ServerDimensionsLoadCallback.class, listeners -> server -> {
         for (DimensionAPI.ServerDimensionsLoadCallback listener : listeners) {
            try {
               listener.run(server);
            } catch (Exception var7) {
               LOGGER.error("Error during server dimensions load event", var7);
            }
         }
      }
   );
   public static final Event<DimensionAPI.ServerDynamicUpdateListener> SERVER_DIMENSION_DYNAMIC_UPDATE_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ServerDynamicUpdateListener.class, arr -> (server, dims) -> {
         for (DimensionAPI.ServerDynamicUpdateListener listener : arr) {
            try {
               listener.run(server, dims);
            } catch (Exception var8) {
               LOGGER.error("Error during dimension update event", var8);
            }
         }
      }
   );
   public static final Event<DimensionAPI.ClientDynamicUpdateListener> CLIENT_DIMENSION_UPDATE_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.ClientDynamicUpdateListener.class, arr -> set -> {
         for (DimensionAPI.ClientDynamicUpdateListener listener : arr) {
            try {
               listener.run(set);
            } catch (Exception var7) {
               LOGGER.error("Error during dimension update event", var7);
            }
         }
      }
   );
   public static final Event<DimensionAPI.PreRemoveDimensionCallback> SERVER_PRE_REMOVE_DIMENSION_EVENT = EventFactory.createArrayBacked(
      DimensionAPI.PreRemoveDimensionCallback.class, listeners -> world -> {
         for (DimensionAPI.PreRemoveDimensionCallback listener : listeners) {
            try {
               listener.accept(world);
            } catch (Exception var7) {
               LOGGER.error("Error during before removing dimension event", var7);
            }
         }
      }
   );

   public static void addDimension(MinecraftServer server, Identifier dimensionId, DimensionOptions levelStem) {
      if (((IMinecraftServer)server).dimlib_getIsFinishedCreatingWorlds()) {
         addDimensionDynamically(server, dimensionId, levelStem);
      } else if (((IMinecraftServer)server).dimlib_getCanDirectlyRegisterDimensions()) {
         DimensionImpl.directlyRegisterLevelStem(server, dimensionId, levelStem);
      } else {
         LOGGER.error("Cannot add dimension at this time {}", dimensionId, new Throwable());
      }
   }

   public static boolean dimensionExistsInRegistry(MinecraftServer server, Identifier dimensionId) {
      return DimensionImpl.getDimensionRegistry(server).containsId(dimensionId);
   }

   public static void addDimensionIfNotExists(MinecraftServer server, Identifier dimensionId, Supplier<DimensionOptions> levelStem) {
      if (!dimensionExistsInRegistry(server, dimensionId)) {
         addDimension(server, dimensionId, levelStem.get());
      }
   }

   public static void addDimensionDynamically(MinecraftServer server, Identifier dimensionId, DimensionOptions levelStem) {
      Validate.isTrue(server.isRunning(), "The server is not running", new Object[0]);
      DynamicDimensionsImpl.addDimensionDynamically(server, dimensionId, levelStem);
   }

   public static void removeDimensionDynamically(ServerWorld world) {
      if (!world.getServer().isRunning()) {
         LOGGER.error("Cannot remove dimension at this time {}", world, new Throwable());
      } else {
         DynamicDimensionsImpl.removeDimensionDynamically(world);
      }
   }

   public static boolean isDimensionAlive(ServerWorld world) {
      return world.getServer().getWorld(world.getRegistryKey()) == world;
   }

   public static void suppressExperimentalWarning() {
      DimensionImpl.suppressExperimentalWarning = true;
   }

   public static void suppressExperimentalWarningForNamespace(String namespace) {
      DimensionImpl.STABLE_NAMESPACES.add(namespace);
   }

   @Environment(EnvType.CLIENT)
   public static Set<RegistryKey<World>> getClientDimensionIds() {
      return ClientDimensionInfo.getDimensionIds();
   }

   @Environment(EnvType.CLIENT)
   public static Map<RegistryKey<World>, RegistryKey<DimensionType>> getClientDimensionIdToTypeMap() {
      return ClientDimensionInfo.getDimensionIdToType();
   }

   public static void registerDimensionTemplate(String name, DimensionTemplate dimensionTemplate) {
      DimensionTemplate.registerDimensionTemplate(name, dimensionTemplate);
   }

   public interface ClientDynamicUpdateListener {
      void run(Set<RegistryKey<World>> var1);
   }

   public interface PreRemoveDimensionCallback {
      void accept(ServerWorld var1);
   }

   public interface ServerDimensionsLoadCallback {
      void run(MinecraftServer var1);
   }

   public interface ServerDynamicUpdateListener {
      void run(MinecraftServer var1, Set<RegistryKey<World>> var2);
   }
}
