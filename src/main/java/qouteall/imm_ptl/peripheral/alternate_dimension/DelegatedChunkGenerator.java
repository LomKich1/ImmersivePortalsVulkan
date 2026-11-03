package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.ChunkRegion;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.HeightLimitView;
import net.minecraft.util.collection.WeightedPool;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.structure.StructureSet;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.Heightmap.class_2903;
import net.minecraft.world.biome.SpawnSettings.class_1964;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@IPVanillaCopy
public abstract class DelegatedChunkGenerator extends ChunkGenerator {
   protected ChunkGenerator delegate;
   protected BiomeSource biomeSource_;

   public DelegatedChunkGenerator(BiomeSource biomeSource, ChunkGenerator delegate) {
      super(biomeSource);
      this.delegate = delegate;
      this.biomeSource_ = biomeSource;
   }

   public void carve(
      ChunkRegion worldGenRegion, long l, NoiseConfig randomState, BiomeAccess biomeManager, StructureAccessor structureManager, Chunk chunkAccess
   ) {
      this.delegate.carve(worldGenRegion, l, randomState, biomeManager, structureManager, chunkAccess);
   }

   public void buildSurface(ChunkRegion worldGenRegion, StructureAccessor structureManager, NoiseConfig randomState, Chunk chunkAccess) {
      this.delegate.buildSurface(worldGenRegion, structureManager, randomState, chunkAccess);
   }

   public void populateEntities(ChunkRegion region) {
      this.delegate.populateEntities(region);
   }

   public int getWorldHeight() {
      return this.delegate.getWorldHeight();
   }

   @NotNull
   public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig randomState, StructureAccessor structureManager, Chunk chunkAccess) {
      return this.delegate.populateNoise(blender, randomState, structureManager, chunkAccess);
   }

   public int getSeaLevel() {
      return this.delegate.getSeaLevel();
   }

   public int getMinimumY() {
      return this.delegate.getMinimumY();
   }

   public int getHeight(int i, int j, class_2903 types, HeightLimitView levelHeightAccessor, NoiseConfig randomState) {
      return this.delegate.getHeight(i, j, types, levelHeightAccessor, randomState);
   }

   public VerticalBlockSample getColumnSample(int i, int j, HeightLimitView levelHeightAccessor, NoiseConfig randomState) {
      return this.delegate.getColumnSample(i, j, levelHeightAccessor, randomState);
   }

   public void appendDebugHudText(List<String> list, NoiseConfig randomState, BlockPos blockPos) {
      this.delegate.appendDebugHudText(list, randomState, blockPos);
   }

   public StructurePlacementCalculator createStructurePlacementCalculator(RegistryWrapper<StructureSet> holderLookup, NoiseConfig randomState, long l) {
      return super.createStructurePlacementCalculator(holderLookup, randomState, l);
   }

   public CompletableFuture<Chunk> populateBiomes(NoiseConfig randomState, Blender blender, StructureAccessor structureManager, Chunk chunkAccess) {
      return super.populateBiomes(randomState, blender, structureManager, chunkAccess);
   }

   public void setStructureStarts(
      DynamicRegistryManager registryAccess,
      StructurePlacementCalculator chunkGeneratorStructureState,
      StructureAccessor structureManager,
      Chunk chunkAccess,
      StructureTemplateManager structureTemplateManager,
      RegistryKey<World> dimension
   ) {
      super.setStructureStarts(registryAccess, chunkGeneratorStructureState, structureManager, chunkAccess, structureTemplateManager, dimension);
   }

   public void generateFeatures(StructureWorldAccess worldGenLevel, Chunk chunkAccess, StructureAccessor structureManager) {
      this.delegate.generateFeatures(worldGenLevel, chunkAccess, structureManager);
   }

   public Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> getCodecKey() {
      return this.delegate.getCodecKey();
   }

   @Nullable
   public Pair<BlockPos, RegistryEntry<Structure>> locateStructure(
      ServerWorld serverLevel, RegistryEntryList<Structure> holderSet, BlockPos blockPos, int i, boolean bl
   ) {
      return this.delegate.locateStructure(serverLevel, holderSet, blockPos, i, bl);
   }

   public int getSpawnHeight(HeightLimitView level) {
      return this.delegate.getSpawnHeight(level);
   }

   public BiomeSource getBiomeSource() {
      return this.delegate.getBiomeSource();
   }

   public WeightedPool<class_1964> getEntitySpawnList(RegistryEntry<Biome> holder, StructureAccessor structureManager, SpawnGroup mobCategory, BlockPos blockPos) {
      return this.delegate.getEntitySpawnList(holder, structureManager, mobCategory, blockPos);
   }

   public void addStructureReferences(StructureWorldAccess worldGenLevel, StructureAccessor structureManager, Chunk chunkAccess) {
      this.delegate.addStructureReferences(worldGenLevel, structureManager, chunkAccess);
   }

   public int getHeightOnGround(int i, int j, class_2903 types, HeightLimitView levelHeightAccessor, NoiseConfig randomState) {
      return this.delegate.getHeightOnGround(i, j, types, levelHeightAccessor, randomState);
   }

   public int getHeightInGround(int i, int j, class_2903 types, HeightLimitView levelHeightAccessor, NoiseConfig randomState) {
      return this.delegate.getHeightInGround(i, j, types, levelHeightAccessor, randomState);
   }

   public GenerationSettings getGenerationSettings(RegistryEntry<Biome> holder) {
      return this.delegate.getGenerationSettings(holder);
   }
}
