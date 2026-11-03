package qouteall.imm_ptl.core.render.renderer;

import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import qouteall.imm_ptl.core.portal.Portal;

public class RendererDummy extends PortalRenderer {
   @Override
   public boolean replaceFrameBufferClearing() {
      return false;
   }

   @Override
   public void prepareRendering() {
   }

   @Override
   public void onBeforeTranslucentRendering(Matrix4f modelView) {
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

   protected void doRenderPortal(Portal portal, MatrixStack matrixStack) {
   }

   @Override
   public void renderPortalInEntityRenderer(Portal portal) {
   }
}
