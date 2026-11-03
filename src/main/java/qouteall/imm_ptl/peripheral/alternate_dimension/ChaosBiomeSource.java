package qouteall.imm_ptl.peripheral.alternate_dimension;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.SeedMixer;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.entry.RegistryEntryList;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil.class_6552;
import org.jetbrains.annotations.NotNull;
import qouteall.imm_ptl.core.McHelper;

public class ChaosBiomeSource extends BiomeSource {
   public static final String[] vanillaBiomes = new String[]{
      "minecraft:savanna_plateau",
      "minecraft:taiga",
      "minecraft:savanna",
      "minecraft:dripstone_caves",
      "minecraft:swamp",
      "minecraft:basalt_deltas",
      "minecraft:ice_spikes",
      "minecraft:crimson_forest",
      "minecraft:frozen_peaks",
      "minecraft:dark_forest",
      "minecraft:lush_caves",
      "minecraft:old_growth_spruce_taiga",
      "minecraft:deep_dark",
      "minecraft:frozen_river",
      "minecraft:lukewarm_ocean",
      "minecraft:mushroom_fields",
      "minecraft:warm_ocean",
      "minecraft:forest",
      "minecraft:end_midlands",
      "minecraft:windswept_forest",
      "minecraft:deep_ocean",
      "minecraft:sunflower_plains",
      "minecraft:stony_peaks",
      "minecraft:stony_shore",
      "minecraft:nether_wastes",
      "minecraft:deep_lukewarm_ocean",
      "minecraft:flower_forest",
      "minecraft:old_growth_birch_forest",
      "minecraft:desert",
      "minecraft:snowy_taiga",
      "minecraft:beach",
      "minecraft:grove",
      "minecraft:deep_frozen_ocean",
      "minecraft:river",
      "minecraft:old_growth_pine_taiga",
      "minecraft:the_void",
      "minecraft:deep_cold_ocean",
      "minecraft:windswept_gravelly_hills",
      "minecraft:snowy_plains",
      "minecraft:end_highlands",
      "minecraft:jagged_peaks",
      "minecraft:eroded_badlands",
      "minecraft:bamboo_jungle",
      "minecraft:end_barrens",
      "minecraft:plains",
      "minecraft:small_end_islands",
      "minecraft:meadow",
      "minecraft:the_end",
      "minecraft:snowy_beach",
      "minecraft:sparse_jungle",
      "minecraft:jungle",
      "minecraft:snowy_slopes",
      "minecraft:birch_forest",
      "minecraft:mangrove_swamp",
      "minecraft:ocean",
      "minecraft:cold_ocean",
      "minecraft:soul_sand_valley",
      "minecraft:warped_forest",
      "minecraft:badlands",
      "minecraft:windswept_hills",
      "minecraft:windswept_savanna",
      "minecraft:wooded_badlands"
   };
   public static final MapCodec<ChaosBiomeSource> MAP_CODEC = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Biome.REGISTRY_ENTRY_LIST_CODEC.fieldOf("biomes").forGetter(checkerboardColumnBiomeSource -> checkerboardColumnBiomeSource.allowedBiomes)
         )
         .apply(instance, ChaosBiomeSource::new)
   );
   private final RegistryEntryList<Biome> allowedBiomes;

   public ChaosBiomeSource(RegistryEntryList<Biome> holderSet) {
      this.allowedBiomes = holderSet;
   }

   @NotNull
   public static ChaosBiomeSource createChaosBiomeSource(RegistryEntryLookup<Biome> biomeHolderGetter) {
      List<RegistryEntry<Biome>> holders = new ArrayList<>();

      for (String vanillaBiomeId : vanillaBiomes) {
         biomeHolderGetter.getOptional(RegistryKey.of(RegistryKeys.BIOME, McHelper.newResourceLocation(vanillaBiomeId))).ifPresent(holders::add);
      }

      return new ChaosBiomeSource(RegistryEntryList.of(holders));
   }

   private RegistryEntry<Biome> getRandomBiome(int x, int z) {
      int biomeNum = this.allowedBiomes.size();
      int index = Math.abs((int)SeedMixer.mixSeed(x / 5, z / 5)) % biomeNum;
      return this.allowedBiomes.get(index);
   }

   protected MapCodec<? extends BiomeSource> getCodec() {
      return MAP_CODEC;
   }

   protected Stream<RegistryEntry<Biome>> biomeStream() {
      return this.allowedBiomes.stream();
   }

   public RegistryEntry<Biome> getBiome(int x, int y, int z, class_6552 sampler) {
      return this.getRandomBiome(x, z);
   }
}
