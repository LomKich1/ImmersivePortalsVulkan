package qouteall.imm_ptl.core.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;

@Environment(EnvType.CLIENT)
public class PortalEntityRenderer extends EntityRenderer<Portal, PortalEntityRenderer.PortalRenderState> {
   public PortalEntityRenderer(Context context) {
      super(context);
   }

   public PortalEntityRenderer.PortalRenderState createRenderState() {
      return new PortalEntityRenderer.PortalRenderState();
   }

   public void extractRenderState(Portal portal, PortalEntityRenderer.PortalRenderState state, float partialTick) {
      super.updateRenderState(portal, state, partialTick);
      state.portal = portal;
   }

   public void submit(
      PortalEntityRenderer.PortalRenderState state, MatrixStack matrixStack, OrderedRenderCommandQueue submitNodeCollector, CameraRenderState cameraRenderState
   ) {
      Portal portal = state.portal;
      IPCGlobal.renderer.renderPortalInEntityRenderer(portal);
      if (OverlayRendering.shouldRenderOverlay(portal)) {
         OverlayRendering.onRenderPortalEntity(portal, matrixStack, submitNodeCollector);
      }

      if (IPGlobal.debugRenderPortalShapeMesh && !PortalRendering.isRendering()) {
         submitNodeCollector.submitCustom(matrixStack, RenderLayers.lines(), (pose, lineVertexConsumer) -> {
            MatrixStack poseStack = new MatrixStack();
            poseStack.peek().copy(pose);
            WireRenderingHelper.renderPortalShapeMeshDebug(poseStack, lineVertexConsumer, portal);
         });
      }

      super.render(state, matrixStack, submitNodeCollector, cameraRenderState);
   }

   public static class PortalRenderState extends EntityRenderState {
      public Portal portal;
   }
}
