package qouteall.dimlib;

import java.util.HashSet;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.SimpleRegistry;
import net.minecraft.registry.DynamicRegistryManager.Immutable;
import net.minecraft.registry.entry.RegistryEntryInfo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import net.minecraft.world.SaveProperties;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.GeneratorOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qouteall.dimlib.ducks.IMappedRegistry;

public class DimensionImpl {
   public static final Logger LOGGER = LoggerFactory.getLogger(DimensionImpl.class);
   public static final HashSet<String> STABLE_NAMESPACES = new HashSet<>();
   public static boolean suppressExperimentalWarning = false;

   public static void directlyRegisterLevelStem(MinecraftServer server, Identifier dimensionId, DimensionOptions levelStem) {
      Immutable registryAccess = server.getRegistryManager();
      SaveProperties worldData = server.getSaveProperties();
      GeneratorOptions worldOptions = worldData.getGeneratorOptions();
      SimpleRegistry<DimensionOptions> levelStems = (SimpleRegistry<DimensionOptions>)registryAccess.getOrThrow(RegistryKeys.DIMENSION);
      if (!levelStems.containsId(dimensionId)) {
         boolean oldIsFrozen = ((IMappedRegistry)levelStems).dimlib_getIsFrozen();
         ((IMappedRegistry)levelStems).dimlib_setIsFrozen(false);

         try {
            levelStems.add(RegistryKey.of(RegistryKeys.DIMENSION, dimensionId), levelStem, RegistryEntryInfo.DEFAULT);
         } finally {
            ((IMappedRegistry)levelStems).dimlib_setIsFrozen(oldIsFrozen);
         }
      } else {
         LOGGER.error("The dimension {} already exists", dimensionId, new Throwable());
      }
   }

   public static SimpleRegistry<DimensionOptions> getDimensionRegistry(MinecraftServer server) {
      return (SimpleRegistry<DimensionOptions>)server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION);
   }
}
