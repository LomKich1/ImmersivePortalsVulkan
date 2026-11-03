package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.MutableText;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.render.IPRenderTypes;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.my_util.animation.Animated;
import qouteall.q_misc_util.my_util.animation.RenderedPlane;

@Environment(EnvType.CLIENT)
public class ClientPortalWandPortalCreation {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final Animated<Vec3d> cursor = new Animated<>(
      Animated.VEC3_NULLABLE_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, null
   );
   public static final Animated<RenderedPlane> renderedPlane = new Animated<>(
      Animated.RENDERED_PLANE_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPlane.NONE
   );
   @NotNull
   public static ProtoPortal protoPortal = new ProtoPortal();
   private static final int colorOfFirstSideLeftBottom = -327425;
   private static final int colorOfFirstSideRightBottom = -1691038;
   private static final int colorOfFirstSideLeftUp = -200864;
   private static final int colorOfSecondSideLeftBottom = -5308585;
   private static final int colorOfSecondSideRightBottom = -11010094;
   private static final int colorOfSecondSideLeftUp = -4344833;
   private static final int colorOfPlane = -5254150;
   private static final int colorOfCircle = -16515869;
   private static final int colorOfFirstPortalArea = -225277;
   private static final int colorOfFirstPortalArea2 = -33384;
   private static final int colorOfSecondPortalArea = -10423556;
   private static final int colorOfSecondPortalArea2 = -10423556;

   public static void reset() {
      protoPortal = new ProtoPortal();
      renderedPlane.clearTarget();
   }

   public static void onLeftClick() {
      undo();
   }

   private static void undo() {
      protoPortal.undo();
   }

   public static void onRightClick() {
      Vec3d cursorTarget = cursor.getTarget();
      if (cursorTarget != null) {
         ClientWorld world = MinecraftClient.getInstance().world;
         if (world != null) {
            protoPortal.tryPlaceCursor(world.getRegistryKey(), cursorTarget);
            if (protoPortal.isComplete()) {
               finish();
            }
         }
      }
   }

   public static void clearCursorPointing() {
      cursor.clearTarget();
   }

   public static void updateDisplay() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         RegistryKey<World> cursorLimitingDim = protoPortal.getCursorConstraintDim();
         if (cursorLimitingDim != null && player.getEntityWorld().getRegistryKey() != cursorLimitingDim) {
            cursor.clearTarget();
            renderedPlane.clearTarget();
         } else {
            WithDim<Plane> limitingPlane = protoPortal.getCursorConstraintPlane();
            WithDim<Circle> limitingCircle = protoPortal.getCursorConstraintCircle();
            Vec3d eyePos = player.getCameraPosVec(RenderStates.getPartialTick());
            Vec3d viewVec = player.getRotationVec(RenderStates.getPartialTick());
            Vec3d cursorPointing = null;
            int alignment = IPConfig.getConfig().portalWandCursorAlignment;
            if (limitingPlane != null) {
               cursorPointing = limitingPlane.value().rayTrace(eyePos, viewVec);
               if (cursorPointing != null) {
                  cursorPointing = WandUtil.alignOnBlocks(player.getEntityWorld(), cursorPointing, alignment);
                  cursorPointing = limitingPlane.value().getProjection(cursorPointing);
                  if (limitingCircle != null) {
                     cursorPointing = limitingCircle.value().projectToCircle(cursorPointing);
                  }
               }
            } else {
               HitResult hitResult = player.raycast(64.0, RenderStates.getPartialTick(), false);
               if (hitResult.getType() == Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
                  cursorPointing = WandUtil.alignOnBlocks(player.getEntityWorld(), blockHitResult.getPos(), alignment);
               }
            }

            if (limitingPlane != null && limitingCircle == null) {
               renderedPlane.setTarget(new RenderedPlane(limitingPlane, 1.0), Helper.secondToNano(3.0));
            } else {
               renderedPlane.setTarget(RenderedPlane.NONE, Helper.secondToNano(0.5));
            }

            if (cursorPointing != null) {
               ProtoPortal pendingState = protoPortal.copy();
               boolean canPlace = pendingState.tryPlaceCursor(player.getEntityWorld().getRegistryKey(), cursorPointing);
               if (!canPlace || !pendingState.isValidPlacement()) {
                  cursorPointing = null;
                  pendingState = null;
               }

               MutableText promptMessage = protoPortal.getPromptMessage(pendingState);
               if (promptMessage != null) {
                  CustomTextOverlay.putText(promptMessage);
               }
            }

            if (cursorPointing != null) {
               cursor.setTarget(cursorPointing, Helper.secondToNano(0.1));
            } else {
               cursor.clearTarget();
            }
         }
      }
   }

   public static void finish() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.finishPortalCreation", protoPortal.copy()
         );
         reset();
      }
   }

   public static void render(MatrixStack matrixStack, Immediate bufferSource, double camX, double camY, double camZ) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         RegistryKey<World> currDim = player.getEntityWorld().getRegistryKey();
         VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayers.lines());
         Vec3d cameraPos = new Vec3d(camX, camY, camZ);
         WithDim<Circle> circle = protoPortal.getCursorConstraintCircle();
         Vec3d renderedCursor = cursor.getCurrent();
         if (circle != null && renderedCursor != null) {
            renderedCursor = circle.value().projectToCircle(renderedCursor);
         }

         ProtoPortal renderedProtoPortal = protoPortal;
         if (renderedCursor != null) {
            ProtoPortal pending = protoPortal.copy();
            boolean canPlace = pending.tryPlaceCursor(currDim, renderedCursor);
            if (canPlace) {
               renderedProtoPortal = pending;
            }
         }

         if (circle == null) {
            circle = protoPortal.getCursorConstraintCircle();
         }

         if (renderedProtoPortal.firstSide != null && currDim == renderedProtoPortal.firstSide.dimension) {
            WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.firstSide.leftBottom, -327425, 1.0, matrixStack);
            if (renderedProtoPortal.firstSide.rightBottom != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.firstSide.rightBottom, -1691038, 1.0, matrixStack);
            }

            if (renderedProtoPortal.firstSide.leftTop != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.firstSide.leftTop, -200864, 1.0, matrixStack);
               WandUtil.renderPortalAreaGrid(vertexConsumer, cameraPos, renderedProtoPortal.firstSide, -225277, matrixStack);
            }
         }

         if (renderedProtoPortal.secondSide != null && currDim == renderedProtoPortal.secondSide.dimension) {
            WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.secondSide.leftBottom, -5308585, 1.0, matrixStack);
            if (renderedProtoPortal.secondSide.rightBottom != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.secondSide.rightBottom, -11010094, 1.0, matrixStack);
            }

            if (renderedProtoPortal.secondSide.leftTop != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedProtoPortal.secondSide.leftTop, -4344833, 1.0, matrixStack);
               WandUtil.renderPortalAreaGrid(vertexConsumer, cameraPos, renderedProtoPortal.secondSide, -10423556, matrixStack);
            }
         }

         VertexConsumer debugLineStripConsumer = bufferSource.getBuffer(IPRenderTypes.debugLineStrip());
         WithDim<Circle> renderedCircle = circle != null ? circle : renderedProtoPortal.getCursorConstraintCircle();
         if (renderedCircle != null && renderedCircle.dimension() == currDim) {
            WireRenderingHelper.renderCircle(debugLineStripConsumer, cameraPos, renderedCircle.value(), -16515869, matrixStack);
         }

         RenderedPlane currRenderedPlane = renderedPlane.getCurrent();
         if (currRenderedPlane != null && currRenderedPlane.plane() != null && currRenderedPlane.plane().dimension() == currDim) {
            double scale = currRenderedPlane.scale();
            if (scale > 0.01) {
               WireRenderingHelper.renderPlane(debugLineStripConsumer, cameraPos, currRenderedPlane.plane().value(), scale, -5254150, matrixStack, true);
            }
         }
      }
   }
}
