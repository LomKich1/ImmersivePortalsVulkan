package qouteall.imm_ptl.core.portal;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.api.PortalAPI;
import qouteall.imm_ptl.core.commands.PortalCommand;
import qouteall.imm_ptl.core.portal.shape.SpecialFlatPortalShape;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.MiscHelper;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.my_util.Mesh2D;

public class PortalManipulation {
   public static final DQuaternion flipAxisW = DQuaternion.rotationByDegrees(new Vec3d(0.0, 1.0, 0.0), 180.0).fixFloatingPointErrorAccumulation();

   public static void setPortalTransformation(Portal portal, RegistryKey<World> destDim, Vec3d destPos, @Nullable DQuaternion rotation, double scale) {
      portal.setDestDim(destDim);
      portal.setDestination(destPos);
      portal.setRotation(rotation);
      portal.setScaling(scale);
      portal.updateCache();
   }

   public static void removeConnectedPortals(Portal portal, Consumer<Portal> removalInformer) {
      removeOverlappedPortals(
         portal.getEntityWorld(),
         portal.getOriginPos(),
         portal.getNormal().multiply(-1.0),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
      ServerWorld toWorld = MiscHelper.getServer().getWorld(portal.getDestDim());
      removeOverlappedPortals(
         toWorld,
         portal.getDestPos(),
         portal.transformLocalVecNonScale(portal.getNormal().multiply(-1.0)),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
      removeOverlappedPortals(
         toWorld,
         portal.getDestPos(),
         portal.transformLocalVecNonScale(portal.getNormal()),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
   }

   public static Portal completeBiWayPortal(Portal portal, EntityType<? extends Portal> entityType) {
      Portal newPortal = createReversePortal(portal, entityType);
      McHelper.spawnServerEntity(newPortal);
      return newPortal;
   }

   public static <T extends Portal> T createReversePortal(Portal portal, EntityType<T> entityType) {
      World world = portal.getDestinationWorld();
      T newPortal = (T)entityType.create(world, SpawnReason.TRIGGERED);

      assert newPortal != null;

      newPortal.setDestDim(portal.getEntityWorld().getRegistryKey());
      newPortal.setPosition(portal.getDestPos().x, portal.getDestPos().y, portal.getDestPos().z);
      newPortal.setDestination(portal.getOriginPos());
      newPortal.specificPlayerId = portal.specificPlayerId;
      newPortal.setWidth(portal.getPortalWidth() * portal.getScaling());
      newPortal.setHeight(portal.getPortalHeight() * portal.getScaling());
      newPortal.setThickness(portal.getThickness() * portal.getScaling());
      newPortal.setAxisW(portal.getAxisW().multiply(-1.0));
      newPortal.setAxisH(portal.getAxisH());
      newPortal.setPortalShape(portal.getPortalShape().getReverse());
      if (portal.getRotation() != null) {
         rotatePortalBody(newPortal, portal.getRotation());
         newPortal.setRotation(portal.getRotation().getConjugated());
      }

      newPortal.setScaling(1.0 / portal.getScaling());
      copyAdditionalProperties(newPortal, portal);
      return newPortal;
   }

   public static void rotatePortalBody(Portal portal, DQuaternion rotation) {
      portal.setAxisW(rotation.rotate(portal.getAxisW()));
      portal.setAxisH(rotation.rotate(portal.getAxisH()));
   }

   public static Portal completeBiFacedPortal(Portal portal, EntityType<Portal> entityType) {
      Portal newPortal = createFlippedPortal(portal, entityType);
      McHelper.spawnServerEntity(newPortal);
      return newPortal;
   }

   public static <T extends Portal> T createFlippedPortal(Portal portal, EntityType<T> entityType) {
      World world = portal.getEntityWorld();
      T newPortal = (T)entityType.create(world, SpawnReason.TRIGGERED);

      assert newPortal != null;

      newPortal.setDestDim(portal.getDestDim());
      newPortal.setPosition(portal.getX(), portal.getY(), portal.getZ());
      newPortal.setDestination(portal.getDestPos());
      newPortal.specificPlayerId = portal.specificPlayerId;
      newPortal.setWidth(portal.getPortalWidth());
      newPortal.setHeight(portal.getPortalHeight());
      newPortal.setThickness(portal.getThickness());
      newPortal.setAxisW(portal.getAxisW().multiply(-1.0));
      newPortal.setAxisH(portal.getAxisH());
      newPortal.setPortalShape(portal.getPortalShape().getFlipped());
      newPortal.setRotation(portal.getRotation());
      newPortal.setScaling(portal.getScaling());
      copyAdditionalProperties(newPortal, portal);
      return newPortal;
   }

   public static Portal copyPortal(Portal portal, EntityType<Portal> entityType) {
      World world = portal.getEntityWorld();
      Portal newPortal = (Portal)entityType.create(world, SpawnReason.TRIGGERED);
      newPortal.setDestDim(portal.getDestDim());
      newPortal.setPosition(portal.getX(), portal.getY(), portal.getZ());
      newPortal.setDestination(portal.getDestPos());
      newPortal.specificPlayerId = portal.specificPlayerId;
      newPortal.setWidth(portal.getPortalWidth());
      newPortal.setHeight(portal.getPortalHeight());
      newPortal.setAxisW(portal.getAxisW());
      newPortal.setAxisH(portal.getAxisH());
      newPortal.setPortalShape(portal.getPortalShape().cloneIfNecessary());
      newPortal.setRotation(portal.getRotation());
      newPortal.setScaling(portal.getScaling());
      copyAdditionalProperties(newPortal, portal);
      return newPortal;
   }

   public static void completeBiWayBiFacedPortal(
      Portal portal, Consumer<Portal> removalInformer, Consumer<Portal> addingInformer, EntityType<Portal> entityType
   ) {
      removeOverlappedPortals(
         (ServerWorld)portal.getEntityWorld(),
         portal.getOriginPos(),
         portal.getNormal().multiply(-1.0),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
      Portal oppositeFacedPortal = completeBiFacedPortal(portal, entityType);
      removeOverlappedPortals(
         MiscHelper.getServer().getWorld(portal.getDestDim()),
         portal.getDestPos(),
         portal.transformLocalVecNonScale(portal.getNormal().multiply(-1.0)),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
      Portal r1 = completeBiWayPortal(portal, entityType);
      removeOverlappedPortals(
         MiscHelper.getServer().getWorld(oppositeFacedPortal.getDestDim()),
         oppositeFacedPortal.getDestPos(),
         oppositeFacedPortal.transformLocalVecNonScale(oppositeFacedPortal.getNormal().multiply(-1.0)),
         p -> Objects.equals(p.specificPlayerId, portal.specificPlayerId),
         removalInformer
      );
      Portal r2 = completeBiWayPortal(oppositeFacedPortal, entityType);
      addingInformer.accept(oppositeFacedPortal);
      addingInformer.accept(r1);
      addingInformer.accept(r2);
   }

   public static void removeOverlappedPortals(World world, Vec3d pos, Vec3d normal, Predicate<Portal> predicate, Consumer<Portal> informer) {
      getPortalCluster(world, pos, normal, predicate).forEach(e -> {
         e.remove(RemovalReason.KILLED);
         informer.accept(e);
      });
   }

   public static List<Portal> getPortalCluster(World world, Vec3d pos, Vec3d normal, Predicate<Portal> predicate) {
      return McHelper.findEntitiesByBox(
         Portal.class,
         world,
         new Box(pos.add(0.1, 0.1, 0.1), pos.subtract(0.1, 0.1, 0.1)),
         IPGlobal.maxNormalPortalRadius,
         p -> p.getNormal().dotProduct(normal) > 0.5 && predicate.test(p)
      );
   }

   public static <T extends Portal> T createOrthodoxPortal(
      EntityType<T> entityType, ServerWorld fromWorld, ServerWorld toWorld, Direction facing, Box portalArea, Vec3d destination
   ) {
      T portal = (T)entityType.create(fromWorld, SpawnReason.TRIGGERED);
      PortalAPI.setPortalOrthodoxShape(portal, facing, portalArea);
      portal.setDestination(destination);
      portal.setDestDim(toWorld.getRegistryKey());
      return portal;
   }

   public static void copyAdditionalProperties(Portal to, Portal from) {
      copyAdditionalProperties(to, from, true);
   }

   public static void copyAdditionalProperties(Portal to, Portal from, boolean includeSpecialProperties) {
      to.setTeleportable(from.isTeleportable());
      to.setTeleportChangesScale(from.isTeleportChangesScale());
      to.teleportChangesGravity = from.teleportChangesGravity;
      to.specificPlayerId = from.specificPlayerId;
      PortalExtension.get(to).motionAffinity = PortalExtension.get(from).motionAffinity;
      PortalExtension.get(to).adjustPositionAfterTeleport = PortalExtension.get(from).adjustPositionAfterTeleport;
      to.setCrossPortalCollisionEnabled(from.isCrossPortalCollisionEnabled());
      PortalExtension.get(to).bindCluster = PortalExtension.get(from).bindCluster;
      to.animation.defaultAnimation = from.animation.defaultAnimation.copy();
      to.setIsVisible(from.isVisible());
      if (includeSpecialProperties) {
         to.portalTag = from.portalTag;
         to.setCommandsOnTeleported(from.getCommandsOnTeleported());
      }
   }

   @Deprecated
   public static void createScaledBoxView(
      ServerWorld areaWorld,
      Box area,
      ServerWorld boxWorld,
      Vec3d boxBottomCenter,
      double scale,
      boolean biWay,
      boolean teleportChangesScale,
      boolean outerFuseView,
      boolean outerRenderingMergable,
      boolean innerRenderingMergable,
      boolean hasCrossPortalCollision
   ) {
      Vec3d viewBoxSize = Helper.getBoxSize(area).multiply(1.0 / scale);
      Box viewBox = Helper.getBoxByBottomPosAndSize(boxBottomCenter, viewBoxSize);

      for (Direction direction : Direction.values()) {
         Portal portal = createOrthodoxPortal(
            Portal.ENTITY_TYPE, boxWorld, areaWorld, direction, Helper.getBoxSurface(viewBox, direction), Helper.getBoxSurface(area, direction).getCenter()
         );
         portal.setScaling(scale);
         portal.setTeleportChangesScale(teleportChangesScale);
         portal.setFuseView(outerFuseView);
         portal.renderingMergable = outerRenderingMergable;
         portal.setCrossPortalCollisionEnabled(hasCrossPortalCollision);
         portal.portalTag = "imm_ptl:scale_box";
         McHelper.spawnServerEntity(portal);
         if (biWay) {
            Portal reversePortal = createReversePortal(portal, Portal.ENTITY_TYPE);
            reversePortal.renderingMergable = innerRenderingMergable;
            McHelper.spawnServerEntity(reversePortal);
         }
      }
   }

   public static Portal placePortal(double width, double height, Entity entity) {
      Vec3d playerLook = entity.getRotationVector();
      Pair<BlockHitResult, List<Portal>> rayTrace = IPMcHelper.rayTrace(
         entity.getEntityWorld(),
         new RaycastContext(
            entity.getCameraPosVec(1.0F), entity.getCameraPosVec(1.0F).add(playerLook.multiply(100.0)), ShapeType.OUTLINE, FluidHandling.NONE, entity
         ),
         true
      );
      BlockHitResult hitResult = (BlockHitResult)rayTrace.getLeft();
      List<Portal> hitPortals = (List<Portal>)rayTrace.getRight();
      if (IPMcHelper.hitResultIsMissedOrNull(hitResult)) {
         return null;
      } else {
         for (Portal hitPortal : hitPortals) {
            playerLook = hitPortal.transformLocalVecNonScale(playerLook);
         }

         Direction lookingDirection = Helper.getFacingExcludingAxis(playerLook, hitResult.getSide().getAxis());
         if (lookingDirection == null) {
            return null;
         } else {
            Vec3d axisH = Vec3d.of(hitResult.getSide().getVector());
            Vec3d axisW = axisH.crossProduct(Vec3d.of(lookingDirection.getOpposite().getVector()));
            Vec3d pos = Vec3d.ofCenter(hitResult.getBlockPos()).add(axisH.multiply(0.5 + height / 2.0));
            World world = hitPortals.isEmpty() ? entity.getEntityWorld() : hitPortals.get(hitPortals.size() - 1).getDestinationWorld();
            Portal portal = new Portal(Portal.ENTITY_TYPE, world);
            portal.setPos(pos.x, pos.y, pos.z);
            portal.setAxisW(axisW);
            portal.setAxisH(axisH);
            portal.setWidth(width);
            portal.setHeight(height);
            return portal;
         }
      }
   }

   public static DQuaternion getPortalOrientationQuaternion(Vec3d axisW, Vec3d axisH) {
      Vec3d normal = axisW.crossProduct(axisH);
      return DQuaternion.matrixToQuaternion(axisW, axisH, normal);
   }

   public static void setPortalOrientationQuaternion(Portal portal, DQuaternion quaternion) {
      portal.setOrientationRotation(quaternion);
   }

   public static void adjustRotationToConnect(Portal portalA, Portal portalB) {
      DQuaternion a = PortalAPI.getPortalOrientationQuaternion(portalA);
      DQuaternion b = PortalAPI.getPortalOrientationQuaternion(portalB);
      DQuaternion delta = b.hamiltonProduct(a.getConjugated());
      DQuaternion flip = DQuaternion.rotationByDegrees(portalB.getAxisH(), 180.0);
      DQuaternion aRot = flip.hamiltonProduct(delta);
      portalA.setRotation(aRot);
      portalB.setRotation(aRot.getConjugated());
   }

   public static boolean isOtherSideBoxInside(Box transformedBoundingBox, Portal renderingPortal) {
      return Arrays.stream(Helper.eightVerticesOf(transformedBoundingBox)).anyMatch(p -> renderingPortal.isOnDestinationSide(p, 0.0));
   }

   @Nullable
   public static Portal findParallelPortal(Portal portal) {
      return Helper.getFirstNullable(
         McHelper.findEntitiesRough(
            Portal.class,
            portal.getDestinationWorld(),
            portal.getDestPos(),
            0,
            p1 -> p1.getOriginPos().subtract(portal.getDestPos()).lengthSquared() < 0.01
               && p1.getDestPos().subtract(portal.getOriginPos()).lengthSquared() < 0.01
               && p1.getNormal().dotProduct(portal.getContentDirection()) < -0.9
               && p1.getContentDirection().dotProduct(portal.getNormal()) < -0.9
               && !(p1 instanceof Mirror)
               && p1 != portal
         )
      );
   }

   @Nullable
   public static Portal findReversePortal(Portal portal) {
      return Helper.getFirstNullable(
         McHelper.findEntitiesRough(Portal.class, portal.getDestinationWorld(), portal.getDestPos(), 0, p1 -> Portal.isReversePortal(portal, p1))
      );
   }

   @Nullable
   public static Portal findFlippedPortal(Portal portal) {
      return Helper.getFirstNullable(
         McHelper.findEntitiesRough(
            Portal.class,
            portal.getOriginWorld(),
            portal.getOriginPos(),
            0,
            p1 -> p1.getOriginPos().subtract(portal.getOriginPos()).lengthSquared() < 0.01
               && p1.getNormal().dotProduct(portal.getNormal()) < -0.9
               && p1.getDestPos().squaredDistanceTo(portal.getDestPos()) < 0.01
               && !(p1 instanceof Mirror)
               && p1 != portal
         )
      );
   }

   @Deprecated
   public static Optional<com.mojang.datafixers.util.Pair<Portal, Vec3d>> raytracePortals(World world, Vec3d from, Vec3d to, boolean includeGlobalPortal) {
      return PortalCommand.raytracePortals(world, from, to, includeGlobalPortal);
   }

   public static DQuaternion computeDeltaTransformation(DQuaternion thisSideOrientation, DQuaternion otherSideOrientation) {
      return otherSideOrientation.hamiltonProduct(flipAxisW).hamiltonProduct(thisSideOrientation.getConjugated());
   }

   public static void makePortalRound(Portal portal, int triangleNum) {
      Mesh2D mesh2D = new Mesh2D();

      for (int i = 0; i < triangleNum; i++) {
         mesh2D.addTriangle(
            0.0,
            0.0,
            Math.cos((Math.PI * 2) * i / triangleNum),
            Math.sin((Math.PI * 2) * i / triangleNum),
            Math.cos((Math.PI * 2) * (i + 1.0) / triangleNum),
            Math.sin((Math.PI * 2) * (i + 1.0) / triangleNum)
         );
      }

      portal.setPortalShape(new SpecialFlatPortalShape(mesh2D));
   }
}
