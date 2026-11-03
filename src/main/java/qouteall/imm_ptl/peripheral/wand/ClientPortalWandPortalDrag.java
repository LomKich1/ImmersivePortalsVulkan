package qouteall.imm_ptl.peripheral.wand;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.text.MutableText;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.VertexConsumerProvider.class_4598;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.mc_utils.WireRenderingHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.TimingFunction;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.util.PortalLocalXYNormalized;
import qouteall.imm_ptl.core.render.IPRenderTypes;
import qouteall.imm_ptl.core.render.context_management.PortalRendering;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.q_misc_util.CustomTextOverlay;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.api.McRemoteProcedureCall;
import qouteall.q_misc_util.my_util.Circle;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.RayTraceResult;
import qouteall.q_misc_util.my_util.Sphere;
import qouteall.q_misc_util.my_util.WithDim;
import qouteall.q_misc_util.my_util.animation.Animated;
import qouteall.q_misc_util.my_util.animation.RenderedPlane;
import qouteall.q_misc_util.my_util.animation.RenderedPoint;

@Environment(EnvType.CLIENT)
public class ClientPortalWandPortalDrag {
   private static final Logger LOGGER = LogUtils.getLogger();
   @Nullable
   private static UUID selectedPortalId;
   @Nullable
   private static PortalLocalXYNormalized lockedAnchor;
   private static boolean lockWidth = false;
   private static boolean lockHeight = false;
   @Nullable
   private static ClientPortalWandPortalDrag.SelectionStatus selectionStatus;
   public static Animated<RenderedPoint> cursor = new Animated<>(
      Animated.RENDERED_POINT_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPoint.EMPTY
   );
   public static Animated<UnilateralPortalState> renderedRect = new Animated<>(
      UnilateralPortalState.ANIMATION_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, null
   );
   public static Animated<RenderedPlane> renderedPlane = new Animated<>(
      Animated.RENDERED_PLANE_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPlane.NONE
   );
   public static Animated<RenderedPlane> renderedPrePlane = new Animated<>(
      Animated.RENDERED_PLANE_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, RenderedPlane.NONE
   );
   private static Animated<RenderedPoint> renderedLockedAnchor = new Animated<>(
      Animated.RENDERED_POINT_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.circle::mapProgress, RenderedPoint.EMPTY
   );
   private static Animated<Double> renderedWidthLineSegment = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   private static Animated<Double> renderedHeightLineSegment = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   private static Animated<Double> renderedWidthLock = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   private static Animated<Double> renderedHeightLock = new Animated<>(
      Animated.DOUBLE_DEFAULT_ZERO_TYPE_INFO, () -> RenderStates.renderStartNanoTime, TimingFunction.sine::mapProgress, 0.0
   );
   @Nullable
   public static ClientPortalWandPortalDrag.DraggingContext draggingContext;
   private static boolean isUndoing = false;
   private static final int colorOfCursor = -16515844;
   private static final int colorOfRect1 = -1309700;
   private static final int colorOfRect2 = -218877;
   private static final int colorOfPlane = -1;
   private static final int colorOfLock = -1;
   private static final int colorOfWidthLine = -16516018;
   private static final int colorOfHeightLine = -198653;
   private static final int colorOfCursorInLock = -384160;
   private static final int colorOfSphere = -1;
   private static final double renderedPreCircleRadius = 0.5;

   public static void init() {
      ClientPortalAnimationManagement.clientAnimationUpdateSignal.connect(ClientPortalWandPortalDrag::updateDraggedPortalAnimation);
   }

   public static void reset() {
      selectedPortalId = null;
      clearLock();
      selectionStatus = null;
      cursor.clearTarget();
      renderedPrePlane.clearTarget();
      renderedPlane.clearTarget();
      renderedRect.clearTarget();
      draggingContext = null;
      isUndoing = false;
   }

   public static void onLeftClick() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (isDragging()) {
            undoDragging();
         } else if (selectionStatus != null) {
            if (selectionStatus.selectsHorizontalEdge) {
               lockWidth = !lockWidth;
               CustomTextOverlay.putText(
                  Text.translatable(lockWidth ? "imm_ptl.wand.width_locked" : "imm_ptl.wand.width_unlocked").formatted(Formatting.field_1065),
                  3.0,
                  "0_alert"
               );
            } else if (selectionStatus.selectsVerticalEdge) {
               lockHeight = !lockHeight;
               CustomTextOverlay.putText(
                  Text.translatable(lockHeight ? "imm_ptl.wand.height_locked" : "imm_ptl.wand.height_unlocked").formatted(Formatting.field_1065),
                  3.0,
                  "0_alert"
               );
            } else {
               PortalLocalXYNormalized selectedAnchor = selectionStatus.selectedAnchor;
               if (lockedAnchor != null && lockedAnchor.isCloseTo(selectedAnchor, 0.01)) {
                  lockedAnchor = null;
                  CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.anchor_unlocked").formatted(Formatting.field_1065), 3.0, "0_alert");
               } else {
                  CustomTextOverlay.putText(
                     Text.translatable(lockedAnchor != null ? "imm_ptl.wand.anchor_lock_moved" : "imm_ptl.wand.anchor_locked")
                        .formatted(Formatting.field_1065),
                     3.0,
                     "0_alert"
                  );
                  lockedAnchor = selectedAnchor;
               }
            }
         }
      }
   }

   public static void onRightClick() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (!isDragging()) {
            if (!isUndoing) {
               startDragging();
            }
         }
      }
   }

   public static void tick() {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      ClientPlayerEntity player = minecraft.player;
      if (player != null) {
         if (isDragging()
            && player.getStackInHand(Hand.field_5808).getItem() != PortalWandItem.instance
            && player.getStackInHand(Hand.field_5810).getItem() != PortalWandItem.instance) {
            LOGGER.info("Undo dragging because the player is not holding the wand");
            undoDragging();
         }
      }
   }

   public static boolean isDragging() {
      return draggingContext != null;
   }

   public static void updateDisplay() {
      MinecraftClient minecraft = MinecraftClient.getInstance();
      ClientPlayerEntity player = minecraft.player;
      if (player != null) {
         if (!minecraft.options.useKey.isPressed()) {
            isUndoing = false;
         }

         if (!isUndoing) {
            if (draggingContext != null) {
               handleDragging(player, draggingContext);
            } else {
               Portal originalSelectedPortal = getSelectedPortal();
               if (originalSelectedPortal == null) {
                  reset();
               }

               Vec3d eyePos = player.getCameraPosVec(RenderStates.getPartialTick());
               Vec3d viewVec = player.getRotationVector();
               Pair<Portal, RayTraceResult> rayTraceResult = PortalUtils.lenientRayTracePortals(
                     player.getEntityWorld(), eyePos, eyePos.add(viewVec.multiply(64.0)), false, p -> true, 1.0
                  )
                  .orElse(null);
               if (rayTraceResult == null) {
                  selectionStatus = null;
                  cursor.clearTarget();
                  renderedPlane.clearTarget();
                  renderedPrePlane.clearTarget();
                  CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.select_portal"));
                  if (originalSelectedPortal == null) {
                     renderedRect.clearTarget();
                  } else {
                     renderedRect.setTarget(originalSelectedPortal.getThisSideState(), Helper.secondToNano(1.0));
                  }
               } else {
                  Portal portal = (Portal)rayTraceResult.getFirst();
                  Vec3d hitPos = ((RayTraceResult)rayTraceResult.getSecond()).hitPos();
                  if (selectedPortalId == null) {
                     selectedPortalId = portal.getUuid();
                  } else if (!Objects.equals(selectedPortalId, portal.getUuid())) {
                     if (originalSelectedPortal == null || !Portal.isFlippedPortal(originalSelectedPortal, portal)) {
                        selectionStatus = null;
                        if (hasLock()) {
                           CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.lock_cleared").formatted(Formatting.field_1065), 5.0, "1_alert");
                           clearLock();
                        }
                     } else if (lockedAnchor != null) {
                        Vec3d lockedAnchorPos = lockedAnchor.getPos(originalSelectedPortal);
                        lockedAnchor = PortalLocalXYNormalized.fromPos(portal, lockedAnchorPos);
                        if (!lockedAnchor.isValid()) {
                           lockedAnchor = null;
                        }
                     }

                     selectedPortalId = portal.getUuid();
                  }

                  PortalLocalXYNormalized rawSelection = PortalLocalXYNormalized.fromPos(portal, hitPos);
                  int cursorAlignment = IPConfig.getConfig().portalWandCursorAlignment;
                  PortalLocalXYNormalized selection = cursorAlignment == 0 ? rawSelection.clamp() : rawSelection.clamp().snapToGrid(cursorAlignment);
                  boolean selectsHorizontalEdge = rawSelection.ny() < 0.0 || rawSelection.ny() > 1.0;
                  boolean selectsVerticalEdge = rawSelection.nx() < 0.0 || rawSelection.nx() > 1.0;
                  selectionStatus = new ClientPortalWandPortalDrag.SelectionStatus(selection, selectsHorizontalEdge, selectsVerticalEdge);
                  Vec3d cursorPos = selection.getPos(portal);
                  cursor.setTarget(new RenderedPoint(new WithDim<>(player.getEntityWorld().getRegistryKey(), cursorPos), 1.0), Helper.secondToNano(0.5));
                  UnilateralPortalState lastRenderedRect = renderedRect.getCurrent();
                  UnilateralPortalState newRenderedRect = portal.getThisSideState();
                  if (lastRenderedRect != null) {
                     Pair<UnilateralPortalState.RectInvariant, UnilateralPortalState> rectTurning = lastRenderedRect.turnToClosestTo(
                        newRenderedRect.orientation()
                     );
                     if (((UnilateralPortalState.RectInvariant)rectTurning.getFirst()).switchesWidthAndHeight()) {
                        Animated<Double> temp = renderedWidthLineSegment;
                        renderedWidthLineSegment = renderedHeightLineSegment;
                        renderedHeightLineSegment = temp;
                     }
                  }

                  renderedRect.setTarget(newRenderedRect, Helper.secondToNano(1.0));
                  Pair<Plane, MutableText> planeInfo = getPlayerFacingPlaneAligned(player, cursorPos, portal);
                  if (selectsHorizontalEdge) {
                     CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.lock_width", new Object[]{minecraft.options.attackKey.getBoundKeyLocalizedText()}));
                     renderedPrePlane.clearTarget();
                  } else if (selectsVerticalEdge) {
                     CustomTextOverlay.putText(
                        Text.translatable("imm_ptl.wand.lock_height", new Object[]{minecraft.options.attackKey.getBoundKeyLocalizedText()})
                     );
                     renderedPrePlane.clearTarget();
                  } else {
                     Text planeText = Text.translatable(
                        "imm_ptl.wand.on_plane", new Object[]{((MutableText)planeInfo.getSecond()).formatted(Formatting.field_1065)}
                     );
                     CustomTextOverlay.putText(
                        Text.translatable(
                           "imm_ptl.wand.pre_drag",
                           new Object[]{minecraft.options.attackKey.getBoundKeyLocalizedText(), minecraft.options.useKey.getBoundKeyLocalizedText(), planeText}
                        )
                     );
                     renderedPrePlane.setTarget(
                        new RenderedPlane(new WithDim<>(player.getEntityWorld().getRegistryKey(), (Plane)planeInfo.getFirst()), 1.0), Helper.secondToNano(0.3)
                     );
                  }
               }
            }
         }
      }
   }

   private static boolean hasLock() {
      return lockedAnchor != null || lockWidth || lockHeight;
   }

   private static void clearLock() {
      lockedAnchor = null;
      lockWidth = false;
      lockHeight = false;
      renderedLockedAnchor.clearTarget();
      renderedWidthLineSegment.clearTarget();
      renderedHeightLineSegment.clearTarget();
      renderedWidthLock.clearTarget();
      renderedHeightLock.clearTarget();
   }

   private static void handleDragging(ClientPlayerEntity player, ClientPortalWandPortalDrag.DraggingContext draggingContext) {
      Vec3d eyePos = player.getCameraPosVec(RenderStates.getPartialTick());
      Vec3d viewVec = player.getRotationVector();
      RegistryKey<World> currDim = player.getEntityWorld().getRegistryKey();
      if (draggingContext.dimension != currDim) {
         stopDragging();
      } else {
         MinecraftClient minecraft = MinecraftClient.getInstance();
         if (!minecraft.options.useKey.isPressed()) {
            stopDragging();
         } else {
            Vec3d cursorPos = null;
            Plane plane = draggingContext.limitingPlane();
            Sphere sphere = draggingContext.limitingSphere;
            int alignment = IPConfig.getConfig().portalWandCursorAlignment;
            if (plane != null) {
               cursorPos = plane.rayTrace(eyePos, viewVec);
               if (cursorPos != null) {
                  cursorPos = align(cursorPos, alignment);
                  cursorPos = plane.getProjection(cursorPos);
               }
            } else if (sphere != null) {
               cursorPos = sphere.rayTrace(eyePos, viewVec);
               if (cursorPos != null) {
                  cursorPos = align(cursorPos, alignment);
                  cursorPos = sphere.projectToSphere(cursorPos);
               }
            }

            if (cursorPos != null) {
               Vec3d originalCursorTarget = cursor.getTarget().pos().value();
               if (originalCursorTarget == null) {
                  return;
               }

               if (originalCursorTarget.distanceTo(cursorPos) > 1.0E-6) {
                  cursor.setTarget(new RenderedPoint(new WithDim<>(currDim, cursorPos), 1.0), Helper.secondToNano(0.2));
                  onDrag(cursorPos, draggingContext);
               }
            }

            MutableText draggingText = Text.translatable("imm_ptl.wand.dragging");
            Text planeText = draggingContext.planeText() == null
               ? Text.literal("")
               : Text.translatable("imm_ptl.wand.on_plane", new Object[]{draggingContext.planeText()});
            MutableText undoPrompt = Text.literal("\n")
               .append(Text.translatable("imm_ptl.wand.left_click_to_undo", new Object[]{minecraft.options.attackKey.getBoundKeyLocalizedText()}));
            MutableText cursorPosText = cursorPos == null
               ? Text.literal("")
               : Text.literal("\n")
                  .append(
                     Text.translatable(
                        "imm_ptl.wand.cursor_pos",
                        new Object[]{"%.3f".formatted(cursorPos.x), "%.3f".formatted(cursorPos.y), "%.3f".formatted(cursorPos.z)}
                     )
                  );
            Portal portal = WandUtil.getClientPortalByUUID(draggingContext.portalId());
            boolean shouldDisplaySize = lockedAnchor != null;
            PortalState animationEndingState = getAnimationEndingState();
            Text portalSizeText;
            if (portal != null && shouldDisplaySize && animationEndingState != null) {
               portalSizeText = Text.literal(" ")
                  .append(
                     Text.translatable(
                        "imm_ptl.wand.portal_size",
                        new Object[]{
                           "%.3f".formatted(animationEndingState.width),
                           "%.3f".formatted(animationEndingState.height),
                           "%.3f".formatted(animationEndingState.scaling)
                        }
                     )
                  );
            } else {
               portalSizeText = Text.literal("");
            }

            CustomTextOverlay.putText(draggingText.append(planeText).append(undoPrompt).append(cursorPosText).append(portalSizeText));
         }
      }
   }

   private static Vec3d align(Vec3d pos, int alignment) {
      return alignment == 0
         ? pos
         : new Vec3d(
            (double)Math.round(pos.getX() * alignment) / alignment,
            (double)Math.round(pos.getY() * alignment) / alignment,
            (double)Math.round(pos.getZ() * alignment) / alignment
         );
   }

   private static void startDragging() {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (isDragging()) {
            LOGGER.error("start dragging when already dragging");
         } else if (!MinecraftClient.getInstance().options.useKey.isPressed()) {
            LOGGER.error("start dragging when not right clicking");
         } else {
            Portal portal = getSelectedPortal();
            if (portal != null) {
               RenderedPoint cursorTarget = cursor.getTarget();
               if (cursorTarget != null) {
                  WithDim<Vec3d> cursorTargetPos = cursorTarget.pos();
                  if (cursorTargetPos != null) {
                     Vec3d cursorPos = cursorTargetPos.value();
                     if (cursorPos != null) {
                        if (selectionStatus != null) {
                           PortalLocalXYNormalized selectedAnchor = selectionStatus.selectedAnchor;
                           if (lockedAnchor == null || !lockedAnchor.isCloseTo(selectedAnchor, 0.01)) {
                              PortalWandInteraction.DraggingInfo draggingInfo = new PortalWandInteraction.DraggingInfo(
                                 lockedAnchor, selectedAnchor, null, lockWidth, lockHeight
                              );
                              RegistryKey<World> currDim = player.getEntityWorld().getRegistryKey();
                              Pair<Plane, MutableText> info = getPlayerFacingPlaneAligned(player, cursorPos, portal);
                              Plane plane = (Plane)info.getFirst();
                              renderedPlane.setTarget(new RenderedPlane(new WithDim<>(currDim, plane), 1.0), Helper.secondToNano(0.5));
                              renderedPrePlane.clearTarget();
                              draggingContext = new ClientPortalWandPortalDrag.DraggingContext(
                                 currDim,
                                 portal.getUuid(),
                                 plane,
                                 null,
                                 draggingInfo,
                                 ((MutableText)info.getSecond()).formatted(Formatting.field_1065),
                                 portal.getPortalState()
                              );
                           }
                        }
                     }
                  }
               }
            }
         }
      }
   }

   private static void stopDragging() {
      draggingContext = null;
      renderedPlane.clearTarget();
      renderedRect.clearTarget();
      McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.finishDragging");
   }

   private static void undoDragging() {
      McRemoteProcedureCall.tellServerToInvoke("qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.undoDrag");
      renderedPlane.clearTarget();
      renderedRect.clearTarget();
      draggingContext = null;
      isUndoing = true;
   }

   private static void onDrag(Vec3d cursorPos, ClientPortalWandPortalDrag.DraggingContext draggingContext) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         Portal portal = WandUtil.getClientPortalByUUID(draggingContext.portalId());
         if (portal == null) {
            LOGGER.error("cannot find portal to drag {}", selectedPortalId);
            reset();
         } else {
            PortalWandInteraction.DraggingInfo draggingInfo = draggingContext.draggingInfo();
            UnilateralPortalState newThisSideState = PortalWandInteraction.applyDrag(
               draggingContext.originalPortalState().getThisSideState(), cursorPos, draggingInfo, true
            );
            boolean isValid = PortalWandInteraction.validateDraggedPortalState(draggingContext.originalPortalState(), newThisSideState, player);
            if (isValid) {
               McRemoteProcedureCall.tellServerToInvoke(
                  "qouteall.imm_ptl.peripheral.wand.PortalWandInteraction.RemoteCallables.requestApplyDrag", selectedPortalId, cursorPos, draggingInfo
               );
               CustomTextOverlay.remove("1_invalid_dragging");
            } else {
               CustomTextOverlay.putText(Text.translatable("imm_ptl.wand.invalid_dragging").formatted(Formatting.field_1061), 3.0, "1_invalid_dragging");
            }
         }
      }
   }

   @Nullable
   private static Portal getSelectedPortal() {
      return WandUtil.getClientPortalByUUID(selectedPortalId);
   }

   private static Pair<Plane, MutableText> getPlayerFacingPlaneAligned(PlayerEntity player, Vec3d cursorPos, Portal portal) {
      ArrayList<Pair<Plane, MutableText>> candidates = new ArrayList<>();
      Vec3d X = new Vec3d(1.0, 0.0, 0.0);
      Vec3d Y = new Vec3d(0.0, 1.0, 0.0);
      Vec3d Z = new Vec3d(0.0, 0.0, 1.0);
      candidates.add(Pair.of(new Plane(cursorPos, X), Text.translatable("imm_ptl.wand.plane.x")));
      candidates.add(Pair.of(new Plane(cursorPos, Y), Text.translatable("imm_ptl.wand.plane.y")));
      candidates.add(Pair.of(new Plane(cursorPos, Z), Text.translatable("imm_ptl.wand.plane.z")));
      Predicate<Vec3d> isOrthodox = p -> Math.abs(p.dotProduct(X)) > 0.9999 || Math.abs(p.dotProduct(Y)) > 0.9999 || Math.abs(p.dotProduct(Z)) > 0.9999;
      if (!isOrthodox.test(portal.getAxisW())) {
         candidates.add(Pair.of(new Plane(cursorPos, portal.getAxisW()), Text.translatable("imm_ptl.wand.plane.portal_x")));
      }

      if (!isOrthodox.test(portal.getAxisH())) {
         candidates.add(Pair.of(new Plane(cursorPos, portal.getAxisH()), Text.translatable("imm_ptl.wand.plane.portal_y")));
      }

      if (!isOrthodox.test(portal.getNormal())) {
         candidates.add(Pair.of(new Plane(cursorPos, portal.getNormal()), Text.translatable("imm_ptl.wand.plane.portal_z")));
      }

      Vec3d viewVec = player.getRotationVector();
      return Stream.concat(candidates.stream(), candidates.stream().map(p -> Pair.of(((Plane)p.getFirst()).getOpposite(), (MutableText)p.getSecond())))
         .min(Comparator.comparingDouble(p -> ((Plane)p.getFirst()).normal().dotProduct(viewVec)))
         .orElseThrow();
   }

   public static void render(MatrixStack matrixStack, class_4598 bufferSource, double camX, double camY, double camZ) {
      ClientPlayerEntity player = MinecraftClient.getInstance().player;
      if (player != null) {
         if (!PortalRendering.isRendering()) {
            renderedWidthLock.setTarget(lockWidth ? 1.0 : 0.0, Helper.secondToNano(0.5));
            renderedHeightLock.setTarget(lockHeight ? 1.0 : 0.0, Helper.secondToNano(0.5));
            renderedWidthLineSegment.setTarget(
               !lockWidth && (selectionStatus == null || !selectionStatus.selectsHorizontalEdge || draggingContext != null) ? 0.0 : 1.0,
               Helper.secondToNano(0.5)
            );
            renderedHeightLineSegment.setTarget(
               !lockHeight && (selectionStatus == null || !selectionStatus.selectsVerticalEdge || draggingContext != null) ? 0.0 : 1.0,
               Helper.secondToNano(0.5)
            );
            RegistryKey<World> currDim = player.getEntityWorld().getRegistryKey();
            Vec3d cameraPos = new Vec3d(camX, camY, camZ);
            VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderLayers.lines());
            Vec3d renderedCursor = getCursorToRender();
            if (renderedCursor != null) {
               WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, renderedCursor, -16515844, cursor.getCurrent().scale(), matrixStack);
            }

            Portal portal = null;
            if (draggingContext != null) {
               portal = WandUtil.getClientPortalByUUID(draggingContext.portalId);
            }

            if (portal == null && selectedPortalId != null) {
               portal = getSelectedPortal();
            }

            UnilateralPortalState rect = null;
            if (draggingContext != null) {
               if (portal != null) {
                  rect = portal.getThisSideState();
                  renderRect(matrixStack, cameraPos, vertexConsumer, rect);
                  renderedRect.setTarget(rect, 0L);
               }
            } else {
               rect = renderedRect.getCurrent();
               if (rect != null && rect.dimension() == currDim) {
                  renderRect(matrixStack, cameraPos, vertexConsumer, rect);
               }
            }

            if (lockedAnchor != null && portal != null) {
               renderedLockedAnchor.setTarget(
                  new RenderedPoint(new WithDim<>(portal.getOriginDim(), lockedAnchor.getPos(portal)), 1.0), Helper.secondToNano(0.5)
               );
            } else {
               renderedLockedAnchor.clearTarget();
            }

            if (portal != null) {
               RenderedPoint current = renderedLockedAnchor.getCurrent();
               if (current != null && current.pos() != null && current.pos().dimension() == currDim) {
                  Vec3d lockPos = current.pos().value();
                  if (draggingContext != null && lockedAnchor != null) {
                     lockPos = lockedAnchor.getPos(portal);
                  }

                  double lockExtraScale = getLockExtraScale(portal.getThisSideState());
                  matrixStack.push();
                  matrixStack.scale(0.5F, 0.5F, 0.5F);
                  WireRenderingHelper.renderLockShape(vertexConsumer, cameraPos, lockPos, current.scale() * lockExtraScale, -1, matrixStack);
                  matrixStack.pop();
                  WireRenderingHelper.renderSmallCubeFrame(vertexConsumer, cameraPos, lockPos, -384160, current.scale() * lockExtraScale, matrixStack);
               }

               renderWidthHeightLock(matrixStack, cameraPos, vertexConsumer, portal.getThisSideState());
            }

            if (rect != null) {
               renderWidthHeightLineSegment(matrixStack, cameraPos, vertexConsumer, rect);
            }

            VertexConsumer debugLineStripConsumer = bufferSource.getBuffer(IPRenderTypes.debugLineStrip());
            RenderedPlane plane = renderedPlane.getCurrent();
            if (plane != null && plane.plane() != null && plane.plane().dimension() == currDim) {
               Plane planeValue = plane.plane().value();
               if (renderedCursor != null) {
                  planeValue = planeValue.getParallelPlane(renderedCursor);
               }

               WireRenderingHelper.renderPlane(debugLineStripConsumer, cameraPos, planeValue, plane.scale(), -1, matrixStack, true);
            }

            RenderedPlane prePlane = renderedPrePlane.getCurrent();
            if (prePlane != null && prePlane.plane() != null && prePlane.plane().dimension() == currDim) {
               Plane planeValue = prePlane.plane().value();
               if (renderedCursor != null) {
                  planeValue = planeValue.getParallelPlane(renderedCursor);
               }

               WireRenderingHelper.renderCircle(
                  debugLineStripConsumer, cameraPos, new Circle(planeValue, planeValue.pos(), prePlane.scale() * 0.5), -1, matrixStack
               );
            }
         }
      }
   }

   @Nullable
   private static Vec3d getCursorToRender() {
      if (draggingContext != null) {
         Portal portal = WandUtil.getClientPortalByUUID(draggingContext.portalId);
         return portal != null ? draggingContext.draggingInfo().draggingAnchor.getPos(portal) : null;
      } else {
         RenderedPoint renderedPoint = cursor.getCurrent();

         assert renderedPoint != null;

         WithDim<Vec3d> pos = renderedPoint.pos();
         return pos == null ? null : pos.value();
      }
   }

   public static void renderRect(MatrixStack matrixStack, Vec3d cameraPos, VertexConsumer vertexConsumer, UnilateralPortalState rect) {
      WireRenderingHelper.renderRectFrameFlow(matrixStack, cameraPos, vertexConsumer, rect, -1309700, -218877);
   }

   private static double getLockExtraScale(UnilateralPortalState rect) {
      return Math.sqrt(rect.width() * rect.height()) * 0.2;
   }

   private static void renderWidthHeightLock(MatrixStack matrixStack, Vec3d cameraPos, VertexConsumer vertexConsumer, UnilateralPortalState rect) {
      matrixStack.push();
      matrixStack.scale(0.5F, 0.5F, 0.5F);
      double lockPadding = 0.1;
      double lockExtraScale = getLockExtraScale(rect);
      Double widthLockAnim = renderedWidthLock.getCurrent();
      if (widthLockAnim != null && widthLockAnim > 0.001) {
         WireRenderingHelper.renderLockShape(
            vertexConsumer, cameraPos, new PortalLocalXYNormalized(0.5, -lockPadding).getPos(rect), widthLockAnim * lockExtraScale, -16516018, matrixStack
         );
         WireRenderingHelper.renderLockShape(
            vertexConsumer, cameraPos, new PortalLocalXYNormalized(0.5, 1.0 + lockPadding).getPos(rect), widthLockAnim * lockExtraScale, -16516018, matrixStack
         );
      }

      Double heightLockAnim = renderedHeightLock.getCurrent();
      if (heightLockAnim != null && heightLockAnim > 0.001) {
         WireRenderingHelper.renderLockShape(
            vertexConsumer, cameraPos, new PortalLocalXYNormalized(-lockPadding, 0.5).getPos(rect), heightLockAnim * lockExtraScale, -198653, matrixStack
         );
         WireRenderingHelper.renderLockShape(
            vertexConsumer, cameraPos, new PortalLocalXYNormalized(1.0 + lockPadding, 0.5).getPos(rect), heightLockAnim * lockExtraScale, -198653, matrixStack
         );
      }

      matrixStack.pop();
   }

   private static void renderWidthHeightLineSegment(MatrixStack matrixStack, Vec3d cameraPos, VertexConsumer vertexConsumer, UnilateralPortalState rect) {
      matrixStack.push();
      matrixStack.scale(0.5F, 0.5F, 0.5F);
      double padding1 = 0.04;
      double padding2 = padding1 + 0.02;
      double padding3 = padding2 + 0.03;
      Double widthLineAnim = renderedWidthLineSegment.getCurrent();
      if (widthLineAnim != null && widthLineAnim > 0.001) {
         double rangeStart = 0.5 - widthLineAnim / 2.0;
         double rangeEnd = 0.5 + widthLineAnim / 2.0;
         WireRenderingHelper.renderLines(
            vertexConsumer,
            cameraPos,
            rect.position(),
            new Vec3d[]{
               new PortalLocalXYNormalized(rangeStart, -padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, -padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeStart, 1.0 + padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, 1.0 + padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeStart, -padding1).getOffset(rect),
               new PortalLocalXYNormalized(rangeStart, -padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, -padding1).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, -padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeStart, 1.0 + padding1).getOffset(rect),
               new PortalLocalXYNormalized(rangeStart, 1.0 + padding2).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, 1.0 + padding1).getOffset(rect),
               new PortalLocalXYNormalized(rangeEnd, 1.0 + padding2).getOffset(rect),
               new PortalLocalXYNormalized(0.5, -padding2).getOffset(rect),
               new PortalLocalXYNormalized(0.5, -padding3).getOffset(rect),
               new PortalLocalXYNormalized(0.5, 1.0 + padding2).getOffset(rect),
               new PortalLocalXYNormalized(0.5, 1.0 + padding3).getOffset(rect)
            },
            1.0,
            DQuaternion.identity,
            -16516018,
            matrixStack
         );
      }

      Double heightLineAnim = renderedHeightLineSegment.getCurrent();
      if (heightLineAnim != null && heightLineAnim > 0.001) {
         double rangeStart = 0.5 - heightLineAnim / 2.0;
         double rangeEnd = 0.5 + heightLineAnim / 2.0;
         WireRenderingHelper.renderLines(
            vertexConsumer,
            cameraPos,
            rect.position(),
            new Vec3d[]{
               new PortalLocalXYNormalized(-padding2, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(-padding2, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding2, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding2, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(-padding1, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(-padding2, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(-padding1, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(-padding2, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding1, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding2, rangeStart).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding1, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding2, rangeEnd).getOffset(rect),
               new PortalLocalXYNormalized(-padding2, 0.5).getOffset(rect),
               new PortalLocalXYNormalized(-padding3, 0.5).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding2, 0.5).getOffset(rect),
               new PortalLocalXYNormalized(1.0 + padding3, 0.5).getOffset(rect)
            },
            1.0,
            DQuaternion.identity,
            -198653,
            matrixStack
         );
      }

      matrixStack.pop();
   }

   private static PortalCorner getClosestCorner(Portal portal, Vec3d hitPos) {
      return Arrays.stream(PortalCorner.values()).min(Comparator.comparingDouble(corner -> corner.getPos(portal).distanceTo(hitPos))).orElseThrow();
   }

   private static void updateDraggedPortalAnimation() {
      if (draggingContext != null) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null) {
            RenderedPoint renderedCursor = cursor.getCurrent();

            assert renderedCursor != null;

            Vec3d currentCursor = renderedCursor.pos() == null ? null : renderedCursor.pos().value();
            if (currentCursor != null) {
               Portal selectedPortal = getSelectedPortal();
               if (selectedPortal != null) {
                  UnilateralPortalState newState = PortalWandInteraction.applyDrag(
                     draggingContext.originalPortalState().getThisSideState(), currentCursor, draggingContext.draggingInfo, false
                  );
                  if (PortalWandInteraction.validateDraggedPortalState(draggingContext.originalPortalState(), newState, player)) {
                     PortalState newFullState = draggingContext.originalPortalState()
                        .withThisSideUpdated(newState, draggingContext.draggingInfo.shouldLockScale());
                     selectedPortal.setPortalState(newFullState);
                     selectedPortal.rectifyClusterPortals(false);
                  }
               }
            }
         }
      }
   }

   @Nullable
   private static PortalState getAnimationEndingState() {
      if (draggingContext == null) {
         return null;
      } else {
         Portal portal = WandUtil.getClientPortalByUUID(draggingContext.portalId());
         if (portal == null) {
            return null;
         } else {
            RenderedPoint renderedCursorTarget = cursor.getTarget();

            assert renderedCursorTarget != null;

            Vec3d cursorTarget = renderedCursorTarget.pos() == null ? null : renderedCursorTarget.pos().value();
            if (cursorTarget == null) {
               return null;
            } else {
               UnilateralPortalState newThisSide = PortalWandInteraction.applyDrag(
                  draggingContext.originalPortalState().getThisSideState(), cursorTarget, draggingContext.draggingInfo(), false
               );
               return newThisSide == null
                  ? null
                  : draggingContext.originalPortalState().withThisSideUpdated(newThisSide, draggingContext.draggingInfo().shouldLockScale());
            }
         }
      }
   }

   private record DraggingContext(
      RegistryKey<World> dimension,
      @NotNull UUID portalId,
      @Nullable Plane limitingPlane,
      @Nullable Sphere limitingSphere,
      PortalWandInteraction.DraggingInfo draggingInfo,
      @Nullable MutableText planeText,
      PortalState originalPortalState
   ) {
   }

   private record SelectionStatus(PortalLocalXYNormalized selectedAnchor, boolean selectsHorizontalEdge, boolean selectsVerticalEdge) {
   }
}
