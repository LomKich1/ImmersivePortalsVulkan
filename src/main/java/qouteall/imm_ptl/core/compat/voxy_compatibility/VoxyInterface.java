package qouteall.imm_ptl.core.compat.voxy_compatibility;

import com.mojang.logging.LogUtils;
import java.util.Map.Entry;





import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.world.World;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.render.GuiPortalRendering;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.renderer.RendererUsingStencil;

@Environment(EnvType.CLIENT)
public class VoxyInterface {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final String KEEP_PROPERTY = "immptl.voxy.lodSystemPerDimension";
   public static final long KEEP_MIN_FREE_VRAM = 3221225472L;
   public static VoxyInterface.Invoker invoker = new VoxyInterface.Invoker();
   public static boolean prewarmingNow = false;
   public static boolean creatingNow = false;
   public static boolean skipGcNow = false;
   private static boolean gateBypassed = false;

   public static boolean isSecondaryContext() {
      return ClientWorldLoader.getIsCreatingClientWorld() || ClientWorldLoader.getIsWorldSwitched() || PortalRendering.isRendering();
   }

   public static boolean shouldHideRenderSystem(WorldRenderer self) {
      if (gateBypassed) {
         return false;
      } else if (ClientWorldLoader.getIsCreatingClientWorld() || GuiPortalRendering.isRendering()) {
         return true;
      } else if (!PortalRendering.isRendering()) {
         return false;
      } else {
         MinecraftClient mc = MinecraftClient.getInstance();
         return mc.player == null || mc.world == mc.player.getEntityWorld();
      }
   }

   public static void afterLodDraw() {
      if (isStencilPortalPass()) {
         GL11.glEnable(2960);
         RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
      }
   }

   public static void onVoxyGlDisable(int cap) {
      if (cap == 2960 && isStencilPortalPass()) {
         GL11.glEnable(2960);
         RendererUsingStencil.setStencilLimitation(PortalRendering.getPortalLayer());
      } else {
         GL11.glDisable(cap);
      }
   }

   private static boolean isStencilPortalPass() {
      return PortalRendering.isRendering() && IPCGlobal.renderer == IPCGlobal.rendererUsingStencil;
   }

   public static class Invoker {
      public boolean isVoxyPresent() {
         return false;
      }

      public void onPlayerDimensionChangePromoted(WorldRenderer promotedRenderer, ClientWorld toWorld, WorldRenderer demotedRenderer) {
      }

      public void onRendererAboutToRender(WorldRenderer renderer) {
      }

      public boolean isLowOnVideoMemory() {
         return false;
      }
   }

}
