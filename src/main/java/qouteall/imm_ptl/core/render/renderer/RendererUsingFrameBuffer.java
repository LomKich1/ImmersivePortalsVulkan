package qouteall.imm_ptl.core.render.renderer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.IPPortingLibCompat;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.FrontClipping;
import qouteall.imm_ptl.core.render.MyRenderHelper;
import qouteall.imm_ptl.core.render.QueryManager;
import qouteall.imm_ptl.core.render.SecondaryFrameBuffer;
import qouteall.imm_ptl.core.render.ViewAreaRenderer;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

public class RendererUsingFrameBuffer extends PortalRenderer {
   SecondaryFrameBuffer secondaryFrameBuffer = new SecondaryFrameBuffer();

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
   public void finishRendering() {
   }

   @Override
   public void prepareRendering() {
      this.secondaryFrameBuffer.prepare();
      GlStateManager._enableDepthTest();
      GL11.glDisable(2960);
      IPPortingLibCompat.setIsStencilEnabled(client.getFramebuffer(), false);
   }

   protected void doRenderPortal(Portal portal, Matrix4f modelView) {
      if (!PortalRendering.isRendering()) {
         if (this.testShouldRenderPortal(portal, modelView)) {
            PortalRendering.pushPortalLayer(portal);
            Framebuffer oldFrameBuffer = client.getFramebuffer();
            ((IEMinecraftClient)client).ip_setFrameBuffer(this.secondaryFrameBuffer.fb);
            RenderSystem.getDevice()
               .createCommandEncoder()
               .clearColorAndDepthTextures(this.secondaryFrameBuffer.fb.getColorAttachment(), -65281, this.secondaryFrameBuffer.fb.getDepthAttachment(), 1.0);
            GL11.glDisable(2960);
            this.renderPortalContent(portal);
            ((IEMinecraftClient)client).ip_setFrameBuffer(oldFrameBuffer);
            PortalRendering.popPortalLayer();
            CHelper.enableDepthClamp();
            this.renderSecondBufferIntoMainBuffer(portal, modelView, oldFrameBuffer);
            CHelper.disableDepthClamp();
            MyRenderHelper.debugFramebufferDepth();
         }
      }
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }

   @Override
   public boolean replaceFrameBufferClearing() {
      return false;
   }

   private boolean testShouldRenderPortal(Portal portal, Matrix4f modelView) {
      FrontClipping.updateInnerClipping(modelView);
      return QueryManager.renderAndGetDoesAnySamplePass(
         () -> ViewAreaRenderer.renderPortalArea(
            portal, Vec3d.ZERO, modelView, MyRenderHelper.getProjectionMatrix(), true, true, true, true, client.getFramebuffer()
         )
      );
   }

   private void renderSecondBufferIntoMainBuffer(Portal portal, Matrix4f modelView, Framebuffer mainBuffer) {
      MyRenderHelper.drawPortalAreaWithFramebuffer(portal, this.secondaryFrameBuffer.fb, modelView, MyRenderHelper.getProjectionMatrix(), mainBuffer);
   }

   protected void renderPortals(Matrix4f modelView) {
      for (Portal portal : this.getPortalsToRender(modelView)) {
         this.doRenderPortal(portal, modelView);
      }
   }
}
