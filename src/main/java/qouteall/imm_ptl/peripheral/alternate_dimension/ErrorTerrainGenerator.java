package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.Util;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.BlockPos.Mutable;
import net.minecraft.world.Heightmap;
import net.minecraft.world.Heightmap.Type;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ProtoChunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.jetbrains.annotations.NotNull;
import qouteall.q_misc_util.Helper;

public class ErrorTerrainGenerator extends DelegatedChunkGenerator {
   public static final MapCodec<ErrorTerrainGenerator> MAP_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(RegistryOps.getEntryLookupCodec(RegistryKeys.BIOME), RegistryOps.getEntryLookupCodec(RegistryKeys.CHUNK_GENERATOR_SETTINGS))
         .apply(instance, ErrorTerrainGenerator::create)
   );
   public static final int regionChunkNum = 4;
   public static final int averageY = 64;
   public static final int maxY = 128;
   private final BlockState air = Blocks.AIR.getDefaultState();
   private final BlockState defaultBlock = Blocks.STONE.getDefaultState();
   private final BlockState defaultFluid = Blocks.WATER.getDefaultState();
   private final LoadingCache<ChunkPos, RegionErrorTerrainGenerator> cache = CacheBuilder.newBuilder()
      .maximumSize(10000L)
      .expireAfterWrite(30L, TimeUnit.SECONDS)
      .build(new CacheLoader<ChunkPos, RegionErrorTerrainGenerator>() {
         public RegionErrorTerrainGenerator load(ChunkPos key) {
            return new RegionErrorTerrainGenerator(key.x, key.z, System.nanoTime());
         }
      });

   public static ErrorTerrainGenerator create(
      RegistryEntryLookup<Biome> biomeHolderGetter, RegistryEntryLookup<ChunkGeneratorSettings> noiseGeneratorSettingsHolderGetter
   ) {
      ChaosBiomeSource chaosBiomeSource = ChaosBiomeSource.createChaosBiomeSource(biomeHolderGetter);
      ChunkGeneratorSettings skylandSetting = (ChunkGeneratorSettings)noiseGeneratorSettingsHolderGetter.getOrThrow(ChunkGeneratorSettings.FLOATING_ISLANDS)
         .value();
      NoiseChunkGenerator islandChunkGenerator = new NoiseChunkGenerator(chaosBiomeSource, RegistryEntry.of(skylandSetting));
      return new ErrorTerrainGenerator(chaosBiomeSource, islandChunkGenerator);
   }

   public ErrorTerrainGenerator(BiomeSource biomeSource, ChunkGenerator delegate) {
      super(biomeSource, delegate);
   }

   @NotNull
   protected MapCodec<? extends ChunkGenerator> getCodec() {
      return MAP_CODEC;
   }

   @NotNull
   @Override
   public CompletableFuture<Chunk> populateNoise(Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk) {
      ChunkSection[] sectionArray = chunk.getSectionArray();
      ArrayList<ChunkSection> locked = new ArrayList<>();

      for (ChunkSection chunkSection : sectionArray) {
         if (chunkSection != null) {
            chunkSection.lock();
            locked.add(chunkSection);
         }
      }

      return CompletableFuture.<Chunk>supplyAsync(() -> {
         this.doPopulateNoise(chunk);
         return chunk;
      }, Util.getMainWorkerExecutor()).thenApplyAsync(chunkx -> {
         for (ChunkSection chunkSectionx : locked) {
            chunkSectionx.unlock();
         }

         return (Chunk)chunkx;
      }, Util.getMainWorkerExecutor());
   }

   public void doPopulateNoise(Chunk chunk) {
      ProtoChunk protoChunk = (ProtoChunk)chunk;
      ChunkPos pos = chunk.getPos();
      Heightmap oceanFloorHeightMap = protoChunk.getHeightmap(Type.OCEAN_FLOOR_WG);
      Heightmap surfaceHeightMap = protoChunk.getHeightmap(Type.WORLD_SURFACE_WG);
      new Mutable();
      int regionX = Math.floorDiv(pos.x, 4);
      int regionZ = Math.floorDiv(pos.z, 4);
      RegionErrorTerrainGenerator generator = Helper.noError(() -> (RegionErrorTerrainGenerator)this.cache.get(new ChunkPos(regionX, regionZ)));

      for (int sectionY = 0; sectionY < 16; sectionY++) {
         ChunkSection section = protoChunk.getSection(sectionY);

         for (int localX = 0; localX < 16; localX++) {
            for (int localZ = 0; localZ < 16; localZ++) {
               for (int localY = 0; localY < 16; localY++) {
                  int worldX = pos.x * 16 + localX;
                  int worldY = sectionY * 16 + localY;
                  int worldZ = pos.z * 16 + localZ;
                  BlockState currBlockState = generator.getBlockComposition(worldX, worldY, worldZ);
                  if (currBlockState != this.air) {
                     section.setBlockState(localX, localY, localZ, currBlockState, false);
                     oceanFloorHeightMap.trackUpdate(localX, worldY, localZ, currBlockState);
                     surfaceHeightMap.trackUpdate(localX, worldY, localZ, currBlockState);
                  }
               }
            }
         }
      }
   }
}
