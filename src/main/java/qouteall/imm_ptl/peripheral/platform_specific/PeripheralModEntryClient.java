package qouteall.imm_ptl.peripheral.platform_specific;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;
import net.minecraft.client.render.BlockRenderLayer;
import qouteall.imm_ptl.peripheral.PeripheralModMain;

public class PeripheralModEntryClient implements ClientModInitializer {
   public static void registerBlockRenderLayers() {
      BlockRenderLayerMap.putBlock(PeripheralModMain.portalHelperBlock, BlockRenderLayer.field_60925);
   }

   public void onInitializeClient() {
      registerBlockRenderLayers();
      PeripheralModMain.initClient();
   }
}
