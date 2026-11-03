package qouteall.imm_ptl.core.render;

import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory.Context;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import qouteall.imm_ptl.core.portal.LoadingIndicatorEntity;

public class LoadingIndicatorRenderer extends EntityRenderer<LoadingIndicatorEntity, EntityRenderState> {
   public LoadingIndicatorRenderer(Context context) {
      super(context);
   }

   public EntityRenderState createRenderState() {
      return new EntityRenderState();
   }

   public void render(EntityRenderState renderState, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraState) {
   }
}
