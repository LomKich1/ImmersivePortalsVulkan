package qouteall.imm_ptl.core.render;

import java.util.ArrayList;
import java.util.List;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.render.model.BlockModelPart;
import net.minecraft.client.render.model.BlockStateModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.compat.iris_compatibility.IrisInterface;
import qouteall.imm_ptl.core.compat.sodium_compatibility.SodiumInterface;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.nether_portal.BlockPortalShape;
import qouteall.imm_ptl.core.portal.nether_portal.BreakablePortalEntity;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Environment(EnvType.CLIENT)
public class OverlayRendering {
   private static final Random random = Random.create();
   private static boolean shaderOverlayWarned = false;

   public static boolean shouldRenderOverlay(Portal portal) {
      return portal instanceof BreakablePortalEntity breakablePortalEntity && breakablePortalEntity.getActualOverlay() != null
         ? breakablePortalEntity.isInFrontOfPortal(CHelper.getCurrentCameraPos())
         : false;
   }

   public static void onRenderPortalEntity(Portal portal, MatrixStack matrixStack, OrderedRenderCommandQueue submitNodeCollector) {
      if (IrisInterface.invoker.isShaders()) {
         if (!shaderOverlayWarned) {
            shaderOverlayWarned = true;
            CHelper.printChat("[Immersive Portals] Portal overlay cannot be rendered with shaders");
         }
      } else {
         if (portal instanceof BreakablePortalEntity) {
            renderBreakablePortalOverlay((BreakablePortalEntity)portal, RenderStates.getPartialTick(), matrixStack, submitNodeCollector);
         }
      }
   }

   public static List<BakedQuad> getQuads(BlockStateModel model, BlockState blockState, Vec3d portalNormal) {
      Direction facing = Direction.getFacing(portalNormal.x, portalNormal.y, portalNormal.z);
      List<BakedQuad> result = new ArrayList<>();
      List<BlockModelPart> parts = model.getParts(random);

      for (BlockModelPart part : parts) {
         result.addAll(part.getQuads(facing));
      }

      for (BlockModelPart part : parts) {
         result.addAll(part.getQuads(null));
      }

      if (result.isEmpty()) {
         for (Direction direction : Direction.values()) {
            for (BlockModelPart part : parts) {
               result.addAll(part.getQuads(direction));
            }
         }
      }

      return result;
   }

   private static void renderBreakablePortalOverlay(
      BreakablePortalEntity portal, float partialTick, MatrixStack matrixStack, OrderedRenderCommandQueue submitNodeCollector
   ) {
      BreakablePortalEntity.OverlayInfo overlay = portal.getActualOverlay();
      if (overlay != null) {
         BlockState blockState = overlay.blockState();
         Vec3d cameraPos = CHelper.getCurrentCameraPos();
         if (blockState != null) {
            BlockRenderManager blockRenderManager = MinecraftClient.getInstance().getBlockRenderManager();
            BlockPortalShape blockPortalShape = portal.blockPortalShape;
            if (blockPortalShape != null) {
               matrixStack.push();
               Vec3d offset = portal.getNormal().multiply(overlay.offset());
               Vec3d pos = portal.getEntityPos();
               matrixStack.translate(offset.x, offset.y, offset.z);
               BlockStateModel model = blockRenderManager.getModel(blockState);
               RenderLayer renderLayer = TexturedRenderLayers.getBlockTranslucentCull();
               List<BakedQuad> quads = getQuads(model, blockState, portal.getNormal());
               random.setSeed(0L);
               float opacity = (float)overlay.opacity();

               for (BlockPos blockPos : blockPortalShape.area) {
                  matrixStack.push();
                  matrixStack.translate(blockPos.getX() - pos.x, blockPos.getY() - pos.y, blockPos.getZ() - pos.z);
                  if (overlay.rotation() != null) {
                     matrixStack.multiply(overlay.rotation().toMcQuaternion());
                  }

                  submitNodeCollector.submitCustom(
                     matrixStack,
                     renderLayer,
                     (blockPose, buffer) -> {
                        for (BakedQuad quad : quads) {
                           SodiumInterface.invoker.markSpriteActive(quad.sprite());
                           buffer.quad(
                              blockPose,
                              quad,
                              new float[]{1.0F, 1.0F, 1.0F, 1.0F},
                              1.0F,
                              1.0F,
                              1.0F,
                              opacity,
                              new int[]{14680304, 14680304, 14680304, 14680304},
                              OverlayTexture.DEFAULT_UV
                           );
                        }
                     }
                  );
                  matrixStack.pop();
               }

               matrixStack.pop();
            }
         }
      }
   }
}
