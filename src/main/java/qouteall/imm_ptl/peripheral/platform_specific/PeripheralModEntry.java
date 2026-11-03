package qouteall.imm_ptl.peripheral.platform_specific;

import net.fabricmc.api.ModInitializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

public class PeripheralModEntry implements ModInitializer {
   public void onInitialize() {
      PeripheralModMain.registerBlocks((id, ele) -> Registry.register(Registries.BLOCK, id, ele));
      PeripheralModMain.registerItems((id, ele) -> Registry.register(Registries.ITEM, id, ele));
      PeripheralModMain.registerChunkGenerators((id, ele) -> Registry.register(Registries.CHUNK_GENERATOR, id, ele));
      PeripheralModMain.registerBiomeSources((id, ele) -> Registry.register(Registries.BIOME_SOURCE, id, ele));
      PeripheralModMain.registerCreativeTabs((id, ele) -> Registry.register(Registries.ITEM_GROUP, id, ele));
      PeripheralModMain.init();
   }
}
