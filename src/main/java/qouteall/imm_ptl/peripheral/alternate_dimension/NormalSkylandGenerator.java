package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.common.base.Suppliers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.RegistryOps;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.biome.source.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler.class_5487;
import net.minecraft.registry.entry.RegistryEntry.class_6883;
import net.minecraft.registry.entry.RegistryEntryList.class_6888;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.peripheral.ducks.IEChunkGenerator_AlternateDim;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IEChunkAccess_AlternateDim;
import qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension.IENoiseRouterData;

public class NormalSkylandGenerator extends NoiseChunkGenerator {
   public static final MapCodec<NormalSkylandGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME),
            RegistryOps.getEntryLookupCodec(RegistryKeys.DENSITY_FUNCTION),
            RegistryOps.getEntryLookupCodec(RegistryKeys.NOISE_PARAMETERS),
            RegistryOps.getEntryLookupCodec(RegistryKeys.CHUNK_GENERATOR_SETTINGS),
            RegistryOps.getEntryLookupCodec(RegistryKeys.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST),
            Codec.LONG.optionalFieldOf("seed", 0L).forGetter(g -> g.seed)
         )
         .apply(instance, NormalSkylandGenerator::create)
   );
   private NoiseConfig delegatedRandomState;
   private final RegistryEntryLookup<Biome> biomeHolderGetter;
   private final RegistryEntryLookup<DensityFunction> densityFunctionHolderGetter;
   private final RegistryEntryLookup<class_5487> noiseParametersHolderGetter;
   private final long seed;
   private final NoiseChunkGenerator delegate;

   public NormalSkylandGenerator(
      BiomeSource biomeSource,
      RegistryEntry<ChunkGeneratorSettings> noiseGeneratorSettings,
      NoiseChunkGenerator delegate,
      RegistryEntryLookup<Biome> biomeHolderGetter,
      RegistryEntryLookup<DensityFunction> densityFunctionHolderGetter,
      RegistryEntryLookup<class_5487> noiseParametersHolderGetter,
      long seed
   ) {
      super(biomeSource, noiseGeneratorSettings);
      this.delegate = delegate;
      this.biomeHolderGetter = biomeHolderGetter;
      this.densityFunctionHolderGetter = densityFunctionHolderGetter;
      this.noiseParametersHolderGetter = noiseParametersHolderGetter;
      this.seed = seed;
      this.delegatedRandomState = NoiseConfig.create((ChunkGeneratorSettings)delegate.getSettings().comp_349(), noiseParametersHolderGetter, seed);
   }

   public static NormalSkylandGenerator create(
      RegistryEntryLookup<Biome> biomeHolderGetter,
      RegistryEntryLookup<DensityFunction> densityFunctionHolderGetter,
      RegistryEntryLookup<class_5487> noiseParametersHolderGetter,
      RegistryEntryLookup<ChunkGeneratorSettings> noiseGeneratorSettingsHolderGetter,
      RegistryEntryLookup<MultiNoiseBiomeSourceParameterList> biomeParamListLookup,
      long seed
   ) {
      class_6883<MultiNoiseBiomeSourceParameterList> overworldBiomeParamList = biomeParamListLookup.getOrThrow(MultiNoiseBiomeSourceParameterLists.field_42992);
      MultiNoiseBiomeSource overworldBiomeSource = MultiNoiseBiomeSource.create(overworldBiomeParamList);
      ChunkGeneratorSettings overworldNGS = (ChunkGeneratorSettings)noiseGeneratorSettingsHolderGetter.getOrThrow(ChunkGeneratorSettings.OVERWORLD).comp_349();
      ChunkGeneratorSettings intrinsicSkylandNGS = (ChunkGeneratorSettings)noiseGeneratorSettingsHolderGetter.getOrThrow(ChunkGeneratorSettings.FLOATING_ISLANDS).comp_349();
      ChunkGeneratorSettings usedSkylandNGS = new ChunkGeneratorSettings(
         intrinsicSkylandNGS.comp_474(),
         intrinsicSkylandNGS.comp_475(),
         intrinsicSkylandNGS.comp_476(),
         IENoiseRouterData.ip_noNewCaves(
            densityFunctionHolderGetter,
            noiseParametersHolderGetter,
            IENoiseRouterData.ip_slideEndLike(IENoiseRouterData.ip_getFunction(densityFunctionHolderGetter, IENoiseRouterData.get_BASE_3D_NOISE_END()), 0, 128)
         ),
         intrinsicSkylandNGS.comp_478(),
         intrinsicSkylandNGS.comp_538(),
         0,
         intrinsicSkylandNGS.comp_480(),
         intrinsicSkylandNGS.comp_481(),
         intrinsicSkylandNGS.comp_482(),
         intrinsicSkylandNGS.comp_483()
      );
      NoiseChunkGenerator skylandGenerator = new NoiseChunkGenerator(overworldBiomeSource, RegistryEntry.of(usedSkylandNGS));
      NormalSkylandGenerator result = new NormalSkylandGenerator(
         overworldBiomeSource,
         RegistryEntry.of(overworldNGS),
         skylandGenerator,
         biomeHolderGetter,
         densityFunctionHolderGetter,
         noiseParametersHolderGetter,
         seed
      );
      ((IEChunkGenerator_AlternateDim)result)
         .ip_setFeaturesPerStep(Suppliers.memoize(() -> PlacedFeatureIndexer.collectIndexedFeatures(List.copyOf(overworldBiomeSource.getBiomes()), holder -> {
            Biome biome = (Biome)holder.comp_349();
            GenerationSettings bgs = biome.getGenerationSettings();
            return bgs.getFeatures();
         }, true)));
      return result;
   }

   @NotNull
   protected MapCodec<? extends ChunkGenerator> getCodec() {
      return MAP_CODEC;
   }

   public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig pRandomState, StructureAccessor structureManager, Chunk chunkAccess) {
      ((IEChunkAccess_AlternateDim)chunkAccess).ip_setNoiseChunk(null);
      return this.delegate.populateNoise(blender, this.delegatedRandomState, structureManager, chunkAccess).thenApply(c -> {
         ((IEChunkAccess_AlternateDim)c).ip_setNoiseChunk(null);
         return (Chunk)c;
      });
   }

   public StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> structureSetLookup, NoiseConfig randomState, long seed) {
      RegistryWrapper<StructureSet> structureSetLookupDelegate = new RegistryWrapper<StructureSet>() {
         public Stream<class_6883<StructureSet>> streamEntries() {
            return structureSetLookup.streamEntries().filter(holder -> !holder.registryKey().getValue().getPath().equals("mineshafts"));
         }

         public Stream<class_6888<StructureSet>> getTags() {
            return structureSetLookup.getTags();
         }

         public Optional<class_6883<StructureSet>> getOptional(RegistryKey<StructureSet> resourceKey) {
            return structureSetLookup.getOptional(resourceKey);
         }

         public Optional<class_6888<StructureSet>> getOptional(TagKey<StructureSet> tagKey) {
            return structureSetLookup.getOptional(tagKey);
         }
      };
      return super.createStructurePlacementCalculator(structureSetLookupDelegate, randomState, seed);
   }
}
