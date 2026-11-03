package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.platform.DepthTestFunction;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalRenderInfo;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.IPRenderPipelines;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.FogRendererContext;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

public class RendererUsingStencil extends PortalRenderer {
   @Override
   public boolean replaceFrameBufferClearing() {
      boolean skipClearing = WorldRenderInfo.isRendering();
      if (skipClearing && WorldRenderInfo.getTopRenderInfo().doRenderSky) {
         MyRenderHelper.renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_COLOR, FogRendererContext.getCurrentFogColor(), client.getFramebuffer());
      }

      return skipClearing;
   }

   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
      this.doPortalRendering(modelView);
   }

   protected void doPortalRendering(Matrix4f modelView) {
      GlStateManager._enableDepthTest();
      GlStateManager._depthMask(true);
      GL11.glEnable(2960);
      Profilers.get().swap("render_portal_total");
      this.renderPortals(modelView);
      if (PortalRendering.isRendering()) {
         this.setStencilStateForWorldRendering();
      } else {
         this.myFinishRendering();
      }
   }

   protected void renderPortals(Matrix4f modelView) {
      for (Portal portal : this.getPortalsToRender(modelView)) {
         this.doRenderPortal(portal, modelView);
      }
   }

   @Override
   public void onAfterTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void onHandRenderingEnded() {
   }

   @Override
   public void prepareRendering() {
      if (!IPPortingLibCompat.getIsStencilEnabled(client.getFramebuffer())) {
         IPPortingLibCompat.setIsStencilEnabled(client.getFramebuffer(), true);
         if (MinecraftClient.usesImprovedTransparency()) {
         }
      }

      MyRenderHelper.bindRenderTargetFbo(client.getFramebuffer());
      GL11.glClearStencil(0);
      GL11.glClear(1024);
      GlStateManager._enableDepthTest();
      GL11.glEnable(2960);
   }

   @Override
   public void finishRendering() {
   }

   private void myFinishRendering() {
      GL11.glStencilFunc(519, 2333, 255);
      GL11.glStencilOp(7680, 7680, 7680);
      GL11.glDisable(2960);
      GlStateManager._enableDepthTest();
   }

   protected void doRenderPortal(Portal portal, Matrix4f modelView) {
      if (!shouldSkipRenderingInsideFuseViewPortal(portal)) {
         int outerPortalStencilValue = PortalRendering.getPortalLayer();
         Profilers.get().push("render_view_area");
         boolean anySamplePassed = PortalRenderInfo.renderAndDecideVisibility(portal, () -> this.renderPortalViewAreaToStencil(portal, modelView));
         Profilers.get().pop();
         if (!anySamplePassed) {
            this.setStencilStateForWorldRendering();
         } else {
            PortalRendering.pushPortalLayer(portal);
            int thisPortalStencilValue = outerPortalStencilValue + 1;
            if (!portal.isFuseView()) {
               Profilers.get().push("clear_depth_of_view_area");
               this.clearDepthOfThePortalViewArea(portal);
               Profilers.get().pop();
            }

            this.setStencilStateForWorldRendering();
            this.renderPortalContent(portal);
            PortalRendering.popPortalLayer();
            if (!portal.isFuseView()) {
               this.restoreDepthOfPortalViewArea(portal, modelView, thisPortalStencilValue);
            }

            clampStencilValue(outerPortalStencilValue);
         }
      }
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   private void renderPortalViewAreaToStencil(Portal portal, Matrix4f modelView) {
      int outerPortalStencilValue = PortalRendering.getPortalLayer();
      GL11.glStencilFunc(514, outerPortalStencilValue, 255);
      GL11.glStencilOp(7680, 7680, 7682);
      GL11.glStencilMask(255);
      FrontClipping.updateInnerClipping(modelView);
      ViewAreaRenderer.renderPortalArea(
         portal, Vec3d.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, true, true, true, client.getFramebuffer()
      );
   }

   private void clearDepthOfThePortalViewArea(Portal portal) {
      GlStateManager._enableDepthTest();
      GlStateManager._depthMask(true);
      this.setStencilStateForWorldRendering();
      MyRenderHelper.renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_DEPTH_CLEAR, 1.0F, 255, 255, 255, 255, client.getFramebuffer());
   }

   protected void restoreDepthOfPortalViewArea(Portal portal, Matrix4f modelView, int portalStencilValue) {
      setStencilLimitation(portalStencilValue);
      Framebuffer target = client.getFramebuffer();

      for (DepthTestFunction depthTest : new DepthTestFunction[]{DepthTestFunction.LESS_DEPTH_TEST, DepthTestFunction.GREATER_DEPTH_TEST}) {
         ViewAreaRenderer.renderPortalArea(
            portal, Vec3d.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), false, false, true, true, depthTest, target
         );
      }
   }

   public static void clampStencilValue(int maximumValue) {
      GlStateManager._depthMask(true);
      GL11.glStencilFunc(513, maximumValue, 255);
      GL11.glStencilOp(7680, 7681, 7681);
      MyRenderHelper.renderScreenTriangle(IPRenderPipelines.SCREEN_TRIANGLE_STENCIL_ONLY, client.getFramebuffer());
      GlStateManager._enableDepthTest();
   }

   private void setStencilStateForWorldRendering() {
      int thisPortalStencilValue = PortalRendering.getPortalLayer();
      setStencilLimitation(thisPortalStencilValue);
   }

   public static void setStencilLimitation(int stencilValue) {
      GL11.glStencilFunc(514, stencilValue, 255);
      GL11.glStencilOp(7680, 7680, 7680);
   }

   public static boolean shouldSkipRenderingInsideFuseViewPortal(Portal portal) {
      if (!PortalRendering.isRendering()) {
         return false;
      } else {
         Portal renderingPortal = PortalRendering.getRenderingPortal();
         if (!renderingPortal.isFuseView()) {
            return false;
         } else {
            Vec3d cameraPos = CHelper.getCurrentCameraPos();
            Vec3d transformedCameraPos = portal.transformPoint(renderingPortal.transformPoint(cameraPos));
            return cameraPos.squaredDistanceTo(transformedCameraPos) < 0.1;
         }
      }
   }
}
