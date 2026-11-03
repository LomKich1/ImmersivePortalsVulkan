package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureSet;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.collection.Pool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.World;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.GenerationSettings;
import net.minecraft.world.biome.SpawnSettings.SpawnEntry;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.chunk.placement.StructurePlacementCalculator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.structure.Structure;
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

   public void carve(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk) {
      this.delegate.carve(chunkRegion, seed, noiseConfig, biomeAccess, structureAccessor, chunk);
   }

   public void buildSurface(ChunkRegion region, StructureAccessor structures, NoiseConfig noiseConfig, Chunk chunk) {
      this.delegate.buildSurface(region, structures, noiseConfig, chunk);
   }

   public void populateEntities(ChunkRegion region) {
      this.delegate.populateEntities(region);
   }

   public int getWorldHeight() {
      return this.delegate.getWorldHeight();
   }

   @NotNull
   public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
      return this.delegate.populateNoise(blender, noiseConfig, structureAccessor, chunk);
   }

   public int getSeaLevel() {
      return this.delegate.getSeaLevel();
   }

   public int getMinimumY() {
      return this.delegate.getMinimumY();
   }

   public int getHeight(int x, int z, Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
      return this.delegate.getHeight(x, z, heightmap, world, noiseConfig);
   }

   public VerticalBlockSample getColumnSample(int x, int z, HeightLimitView world, NoiseConfig noiseConfig) {
      return this.delegate.getColumnSample(x, z, world, noiseConfig);
   }

   public void appendDebugHudText(List<String> text, NoiseConfig noiseConfig, BlockPos pos) {
      this.delegate.appendDebugHudText(text, noiseConfig, pos);
   }

   public StructurePlacementCalculator createStructurePlacementCalculator(
      RegistryWrapper<StructureSet> structureSetRegistry, NoiseConfig noiseConfig, long seed
   ) {
      return super.createStructurePlacementCalculator(structureSetRegistry, noiseConfig, seed);
   }

   public CompletableFuture<Chunk> populateBiomes(NoiseConfig noiseConfig, Blender blender, StructureAccessor structureAccessor, Chunk chunk) {
      return super.populateBiomes(noiseConfig, blender, structureAccessor, chunk);
   }

   public void setStructureStarts(
      DynamicRegistryManager registryManager,
      StructurePlacementCalculator placementCalculator,
      StructureAccessor structureAccessor,
      Chunk chunk,
      StructureTemplateManager structureTemplateManager,
      RegistryKey<World> dimension
   ) {
      super.setStructureStarts(registryManager, placementCalculator, structureAccessor, chunk, structureTemplateManager, dimension);
   }

   public void generateFeatures(StructureWorldAccess world, Chunk chunk, StructureAccessor structureAccessor) {
      this.delegate.generateFeatures(world, chunk, structureAccessor);
   }

   public Optional<RegistryKey<MapCodec<? extends ChunkGenerator>>> getCodecKey() {
      return this.delegate.getCodecKey();
   }

   @Nullable
   public Pair<BlockPos, RegistryEntry<Structure>> locateStructure(
      ServerWorld world, RegistryEntryList<Structure> structures, BlockPos center, int radius, boolean skipReferencedStructures
   ) {
      return this.delegate.locateStructure(world, structures, center, radius, skipReferencedStructures);
   }

   public int getSpawnHeight(HeightLimitView world) {
      return this.delegate.getSpawnHeight(world);
   }

   public BiomeSource getBiomeSource() {
      return this.delegate.getBiomeSource();
   }

   public Pool<SpawnEntry> getEntitySpawnList(RegistryEntry<Biome> biome, StructureAccessor accessor, SpawnGroup group, BlockPos pos) {
      return this.delegate.getEntitySpawnList(biome, accessor, group, pos);
   }

   public void addStructureReferences(StructureWorldAccess world, StructureAccessor structureAccessor, Chunk chunk) {
      this.delegate.addStructureReferences(world, structureAccessor, chunk);
   }

   public int getHeightOnGround(int x, int z, Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
      return this.delegate.getHeightOnGround(x, z, heightmap, world, noiseConfig);
   }

   public int getHeightInGround(int x, int z, Type heightmap, HeightLimitView world, NoiseConfig noiseConfig) {
      return this.delegate.getHeightInGround(x, z, heightmap, world, noiseConfig);
   }

   public GenerationSettings getGenerationSettings(RegistryEntry<Biome> biomeEntry) {
      return this.delegate.getGenerationSettings(biomeEntry);
   }
}
