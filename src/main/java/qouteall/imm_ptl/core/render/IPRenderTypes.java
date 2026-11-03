package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.RenderLayer;

@Environment(EnvType.CLIENT)
public class IPRenderTypes {
   private static final RenderLayer DEBUG_LINE_STRIP = RenderLayer.of(
      "imm_ptl_debug_line_strip", RenderSetup.builder(IPRenderPipelines.DEBUG_LINE_STRIP).build()
   );

   public static RenderLayer debugLineStrip() {
      return DEBUG_LINE_STRIP;
   }
}
