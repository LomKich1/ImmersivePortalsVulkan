package qouteall.imm_ptl.core.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Environment(EnvType.CLIENT)
public class GuiPortalRendering {
   private static final Logger LOGGER = LogUtils.getLogger();
   @Nullable
   private static Framebuffer renderingFrameBuffer = null;
   private static final HashMap<Framebuffer, WorldRenderInfo> renderingTasks = new HashMap<>();

   @Nullable
   public static Framebuffer getRenderingFrameBuffer() {
      return renderingFrameBuffer;
   }

   public static boolean isRendering() {
      return getRenderingFrameBuffer() != null;
   }

   private static void renderWorldIntoFrameBuffer(WorldRenderInfo worldRenderInfo, Framebuffer framebuffer) {
      RenderStates.basicProjectionMatrix = null;
      CHelper.checkGlError();
      ((IECamera)RenderStates.originalCamera).ip_resetState(worldRenderInfo.cameraPos, worldRenderInfo.world);
      Validate.isTrue(renderingFrameBuffer == null);
      renderingFrameBuffer = framebuffer;
      MyRenderHelper.restoreViewPort();
      Framebuffer mcFb = MyGameRenderer.client.getFramebuffer();
      Validate.isTrue(mcFb != framebuffer);
      ((IEMinecraftClient)MyGameRenderer.client).ip_setFrameBuffer(framebuffer);
      if (!worldRenderInfo.doRenderSky) {
         RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(framebuffer.getColorAttachment(), 0, framebuffer.getDepthAttachment(), 1.0);
      }

      IPCGlobal.renderer.prepareRendering();
      IPCGlobal.renderer.invokeWorldRendering(worldRenderInfo);
      IPCGlobal.renderer.finishRendering();
      ((IEMinecraftClient)MyGameRenderer.client).ip_setFrameBuffer(mcFb);
      renderingFrameBuffer = null;
      MyRenderHelper.restoreViewPort();
      CHelper.checkGlError();
      RenderStates.basicProjectionMatrix = null;
   }

   public static void submitNextFrameRendering(WorldRenderInfo worldRenderInfo, Framebuffer renderTarget) {
      if (!ClientWorldLoader.getIsInitialized()) {
         LOGGER.error("Trying to submit world rendering task before client world is initialized", new Throwable());
      } else {
         Validate.isTrue(!renderingTasks.containsKey(renderTarget));
         Framebuffer mcFB = MinecraftClient.getInstance().getFramebuffer();
         if (renderTarget.textureWidth != mcFB.textureWidth || renderTarget.textureHeight != mcFB.textureHeight) {
            renderTarget.resize(mcFB.textureWidth, mcFB.textureHeight);
            LOGGER.info("Resized Framebuffer for GUI Portal Rendering");
         }

         renderingTasks.put(renderTarget, worldRenderInfo);
      }
   }

   public static void _onGameRenderEnd() {
      renderingTasks.forEach((frameBuffer, worldRendering) -> renderWorldIntoFrameBuffer(worldRendering, frameBuffer));
      renderingTasks.clear();
   }

   public static void _init() {
      IPCGlobal.CLIENT_CLEANUP_EVENT.register(renderingTasks::clear);
   }
}
