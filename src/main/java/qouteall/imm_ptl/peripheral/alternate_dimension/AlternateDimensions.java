package qouteall.imm_ptl.peripheral.alternate_dimension;

import java.util.List;
import java.util.Optional;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorLayer;
import net.minecraft.world.gen.chunk.FlatChunkGeneratorConfig;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry.class_6883;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import qouteall.dimlib.DimensionTemplate;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.ducks.IEWorld;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimensionStackAPI;
import qouteall.q_misc_util.platform.Platform;

public class AlternateDimensions {
   public static final RegistryKey<DimensionType> SURFACE_TYPE = RegistryKey.of(
      RegistryKeys.field_41241, McHelper.newResourceLocation("immersive_portals:surface_type")
   );
   public static final RegistryKey<DimensionType> SURFACE_TYPE_BRIGHT = RegistryKey.of(
      RegistryKeys.field_41241, McHelper.newResourceLocation("immersive_portals:surface_type_bright")
   );
   public static final RegistryKey<World> SKYLAND = RegistryKey.of(
      RegistryKeys.WORLD, McHelper.newResourceLocation("immersive_portals:skyland")
   );
   public static final RegistryKey<World> BRIGHT_SKYLAND = RegistryKey.of(
      RegistryKeys.WORLD, McHelper.newResourceLocation("immersive_portals:bright_skyland")
   );
   public static final RegistryKey<World> CHAOS = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation("immersive_portals:chaos"));
   public static final RegistryKey<World> VOID = RegistryKey.of(RegistryKeys.WORLD, McHelper.newResourceLocation("immersive_portals:void"));
   public static final RegistryKey<World> BRIGHT_VOID = RegistryKey.of(
      RegistryKeys.WORLD, McHelper.newResourceLocation("immersive_portals:bright_void")
   );
   public static final DimensionTemplate SKYLAND_TEMPLATE = new DimensionTemplate(
      SURFACE_TYPE,
      (server, dimensionTypeHolder) -> new DimensionOptions(
         dimensionTypeHolder, createSkylandGenerator(server.getRegistryManager(), server.getSaveProperties().getGeneratorOptions().getSeed())
      )
   );
   public static final DimensionTemplate BRIGHT_SKYLAND_TEMPLATE = new DimensionTemplate(
      SURFACE_TYPE_BRIGHT,
      (server, dimensionTypeHolder) -> new DimensionOptions(
         dimensionTypeHolder, createSkylandGenerator(server.getRegistryManager(), server.getSaveProperties().getGeneratorOptions().getSeed())
      )
   );
   public static final DimensionTemplate CHAOS_TEMPLATE = new DimensionTemplate(
      SURFACE_TYPE,
      (server, dimensionTypeHolder) -> new DimensionOptions(
         dimensionTypeHolder, createErrorTerrainGenerator(server.getSaveProperties().getGeneratorOptions().getSeed(), server.getRegistryManager())
      )
   );
   public static final DimensionTemplate BRIGHT_VOID_TEMPLATE = new DimensionTemplate(
      SURFACE_TYPE_BRIGHT, (server, dimensionTypeHolder) -> new DimensionOptions(dimensionTypeHolder, createVoidGenerator(server.getRegistryManager()))
   );

   public static void init() {
      DimensionStackAPI.DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT.register((registryAccess, options) -> List.of(BRIGHT_SKYLAND, SKYLAND, CHAOS, VOID));
      DimensionStackAPI.DIMENSION_STACK_PRE_UPDATE_EVENT.register(AlternateDimensions::addAltDimsIfUsedInDimStack);
      Platform.get().onServerTickEnd(AlternateDimensions::tick);
      DimensionTemplate.registerDimensionTemplate("skyland", SKYLAND_TEMPLATE);
      DimensionTemplate.registerDimensionTemplate("bright_skyland", BRIGHT_SKYLAND_TEMPLATE);
      DimensionTemplate.registerDimensionTemplate("chaos", CHAOS_TEMPLATE);
      DimensionTemplate.registerDimensionTemplate("bright_void", BRIGHT_VOID_TEMPLATE);
   }

   private static void addAltDimsIfUsedInDimStack(MinecraftServer server, @Nullable DimStackInfo dimStackInfo) {
      if (dimStackInfo != null) {
         if (dimStackInfo.hasDimension(BRIGHT_SKYLAND)) {
            DimensionAPI.addDimensionIfNotExists(server, BRIGHT_SKYLAND.getValue(), () -> BRIGHT_SKYLAND_TEMPLATE.createLevelStem(server));
         }

         if (dimStackInfo.hasDimension(SKYLAND)) {
            DimensionAPI.addDimensionIfNotExists(server, SKYLAND.getValue(), () -> SKYLAND_TEMPLATE.createLevelStem(server));
         }

         if (dimStackInfo.hasDimension(CHAOS)) {
            DimensionAPI.addDimensionIfNotExists(server, CHAOS.getValue(), () -> CHAOS_TEMPLATE.createLevelStem(server));
         }

         if (dimStackInfo.hasDimension(VOID)) {
            DimensionAPI.addDimensionIfNotExists(server, VOID.getValue(), () -> DimensionTemplate.VOID_TEMPLATE.createLevelStem(server));
         }

         if (dimStackInfo.hasDimension(BRIGHT_VOID)) {
            DimensionAPI.addDimensionIfNotExists(server, BRIGHT_VOID.getValue(), () -> BRIGHT_VOID_TEMPLATE.createLevelStem(server));
         }
      }
   }

   public static boolean isAlternateDimension(World world) {
      RegistryKey<DimensionType> dimensionTypeId = (RegistryKey<DimensionType>)world.getDimensionEntry().getKey().orElseThrow();
      return dimensionTypeId == SURFACE_TYPE || dimensionTypeId == SURFACE_TYPE_BRIGHT;
   }

   public static ChunkGenerator createSkylandGenerator(DynamicRegistryManager rm, long seed) {
      return NormalSkylandGenerator.create(
         rm.get(RegistryKeys.BIOME),
         rm.get(RegistryKeys.DENSITY_FUNCTION),
         rm.get(RegistryKeys.NOISE_PARAMETERS),
         rm.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS),
         rm.get(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
         seed
      );
   }

   public static ChunkGenerator createErrorTerrainGenerator(long seed, DynamicRegistryManager rm) {
      return ErrorTerrainGenerator.create(rm.get(RegistryKeys.BIOME), rm.get(RegistryKeys.CHUNK_GENERATOR_SETTINGS));
   }

   public static ChunkGenerator createVoidGenerator(DynamicRegistryManager rm) {
      Registry<Biome> biomeRegistry = rm.get(RegistryKeys.BIOME);
      class_6883<Biome> plainsHolder = biomeRegistry.getOrThrow(BiomeKeys.field_9451);
      FlatChunkGeneratorConfig flatChunkGeneratorConfig = new FlatChunkGeneratorConfig(Optional.of(RegistryEntryList.of(new RegistryEntry[0])), plainsHolder, List.of());
      flatChunkGeneratorConfig.getLayers().add(new FlatChunkGeneratorLayer(1, Blocks.field_10124));
      flatChunkGeneratorConfig.updateLayerBlocks();
      return new FlatChunkGenerator(flatChunkGeneratorConfig);
   }

   private static void tick(MinecraftServer server) {
      for (ServerWorld world : server.getWorlds()) {
         if (isAlternateDimension(world)) {
            syncWeatherFromOverworld(world, McHelper.getOverWorldOnServer());
         }
      }
   }

   private static void syncWeatherFromOverworld(ServerWorld world, ServerWorld overworld) {
      ((IEWorld)world).portal_setWeather(overworld.getRainGradient(1.0F), overworld.getRainGradient(1.0F), overworld.getThunderGradient(1.0F), overworld.getThunderGradient(1.0F));
   }
}
