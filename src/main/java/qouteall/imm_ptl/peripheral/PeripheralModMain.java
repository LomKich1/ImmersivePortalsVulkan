package qouteall.imm_ptl.peripheral;

import com.mojang.serialization.MapCodec;
import java.util.function.BiConsumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.AbstractBlock.Settings;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.peripheral.alternate_dimension.AlternateDimensions;
import qouteall.imm_ptl.peripheral.alternate_dimension.ChaosBiomeSource;
import qouteall.imm_ptl.peripheral.alternate_dimension.ErrorTerrainGenerator;
import qouteall.imm_ptl.peripheral.alternate_dimension.FormulaGenerator;
import qouteall.imm_ptl.peripheral.alternate_dimension.NormalSkylandGenerator;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;
import qouteall.imm_ptl.peripheral.portal_generation.IntrinsicPortalGeneration;
import qouteall.imm_ptl.peripheral.wand.ClientPortalWandPortalDrag;
import qouteall.imm_ptl.peripheral.wand.PortalWandInteraction;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;
import qouteall.q_misc_util.platform.Platform;

public class PeripheralModMain {
   public static final Block portalHelperBlock = new Block(
      Settings.create()
         .nonOpaque()
         .solidBlock((a, b, c) -> false)
         .registryKey(RegistryKey.of(RegistryKeys.BLOCK, McHelper.newResourceLocation("immersive_portals", "portal_helper")))
   );
   public static final BlockItem portalHelperBlockItem = new PortalHelperItem(
      portalHelperBlock,
      new net.minecraft.item.Item.Settings()
         .registryKey(RegistryKey.of(RegistryKeys.ITEM, McHelper.newResourceLocation("immersive_portals", "portal_helper")))
         .useBlockPrefixedTranslationKey()
   );
   public static final ItemGroup TAB = Platform.get()
      .createCreativeTabBuilder()
      .icon(() -> new ItemStack(PortalWandItem.instance))
      .displayName(Text.translatable("imm_ptl.item_group"))
      .entries((enabledFeatures, entries) -> {
         PortalWandItem.addIntoCreativeTag(entries);
         CommandStickItem.addIntoCreativeTag(entries);
         entries.add(portalHelperBlockItem);
      })
      .build();

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      IPOuterClientMisc.initClient();
      PortalWandItem.initClient();
      ClientPortalWandPortalDrag.init();
   }

   public static void init() {
      FormulaGenerator.init();
      IntrinsicPortalGeneration.init();
      DimStackManagement.init();
      AlternateDimensions.init();
      DimensionAPI.suppressExperimentalWarningForNamespace("immersive_portals");
      PortalWandItem.init();
      CommandStickItem.init();
      PortalWandInteraction.init();
      CommandStickItem.registerCommandStickTypes();
   }

   public static void registerItems(BiConsumer<Identifier, Item> regFunc) {
      regFunc.accept(McHelper.newResourceLocation("immersive_portals", "portal_helper"), portalHelperBlockItem);
      regFunc.accept(McHelper.newResourceLocation("immersive_portals:command_stick"), CommandStickItem.instance);
      regFunc.accept(McHelper.newResourceLocation("immersive_portals:portal_wand"), PortalWandItem.instance);
   }

   public static void registerBlocks(BiConsumer<Identifier, Block> regFunc) {
      regFunc.accept(McHelper.newResourceLocation("immersive_portals", "portal_helper"), portalHelperBlock);
   }

   public static void registerChunkGenerators(BiConsumer<Identifier, MapCodec<? extends ChunkGenerator>> regFunc) {
      regFunc.accept(McHelper.newResourceLocation("immersive_portals:error_terrain_generator"), ErrorTerrainGenerator.MAP_CODEC);
      regFunc.accept(McHelper.newResourceLocation("immersive_portals:normal_skyland_generator"), NormalSkylandGenerator.MAP_CODEC);
   }

   public static void registerBiomeSources(BiConsumer<Identifier, MapCodec<? extends BiomeSource>> regFunc) {
      regFunc.accept(McHelper.newResourceLocation("immersive_portals:chaos_biome_source"), ChaosBiomeSource.MAP_CODEC);
   }

   public static void registerCreativeTabs(BiConsumer<Identifier, ItemGroup> regFunc) {
      regFunc.accept(McHelper.newResourceLocation("immersive_portals", "general"), TAB);
   }
}
