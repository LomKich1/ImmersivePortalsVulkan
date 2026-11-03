package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.gl.Framebuffer;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

public class RendererDebug extends PortalRenderer {
   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
      this.renderPortals(modelView);
   }

   @Override
   public void onAfterTranslucentRendering(Matrix4f modelView) {
   }

   @Override
   public void onHandRenderingEnded() {
   }

   @Override
   public void prepareRendering() {
   }

   @Override
   public void finishRendering() {
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   @Override
   public boolean replaceFrameBufferClearing() {
      return false;
   }

   protected void doRenderPortal(Portal portal, Matrix4f modelView) {
      if (RenderStates.getRenderedPortalNum() == 0) {
         if (this.testShouldRenderPortal(portal, modelView)) {
            PortalRendering.pushPortalLayer(portal);
            Framebuffer mainTarget = client.getFramebuffer();
            RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(mainTarget.getColorAttachment(), -65281, mainTarget.getDepthAttachment(), 1.0);
            GL11.glDisable(2960);
            this.renderPortalContent(portal);
            PortalRendering.popPortalLayer();
         }
      }
   }

   private boolean testShouldRenderPortal(Portal portal, Matrix4f modelView) {
      return QueryManager.renderAndGetDoesAnySamplePass(
         () -> ViewAreaRenderer.renderPortalArea(
            portal, Vec3d.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, true, true, true, client.getFramebuffer()
         )
      );
   }

   protected void renderPortals(Matrix4f modelView) {
      for (Portal portal : this.getPortalsToRender(modelView)) {
         this.doRenderPortal(portal, modelView);
      }
   }
}
