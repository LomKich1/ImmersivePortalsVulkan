package qouteall.imm_ptl.peripheral.wand;

import com.mojang.datafixers.util.Pair;
import java.util.Arrays;
import java.util.Comparator;
import java.util.UUID;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider.Immediate;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.render.TransformationManager;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.AARotation;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.my_util.animation.Animated;
import qouteall.q_misc_util.my_util.animation.RenderedPoint;

@Environment(EnvType.CLIENT)
public class ClientPortalWandPortalCopy {
   @NotNull
   private static ClientPortalWandPortalCopy.Status status = new ClientPortalWandPortalCopy.Status_SelectPortal();
   public static Animated<RenderedPoint> cursor = new Animated<>(
      Animated.RENDERED_POINT_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPoint.EMPTY
   );
   public static Animated<UnilateralPortalState> selection = new Animated<>(
      UnilateralPortalState.ANIMATION_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, null
   );
   public static Animated<DQuaternion> placementOrientation = new Animated<>(
      Animated.QUATERNION_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, DQuaternion.identity
   );
   public static Animated<Double> placementOffsetLen = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   private static final int colorOfCursor = -16515844;
   private static final int colorOfSelection1 = -11337838;
   private static final int colorOfSelection2 = -224451;
   private static final int colorOfPendingPlacement1 = -16711681;
   private static final int colorOfPendingPlacement2 = -2339841;

   public static void reset() {
      status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      cursor.clearTarget();
      selection.clearTarget();
      placementOrientation.clearTarget();
      placementOffsetLen.clearTarget();
   }

   public static void updateDisplay() {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      ClientPlayerEntity player = minecraft.player;
      if (player != null) {
         Vec3d eyePos = player.getCameraPosVec(RenderStates.getPartialTick());
         Vec3d viewVec = player.getRotationVector();
         if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
            cursor.clearTarget();
            Pair<Portal, RayTraceResult> rayTraceResult = PortalUtils.lenientRayTracePortals(
                  player.getEntityWorld(), eyePos, eyePos.add(viewVec.multiply(64.0)), false, p -> true, 0.0
               )
               .orElse(null);
            if (rayTraceResult != null) {
               Portal portal = (Portal)rayTraceResult.getFirst();
               selection.setTarget(portal.getThisSideState(), Helper.secondToNano(0.5));
               statusSelectPortal.selectedPortalId = portal.getUuid();
               CustomTextOverlay.putText(
                  Text.translatable(
                     "imm_ptl.wand.copy.prompt.copy_or_cut",
                     new Object[]{
                        MinecraftClient.getInstance().options.useKey.getBoundKeyLocalizedText(),
                        MinecraftClient.getInstance().options.attackKey.getBoundKeyLocalizedText()
                     }
                  )
               );
            } else {
               selection.clearTarget();
               statusSelectPortal.selectedPortalId = null;
               CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.copy.prompt.select_portal"));
            }
         } else {
            if (!(status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal)) {
               throw new RuntimeException();
            }

            selection.clearTarget();
            HitResult hitResult = player.raycast(64.0, RenderStates.getPartialTick(), false);
            int alignment = IPConfig.getConfig().portalWandCursorAlignment;
            Vec3d preCursorPos;
            if (hitResult.getType() == Type.BLOCK && hitResult instanceof BlockHitResult blockHitResult) {
               preCursorPos = blockHitResult.getPos();
            } else {
               preCursorPos = eyePos.add(viewVec.multiply(5.0));
            }

            Vec3d cursorPointing = WandUtil.alignOnBlocks(player.getEntityWorld(), preCursorPos, alignment);
            cursor.setTarget(new RenderedPoint(new WithDim<>(player.getEntityWorld().getRegistryKey(), cursorPointing), 1.0), Helper.secondToNano(0.3));
            DQuaternion camRotInverse = TransformationManager.getPlayerCameraRotation().getConjugated();
            AARotation orientationRot = Arrays.stream(AARotation.values())
               .min(Comparator.comparingDouble(r -> DQuaternion.distanceSq(r.quaternion, camRotInverse)))
               .orElseThrow();
            DQuaternion orientation = orientationRot.quaternion;
            ClientPortalWandPortalCopy.PlacementRequirement req = statusPlacingPortal.placementRequirement;
            boolean isFacingUpOrDown = player.getPitch() > 45.0F || player.getPitch() < -45.0F;
            double offsetLen = isFacingUpOrDown ? 0.0 : req.height() / 2.0;
            Vec3d offset = orientation.rotate(new Vec3d(0.0, offsetLen, 0.0));
            Vec3d placementOrigin = cursorPointing.add(offset);
            UnilateralPortalState placement = new UnilateralPortalState(
               player.getEntityWorld().getRegistryKey(), placementOrigin, orientation, req.width(), req.height()
            );
            statusPlacingPortal.pendingPlacement = placement;
            placementOrientation.setTarget(orientation, Helper.secondToNano(0.3));
            placementOffsetLen.setTarget(offsetLen, Helper.secondToNano(0.3));
            CustomTextOverlay.putText(
               Text.translatable(
                  "imm_ptl.wand.copy.prompt.place_portal",
                  new Object[]{
                     MinecraftClient.getInstance().options.useKey.getBoundKeyLocalizedText(),
                     MinecraftClient.getInstance().options.attackKey.getBoundKeyLocalizedText()
                  }
               )
            );
         }
      }
   }

   public static void onLeftClick() {
      if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
         if (statusSelectPortal.selectedPortalId != null) {
            Portal portal = WandUtil.getClientPortalByUUID(statusSelectPortal.selectedPortalId);
            if (portal != null) {
               status = new ClientPortalWandPortalCopy.Status_PlacingPortal(
                  new ClientPortalWandPortalCopy.PlacementRequirement(portal.getWidth(), portal.getHeight()), true
               );
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.copyCutPortal", statusSelectPortal.selectedPortalId, true
               );
            }
         }
      } else if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal) {
         McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.clearPortalClipboard");
         status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      }
   }

   public static void onRightClick() {
      if (status instanceof ClientPortalWandPortalCopy.Status_SelectPortal statusSelectPortal) {
         if (statusSelectPortal.selectedPortalId != null) {
            Portal portal = WandUtil.getClientPortalByUUID(statusSelectPortal.selectedPortalId);
            if (portal != null) {
               status = new ClientPortalWandPortalCopy.Status_PlacingPortal(
                  new ClientPortalWandPortalCopy.PlacementRequirement(portal.getWidth(), portal.getHeight()), false
               );
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.copyCutPortal", statusSelectPortal.selectedPortalId, false
               );
            }
         }
      } else if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal && statusPlacingPortal.pendingPlacement != null) {
         McRemoteProcedureCall.tellServerToInvoke(
            "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.confirmCopyCut",
            statusPlacingPortal.pendingPlacement.position(),
            statusPlacingPortal.pendingPlacement.orientation()
         );
         status = new ClientPortalWandPortalCopy.Status_SelectPortal();
      }
   }

   public static void render(MatrixStack matrixStack, Immediate bufferSource, double camX, double camY, double camZ) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (!PortalRendering.isRendering()) {
            RegistryKey<World> currDim = player.getEntityWorld().getRegistryKey();
            Vec3d cameraPos = new Vec3d(camX, camY, camZ);
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayers.lines());
            Vec3d cursorPos = null;
            RenderedPoint currentCursor = cursor.getCurrent();
            if (currentCursor != null) {
               WithDim<Vec3d> posWithDim = currentCursor.pos();
               if (posWithDim != null && posWithDim.dimension() == currDim) {
                  cursorPos = posWithDim.value();
               }
            }

            if (cursorPos != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, cursorPos, -16515844, cursor.getCurrent().scale(), matrixStack);
            }

            UnilateralPortalState rect = selection.getCurrent();
            if (rect != null && rect.dimension() == currDim) {
               WireRenderingHelper.renderRectFrameFlow(matrixStack, cameraPos, vertexConsumer, rect, -11337838, -224451);
            }

            if (status instanceof ClientPortalWandPortalCopy.Status_PlacingPortal statusPlacingPortal) {
               DQuaternion orientation = placementOrientation.getCurrent();
               Double offsetLen = placementOffsetLen.getCurrent();
               Validate.isTrue(orientation != null);
               Validate.isTrue(offsetLen != null);
               Vec3d offset = orientation.rotate(new Vec3d(0.0, offsetLen, 0.0));
               if (cursorPos != null) {
                  ClientPortalWandPortalCopy.PlacementRequirement req = statusPlacingPortal.placementRequirement;
                  UnilateralPortalState placement = new UnilateralPortalState(currDim, cursorPos.add(offset), orientation, req.width(), req.height());
                  WireRenderingHelper.renderRectFrameFlow(matrixStack, cameraPos, vertexConsumer, placement, -16711681, -2339841);
               }
            }
         }
      }
   }

   public record PlacementRequirement(double width, double height) {
   }

   private sealed interface Status permits ClientPortalWandPortalCopy.Status_SelectPortal, ClientPortalWandPortalCopy.Status_PlacingPortal {
   }

   private static final class Status_PlacingPortal implements ClientPortalWandPortalCopy.Status {
      @NotNull
      public final ClientPortalWandPortalCopy.PlacementRequirement placementRequirement;
      public final boolean isCut;
      @Nullable
      public UnilateralPortalState pendingPlacement;

      private Status_PlacingPortal(@NotNull ClientPortalWandPortalCopy.PlacementRequirement placementRequirement, boolean isCut) {
         this.placementRequirement = placementRequirement;
         this.isCut = isCut;
      }
   }

   private static final class Status_SelectPortal implements ClientPortalWandPortalCopy.Status {
      @Nullable
      public UUID selectedPortalId;
   }
}
