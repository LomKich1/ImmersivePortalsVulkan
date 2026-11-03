package qouteall.dimlib;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.DynamicRegistryManager.Immutable;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.entry.RegistryEntry.Reference;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.dimension.DimensionTypes;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;

public record DimensionTemplate(RegistryKey<DimensionType> dimensionTypeId, DimensionTemplate.DimensionFactory dimensionFactory) {
   static final Map<String, DimensionTemplate> DIMENSION_TEMPLATES = new LinkedHashMap<>();
   public static final DimensionTemplate VOID_TEMPLATE = new DimensionTemplate(
      DimensionTypes.OVERWORLD,
      (server, dimTypeHolder) -> {
         Immutable registryAccess = server.getRegistryManager();
         Registry<Biome> biomeRegistry = registryAccess.getOrThrow(RegistryKeys.BIOME);
         Reference<Biome> plainsHolder = biomeRegistry.getOrThrow(BiomeKeys.PLAINS);
         FlatChunkGeneratorConfig flatChunkGeneratorConfig = new FlatChunkGeneratorConfig(
            Optional.of(RegistryEntryList.of(new RegistryEntry[0])), plainsHolder, List.of()
         );
         flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.AIR));
         flatChunkGeneratorConfig.updateLayerBlocks();
         FlatChunkGenerator chunkGenerator = new FlatChunkGenerator(flatChunkGeneratorConfig);
         return new DimensionOptions(dimTypeHolder, chunkGenerator);
      }
   );

   public static void registerDimensionTemplate(String name, DimensionTemplate dimensionTemplate) {
      DIMENSION_TEMPLATES.put(name, dimensionTemplate);
   }

   public DimensionOptions createLevelStem(MinecraftServer server) {
      Registry<DimensionType> dimensionTypes = server.getRegistryManager().getOrThrow(RegistryKeys.DIMENSION_TYPE);
      Reference<DimensionType> holder = dimensionTypes.getOrThrow(this.dimensionTypeId);
      return this.dimensionFactory.createLevelStem(server, holder);
   }

   public static void init() {
      registerDimensionTemplate("void", VOID_TEMPLATE);
   }

   public interface DimensionFactory {
      DimensionOptions createLevelStem(MinecraftServer var1, RegistryEntry<DimensionType> var2);
   }
}
