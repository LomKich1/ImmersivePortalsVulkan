package qouteall.imm_ptl.core.render;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public class StencilDepthTextures {
   public static boolean creatingStencilDepth = false;
   private static final IntSet STENCIL_TEXTURE_IDS = new IntOpenHashSet();

   public static void register(int glTextureId) {
      STENCIL_TEXTURE_IDS.add(glTextureId);
   }

   public static void unregister(int glTextureId) {
      STENCIL_TEXTURE_IDS.remove(glTextureId);
   }

   public static boolean isStencilTexture(int glTextureId) {
      return glTextureId != 0 && STENCIL_TEXTURE_IDS.contains(glTextureId);
   }
}
