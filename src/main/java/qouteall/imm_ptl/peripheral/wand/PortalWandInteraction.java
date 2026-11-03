package qouteall.imm_ptl.peripheral.wand;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.command.DefaultPermissions;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalExtension;
import qouteall.imm_ptl.core.portal.PortalManipulation;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;
import qouteall.imm_ptl.core.portal.util.PortalLocalXYNormalized;
import qouteall.imm_ptl.peripheral.CommandStickItem;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Plane;
import qouteall.q_misc_util.my_util.Range;
import qouteall.q_misc_util.platform.Platform;

public class PortalWandInteraction {
   private static final double SIZE_LIMIT = 64.0;
   private static final Logger LOGGER = LogUtils.getLogger();
   private final WeakHashMap<ServerPlayerEntity, PortalWandInteraction.DraggingSession> draggingSessionMap = new WeakHashMap<>();
   private static final WeakHashMap<ServerPlayerEntity, PortalWandInteraction.CopyingSession> copyingSessionMap = new WeakHashMap<>();

   public static PortalWandInteraction of(MinecraftServer server) {
      return IPPerServerInfo.of(server).portalWandInteraction;
   }

   private static void handleFinishPortalCreation(ServerPlayerEntity player, ProtoPortal protoPortal) {
      Validate.isTrue(protoPortal.firstSide != null);
      Validate.isTrue(protoPortal.secondSide != null);
      Vec3d firstSideLeftBottom = protoPortal.firstSide.leftBottom;
      Vec3d firstSideRightBottom = protoPortal.firstSide.rightBottom;
      Vec3d firstSideLeftUp = protoPortal.firstSide.leftTop;
      Vec3d secondSideLeftBottom = protoPortal.secondSide.leftBottom;
      Vec3d secondSideRightBottom = protoPortal.secondSide.rightBottom;
      Vec3d secondSideLeftUp = protoPortal.secondSide.leftTop;
      Validate.notNull(firstSideLeftBottom);
      Validate.notNull(firstSideRightBottom);
      Validate.notNull(firstSideLeftUp);
      Validate.notNull(secondSideLeftBottom);
      Validate.notNull(secondSideRightBottom);
      Validate.notNull(secondSideLeftUp);
      RegistryKey<World> firstSideDimension = protoPortal.firstSide.dimension;
      RegistryKey<World> secondSideDimension = protoPortal.secondSide.dimension;
      Vec3d firstSideHorizontalAxis = firstSideRightBottom.subtract(firstSideLeftBottom);
      Vec3d firstSideVerticalAxis = firstSideLeftUp.subtract(firstSideLeftBottom);
      double firstSideWidth = firstSideHorizontalAxis.length();
      double firstSideHeight = firstSideVerticalAxis.length();
      Vec3d firstSideHorizontalUnitAxis = firstSideHorizontalAxis.normalize();
      Vec3d firstSideVerticalUnitAxis = firstSideVerticalAxis.normalize();
      if (Math.abs(firstSideWidth) < 0.001 || Math.abs(firstSideHeight) < 0.001) {
         player.sendMessage(Text.literal("The first side is too small"));
         LOGGER.error("The first side is too small");
      } else if (firstSideHorizontalUnitAxis.dotProduct(firstSideVerticalUnitAxis) > 0.001) {
         player.sendMessage(Text.literal("The horizontal and vertical axis are not perpendicular in first side"));
         LOGGER.error("The horizontal and vertical axis are not perpendicular in first side");
      } else if (!(firstSideWidth > 64.0) && !(firstSideHeight > 64.0)) {
         Vec3d secondSideHorizontalAxis = secondSideRightBottom.subtract(secondSideLeftBottom);
         Vec3d secondSideVerticalAxis = secondSideLeftUp.subtract(secondSideLeftBottom);
         double secondSideWidth = secondSideHorizontalAxis.length();
         double secondSideHeight = secondSideVerticalAxis.length();
         Vec3d secondSideHorizontalUnitAxis = secondSideHorizontalAxis.normalize();
         Vec3d secondSideVerticalUnitAxis = secondSideVerticalAxis.normalize();
         if (Math.abs(secondSideWidth) < 0.001 || Math.abs(secondSideHeight) < 0.001) {
            player.sendMessage(Text.literal("The second side is too small"));
            LOGGER.error("The second side is too small");
         } else if (secondSideHorizontalUnitAxis.dotProduct(secondSideVerticalUnitAxis) > 0.001) {
            player.sendMessage(Text.literal("The horizontal and vertical axis are not perpendicular in second side"));
            LOGGER.error("The horizontal and vertical axis are not perpendicular in second side");
         } else if (secondSideWidth > 64.0 || secondSideHeight > 64.0) {
            player.sendMessage(Text.literal("The second side is too large"));
            LOGGER.error("The second side is too large");
         } else if (Math.abs(firstSideHeight / firstSideWidth - secondSideHeight / secondSideWidth) > 0.001) {
            player.sendMessage(Text.literal("The two sides have different aspect ratio"));
            LOGGER.error("The two sides have different aspect ratio");
         } else {
            boolean overlaps = false;
            if (firstSideDimension == secondSideDimension) {
               Vec3d firstSideNormal = firstSideHorizontalUnitAxis.crossProduct(firstSideVerticalUnitAxis);
               Vec3d secondSideNormal = secondSideHorizontalUnitAxis.crossProduct(secondSideVerticalUnitAxis);
               if (Math.abs(firstSideNormal.dotProduct(secondSideNormal)) > 0.99
                  && Math.abs(firstSideLeftBottom.subtract(secondSideLeftBottom).dotProduct(firstSideNormal)) < 0.001) {
                  Range firstSideXRange = Range.createUnordered(
                     firstSideLeftBottom.subtract(firstSideLeftBottom).dotProduct(firstSideHorizontalAxis),
                     firstSideRightBottom.subtract(firstSideLeftBottom).dotProduct(firstSideHorizontalAxis)
                  );
                  Range firstSideYRange = Range.createUnordered(
                     firstSideLeftBottom.subtract(firstSideLeftBottom).dotProduct(firstSideVerticalAxis),
                     firstSideLeftUp.subtract(firstSideLeftBottom).dotProduct(firstSideVerticalAxis)
                  );
                  Range secondSideXRange = Range.createUnordered(
                     secondSideLeftBottom.subtract(firstSideLeftBottom).dotProduct(firstSideHorizontalAxis),
                     secondSideRightBottom.subtract(firstSideLeftBottom).dotProduct(firstSideHorizontalAxis)
                  );
                  Range secondSideYRange = Range.createUnordered(
                     secondSideLeftBottom.subtract(firstSideLeftBottom).dotProduct(firstSideVerticalAxis),
                     secondSideLeftUp.subtract(firstSideLeftBottom).dotProduct(firstSideVerticalAxis)
                  );
                  if (firstSideXRange.intersect(secondSideXRange) != null && firstSideYRange.intersect(secondSideYRange) != null) {
                     overlaps = true;
                  }
               }
            }

            Portal portal = (Portal)Portal.ENTITY_TYPE.create(McHelper.getServerWorld(firstSideDimension), SpawnReason.TRIGGERED);
            Validate.notNull(portal);
            portal.setOriginPos(firstSideLeftBottom.add(firstSideHorizontalAxis.multiply(0.5)).add(firstSideVerticalAxis.multiply(0.5)));
            portal.setWidth(firstSideWidth);
            portal.setHeight(firstSideHeight);
            portal.setAxisW(firstSideHorizontalUnitAxis);
            portal.setAxisH(firstSideVerticalUnitAxis);
            portal.setDestDim(secondSideDimension);
            portal.setDestination(secondSideLeftBottom.add(secondSideHorizontalAxis.multiply(0.5)).add(secondSideVerticalAxis.multiply(0.5)));
            portal.setScaling(secondSideWidth / firstSideWidth);
            DQuaternion secondSideOrientation = DQuaternion.matrixToQuaternion(
               secondSideHorizontalUnitAxis, secondSideVerticalUnitAxis, secondSideHorizontalUnitAxis.crossProduct(secondSideVerticalUnitAxis)
            );
            portal.setOtherSideOrientation(secondSideOrientation);
            Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
            Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
            Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
            McHelper.spawnServerEntity(portal);
            if (overlaps) {
               player.sendMessage(Text.translatable("imm_ptl.wand.overlap"));
            } else {
               McHelper.spawnServerEntity(flippedPortal);
               McHelper.spawnServerEntity(reversePortal);
               McHelper.spawnServerEntity(parallelPortal);
            }

            player.sendMessage(Text.translatable("imm_ptl.wand.finished"));
            giveCommandStick(player, "/portal eradicate_portal_cluster");
         }
      } else {
         player.sendMessage(Text.literal("The first side is too large"));
         LOGGER.error("The first side is too large");
      }
   }

   public static void init() {
      Platform.get().onServerTickEnd(server -> {
         of(server).draggingSessionMap.entrySet().removeIf(e -> {
            ServerPlayerEntity player = e.getKey();
            return player.isRemoved() ? true : player.getMainHandStack().getItem() != PortalWandItem.instance;
         });
         copyingSessionMap.entrySet().removeIf(e -> {
            ServerPlayerEntity player = e.getKey();
            return player.isRemoved();
         });
      });
      IPGlobal.SERVER_CLEANUP_EVENT.register(s -> of(s).draggingSessionMap.clear());
      IPGlobal.SERVER_CLEANUP_EVENT.register(s -> copyingSessionMap.clear());
   }

   @Nullable
   public static UnilateralPortalState applyDrag(
      UnilateralPortalState originalState, Vec3d cursorPos, PortalWandInteraction.DraggingInfo info, boolean updateInternalState
   ) {
      if (info.lockedAnchor == null) {
         Vec3d offset = info.draggingAnchor.getOffset(originalState);
         Vec3d newPos = cursorPos.subtract(offset);
         return new UnilateralPortalState.Builder().from(originalState).position(newPos).build();
      } else {
         PortalWandInteraction.OneLockDraggingResult r = performDragWithOneLockedAnchor(
            originalState, info.lockedAnchor, info.draggingAnchor, cursorPos, info.previousRotationAxis, info.lockWidth, info.lockHeight
         );
         if (r == null) {
            return null;
         } else {
            if (updateInternalState) {
               info.previousRotationAxis = r.rotationAxis();
            }

            return r.newState();
         }
      }
   }

   private static void handleFinishDrag(ServerPlayerEntity player) {
      PortalWandInteraction.DraggingSession session = of(player.server).draggingSessionMap.remove(player);
      if (session != null) {
         Portal portal = session.getPortal();
         if (portal != null) {
            portal.reloadAndSyncToClientNextTick();
         }
      }
   }

   private static void handleUndoDrag(ServerPlayerEntity player) {
      PortalWandInteraction portalWandInteraction = of(player.server);
      PortalWandInteraction.DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
      if (session != null) {
         Portal portal = session.getPortal();
         if (portal == null) {
            LOGGER.error("Cannot find portal {}", session.portalId);
         } else {
            portal.setPortalState(session.originalState);
            portal.reloadAndSyncToClientNextTick();
            portal.rectifyClusterPortals(true);
            portalWandInteraction.draggingSessionMap.remove(player);
         }
      }
   }

   private static void handleDraggingRequest(
      ServerPlayerEntity player, UUID portalId, Vec3d cursorPos, PortalWandInteraction.DraggingInfo draggingInfo, Portal portal
   ) {
      PortalWandInteraction portalWandInteraction = of(player.server);
      PortalWandInteraction.DraggingSession session = portalWandInteraction.draggingSessionMap.get(player);
      if (session == null || !session.portalId.equals(portalId)) {
         session = new PortalWandInteraction.DraggingSession(player.getEntityWorld().getRegistryKey(), portalId, portal.getPortalState(), draggingInfo);
         portalWandInteraction.draggingSessionMap.put(player, session);
      }

      UnilateralPortalState newThisSideState = applyDrag(session.originalState.getThisSideState(), cursorPos, draggingInfo, true);
      if (validateDraggedPortalState(session.originalState, newThisSideState, player)) {
         portal.setThisSideState(newThisSideState, draggingInfo.shouldLockScale());
         portal.reloadAndSyncToClientNextTick();
         portal.rectifyClusterPortals(true);
      } else {
         player.sendMessage(Text.literal("Invalid dragging"));
      }
   }

   private static boolean checkPermission(ServerPlayerEntity player) {
      if (!canPlayerUsePortalWand(player)) {
         player.sendMessage(Text.literal("You cannot use portal wand"));
         LOGGER.error("Player cannot use portal wand {}", player);
         return false;
      } else {
         return true;
      }
   }

   public static boolean validateDraggedPortalState(PortalState originalState, UnilateralPortalState newThisSideState, PlayerEntity player) {
      if (newThisSideState == null) {
         return false;
      } else if (newThisSideState.width() > 64.1) {
         return false;
      } else if (newThisSideState.height() > 64.1) {
         return false;
      } else if (newThisSideState.width() < 0.05) {
         return false;
      } else if (newThisSideState.height() < 0.05) {
         return false;
      } else {
         return originalState.fromWorld != newThisSideState.dimension() ? false : !(newThisSideState.position().distanceTo(player.getEntityPos()) > 64.0);
      }
   }

   private static boolean canPlayerUsePortalWand(ServerPlayerEntity player) {
      return player.getPermissions().hasPermission(DefaultPermissions.GAMEMASTERS)
         || IPGlobal.easeCreativePermission && player.isCreative()
         || IPConfig.getConfig().portalWandUsableOnSurvivalMode && player.interactionManager.getGameMode() == GameMode.SURVIVAL;
   }

   private static void giveCommandStick(ServerPlayerEntity player, String command) {
      CommandStickItem.Data data = CommandStickItem.BUILT_IN_COMMAND_STICK_TYPES.get(command);
      if (data == null) {
         data = new CommandStickItem.Data(command, command, List.of());
      }

      ItemStack stack = new ItemStack(CommandStickItem.instance);
      stack.set(CommandStickItem.COMPONENT_TYPE, data);
      if (!player.getInventory().contains(stack)) {
         player.getInventory().insertStack(stack);
      }
   }

   public static boolean isDragging(ServerPlayerEntity player) {
      return of(player.server).draggingSessionMap.containsKey(player);
   }

   @Nullable
   public static PortalWandInteraction.OneLockDraggingResult performDragWithOneLockedAnchor(
      UnilateralPortalState originalState,
      PortalLocalXYNormalized lockedLocalPos,
      PortalLocalXYNormalized draggingLocalPos,
      Vec3d draggedPos,
      @Nullable Vec3d previousRotationAxis,
      boolean lockWidth,
      boolean lockHeight
   ) {
      Vec3d draggedPosOriginalPos = draggingLocalPos.getPos(originalState);
      Vec3d lockedPos = lockedLocalPos.getPos(originalState);
      Vec3d originalOffset = draggedPosOriginalPos.subtract(lockedPos);
      Vec3d newOffset = draggedPos.subtract(lockedPos);
      double newOffsetLen = newOffset.length();
      double originalOffsetLen = originalOffset.length();
      if (!(newOffsetLen < 1.0E-5) && !(originalOffsetLen < 1.0E-5)) {
         Vec3d originalOffsetN = originalOffset.normalize();
         Vec3d newOffsetN = newOffset.normalize();
         double dot = originalOffsetN.dotProduct(newOffsetN);
         DQuaternion rotation;
         if (Math.abs(dot) < 0.99999) {
            rotation = DQuaternion.getRotationBetween(originalOffset, newOffset).fixFloatingPointErrorAccumulation();
         } else if (dot > 0.0) {
            rotation = DQuaternion.identity;
         } else {
            Plane planeOfPossibleAxis = new Plane(Vec3d.ZERO, originalOffsetN);
            if (previousRotationAxis != null) {
               Vec3d projected = planeOfPossibleAxis.getProjection(previousRotationAxis);
               if (projected.lengthSquared() < 1.0E-5) {
                  return null;
               }

               Vec3d axis = projected.normalize();
               rotation = DQuaternion.rotationByDegrees(axis, 180.0).fixFloatingPointErrorAccumulation();
            } else {
               rotation = DQuaternion.identity;
            }
         }

         DQuaternion newOrientation = rotation.hamiltonProduct(originalState.orientation()).fixFloatingPointErrorAccumulation();
         PortalLocalXYNormalized deltaLocalXY = draggingLocalPos.subtract(lockedLocalPos);
         double newHeight;
         double newWidth;
         if (lockWidth && lockHeight) {
            newWidth = originalState.width();
            newHeight = originalState.height();
         } else {
            Vec3d newNormal = rotation.rotate(originalState.getNormal());
            if (lockWidth) {
               assert !lockHeight;

               newWidth = originalState.width();
               if (Math.abs(deltaLocalXY.ny()) < 0.001) {
                  newHeight = originalState.height();
               } else {
                  double subWidth = Math.abs(deltaLocalXY.nx()) * newWidth;
                  double diff = newOffsetLen * newOffsetLen - subWidth * subWidth;
                  if (diff < 1.0E-6) {
                     return null;
                  }

                  double subHeight = Math.sqrt(diff);
                  newHeight = subHeight / Math.abs(deltaLocalXY.ny());
                  if (Math.abs(subWidth) > 0.001) {
                     newOrientation = getOrientationByNormalDiagonalWidthHeight(
                        newNormal, newOffset, subWidth, subHeight, Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                     );
                  }
               }
            } else if (lockHeight) {
               assert !lockWidth;

               newHeight = originalState.height();
               if (Math.abs(deltaLocalXY.nx()) < 0.001) {
                  newWidth = originalState.width();
               } else {
                  double subHeight = Math.abs(deltaLocalXY.ny()) * newHeight;
                  double diffx = newOffsetLen * newOffsetLen - subHeight * subHeight;
                  if (diffx < 1.0E-6) {
                     return null;
                  }

                  double subWidthx = Math.sqrt(diffx);
                  newWidth = subWidthx / Math.abs(deltaLocalXY.nx());
                  if (Math.abs(subHeight) > 0.001) {
                     newOrientation = getOrientationByNormalDiagonalWidthHeight(
                        newNormal, newOffset, subWidthx, subHeight, Math.signum(deltaLocalXY.nx()), Math.signum(deltaLocalXY.ny())
                     );
                  }
               }
            } else {
               double scaling = newOffsetLen / originalOffsetLen;
               newWidth = originalState.width() * scaling;
               newHeight = originalState.height() * scaling;
            }
         }

         Vec3d newLockedPosOffset = newOrientation.rotate(new Vec3d((lockedLocalPos.nx() - 0.5) * newWidth, (lockedLocalPos.ny() - 0.5) * newHeight, 0.0));
         Vec3d newOrigin = lockedPos.subtract(newLockedPosOffset);
         UnilateralPortalState newPortalState = new UnilateralPortalState(originalState.dimension(), newOrigin, newOrientation, newWidth, newHeight);
         return new PortalWandInteraction.OneLockDraggingResult(newPortalState, rotation.getRotatingAxis());
      } else {
         return null;
      }
   }

   private static DQuaternion getOrientationByNormalDiagonalWidthHeight(Vec3d normal, Vec3d diagonal, double width, double height, double sigX, double sigY) {
      Vec3d newOffsetN = diagonal.normalize();
      double newOffsetLen = diagonal.length();
      Vec3d sideVecN = normal.crossProduct(newOffsetN).normalize();
      double sideVecLen = width * height / newOffsetLen;
      double wFront = sideVecLen * width / height;
      double hFront = sideVecLen * height / width;
      Vec3d sideVecW = sideVecN.multiply(-sideVecLen * sigX * sigY);
      Vec3d sideVecH = sideVecW.multiply(-1.0);
      Vec3d newAxisW = newOffsetN.multiply(wFront).add(sideVecW).normalize().multiply(sigX);
      Vec3d newAxisH = newOffsetN.multiply(hFront).add(sideVecH).normalize().multiply(sigY);
      return DQuaternion.fromFacingVecs(newAxisW, newAxisH).fixFloatingPointErrorAccumulation();
   }

   public static void handleCopyCutPortal(ServerPlayerEntity player, UUID portalId, boolean isCut) {
      Portal portal = WandUtil.getPortalByUUID(player.getEntityWorld(), portalId);
      if (portal == null) {
         player.sendMessage(Text.literal("Cannot find portal " + portalId));
      } else {
         PortalState portalState = portal.getPortalState();
         NbtCompound portalData = portal.writePortalDataToNbt();
         Portal flipped = null;
         Portal reverse = null;
         Portal parallel = null;
         PortalExtension ext = PortalExtension.get(portal);
         if (ext.flippedPortal != null) {
            flipped = ext.flippedPortal;
         }

         if (ext.reversePortal != null) {
            reverse = ext.reversePortal;
         }

         if (ext.parallelPortal != null) {
            parallel = ext.parallelPortal;
         }

         PortalWandInteraction.CopyingSession copyingSession = new PortalWandInteraction.CopyingSession(
            portalState, portalData, isCut, flipped != null, reverse != null, parallel != null
         );
         copyingSessionMap.put(player, copyingSession);
         if (isCut) {
            portal.remove(RemovalReason.KILLED);
            if (flipped != null) {
               flipped.remove(RemovalReason.KILLED);
            }

            if (reverse != null) {
               reverse.remove(RemovalReason.KILLED);
            }

            if (parallel != null) {
               parallel.remove(RemovalReason.KILLED);
            }
         }
      }
   }

   private static void handleConfirmCopyCut(ServerPlayerEntity player, Vec3d origin, DQuaternion rawOrientation) {
      PortalWandInteraction.CopyingSession copyingSession = copyingSessionMap.remove(player);
      if (copyingSession == null) {
         player.sendMessage(Text.literal("Missing copying session"));
      } else {
         DQuaternion orientation = rawOrientation.fixFloatingPointErrorAccumulation();
         if (player.getEntityPos().squaredDistanceTo(origin) > 4096.0) {
            player.sendMessage(Text.literal("Too far away from the portal"));
         } else {
            Portal portal = (Portal)Portal.ENTITY_TYPE.create(player.getEntityWorld(), SpawnReason.TRIGGERED);

            assert portal != null;

            portal.readPortalDataFromNbt(copyingSession.portalData);
            PortalState originalPortalState = copyingSession.portalState;
            UnilateralPortalState originalThisSide = originalPortalState.getThisSideState();
            UnilateralPortalState originalOtherSide = originalPortalState.getOtherSideState();
            UnilateralPortalState newThisSide = new UnilateralPortalState(
               player.getEntityWorld().getRegistryKey(), origin, orientation, originalThisSide.width(), originalThisSide.height()
            );
            portal.setPortalState(UnilateralPortalState.combine(newThisSide, originalOtherSide));
            portal.resetAnimationReferenceState(true, false);
            if (copyingSession.isCut()) {
               McHelper.spawnServerEntity(portal);
               if (copyingSession.hasFlipped) {
                  Portal flippedPortal = PortalManipulation.createFlippedPortal(portal, Portal.ENTITY_TYPE);
                  flippedPortal.resetAnimationReferenceState(true, false);
                  McHelper.spawnServerEntity(flippedPortal);
               }

               if (copyingSession.hasReverse) {
                  Portal reversePortal = PortalManipulation.createReversePortal(portal, Portal.ENTITY_TYPE);
                  reversePortal.resetAnimationReferenceState(false, true);
                  McHelper.spawnServerEntity(reversePortal);
                  if (copyingSession.hasParallel) {
                     Portal parallelPortal = PortalManipulation.createFlippedPortal(reversePortal, Portal.ENTITY_TYPE);
                     parallelPortal.resetAnimationReferenceState(false, true);
                     McHelper.spawnServerEntity(parallelPortal);
                  }
               }
            } else {
               PortalExtension.get(portal).bindCluster = false;
               McHelper.spawnServerEntity(portal);
               if (copyingSession.hasFlipped || copyingSession.hasReverse || copyingSession.hasParallel) {
                  player.sendMessage(Text.translatable("imm_ptl.wand.copy.not_copying_cluster"));
                  giveCommandStick(player, "/portal complete_bi_way_bi_faced_portal");
               }
            }
         }
      }
   }

   private static void handleClearPortalClipboard(ServerPlayerEntity player) {
      copyingSessionMap.remove(player);
   }

   private record CopyingSession(PortalState portalState, NbtCompound portalData, boolean isCut, boolean hasFlipped, boolean hasReverse, boolean hasParallel) {
   }

   public static final class DraggingInfo {
      @Nullable
      public final PortalLocalXYNormalized lockedAnchor;
      public final PortalLocalXYNormalized draggingAnchor;
      @Nullable
      public Vec3d previousRotationAxis;
      public final boolean lockWidth;
      public final boolean lockHeight;

      public DraggingInfo(
         @Nullable PortalLocalXYNormalized lockedAnchor,
         PortalLocalXYNormalized draggingAnchor,
         @Nullable Vec3d previousRotationAxis,
         boolean lockWidth,
         boolean lockHeight
      ) {
         this.lockedAnchor = lockedAnchor;
         this.draggingAnchor = draggingAnchor;
         this.previousRotationAxis = previousRotationAxis;
         this.lockWidth = lockWidth;
         this.lockHeight = lockHeight;
      }

      public boolean shouldLockScale() {
         return this.lockWidth || this.lockHeight;
      }

      public boolean isValid() {
         return this.lockedAnchor != null && !this.lockedAnchor.isValid() ? false : this.draggingAnchor.isValid();
      }
   }

   private static class DraggingSession {
      private final RegistryKey<World> dimension;
      private final UUID portalId;
      private final PortalState originalState;
      private final PortalWandInteraction.DraggingInfo lastDraggingInfo;

      public DraggingSession(RegistryKey<World> dimension, UUID portalId, PortalState originalState, PortalWandInteraction.DraggingInfo lastDraggingInfo) {
         this.dimension = dimension;
         this.portalId = portalId;
         this.originalState = originalState;
         this.lastDraggingInfo = lastDraggingInfo;
      }

      @Nullable
      public Portal getPortal() {
         Entity entity = McHelper.getServerWorld(this.dimension).getEntity(this.portalId);
         return entity instanceof Portal ? (Portal)entity : null;
      }
   }

   public record OneLockDraggingResult(UnilateralPortalState newState, Vec3d rotationAxis) {
   }

   public static class RemoteCallables {
      public static void finishPortalCreation(ServerPlayerEntity player, ProtoPortal protoPortal) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleFinishPortalCreation(player, protoPortal);
         }
      }

      public static void requestApplyDrag(ServerPlayerEntity player, UUID portalId, Vec3d cursorPos, PortalWandInteraction.DraggingInfo draggingInfo) {
         if (PortalWandInteraction.checkPermission(player)) {
            if (player.getEntityWorld().getEntity(portalId) instanceof Portal portal) {
               if (!draggingInfo.isValid()) {
                  player.sendMessage(Text.literal("Invalid dragging info"));
                  PortalWandInteraction.LOGGER.error("Invalid dragging info {}", draggingInfo);
               } else {
                  PortalWandInteraction.handleDraggingRequest(player, portalId, cursorPos, draggingInfo, portal);
               }
            } else {
               PortalWandInteraction.LOGGER.error("Cannot find portal {}", portalId);
            }
         }
      }

      public static void undoDrag(ServerPlayerEntity player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleUndoDrag(player);
         }
      }

      public static void finishDragging(ServerPlayerEntity player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleFinishDrag(player);
         }
      }

      public static void copyCutPortal(ServerPlayerEntity player, UUID portalId, boolean isCut) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleCopyCutPortal(player, portalId, isCut);
         }
      }

      public static void confirmCopyCut(ServerPlayerEntity player, Vec3d origin, DQuaternion orientation) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleConfirmCopyCut(player, origin, orientation);
         }
      }

      public static void clearPortalClipboard(ServerPlayerEntity player) {
         if (PortalWandInteraction.checkPermission(player)) {
            PortalWandInteraction.handleClearPortalClipboard(player);
         }
      }
   }
}
