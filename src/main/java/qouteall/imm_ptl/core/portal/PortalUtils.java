package qouteall.imm_ptl.core.portal;

import com.google.common.collect.Streams;
import com.mojang.datafixers.util.Pair;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult.Type;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.my_util.RayTraceResult;

public class PortalUtils {
   @NotNull
   public static Optional<Pair<Portal, RayTraceResult>> raytracePortals(
      World world, Vec3d from, Vec3d to, boolean includeGlobalPortal, Predicate<Portal> predicate
   ) {
      return lenientRayTracePortals(world, from, to, includeGlobalPortal, predicate, 0.001);
   }

   @NotNull
   public static Optional<Pair<Portal, RayTraceResult>> lenientRayTracePortals(
      World world, Vec3d from, Vec3d to, boolean includeGlobalPortal, Predicate<Portal> predicate, double leniency
   ) {
      Stream<Portal> portalStream = McHelper.getEntitiesNearby(world, from, Portal.class, from.distanceTo(to)).stream();
      if (includeGlobalPortal) {
         List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
         portalStream = Streams.concat(new Stream[]{portalStream, globalPortals.stream()});
      }

      return portalStream.<Pair<Portal, RayTraceResult>>map(portal -> new Pair<>(portal, portal.generalRayTrace(from, to, leniency)))
         .filter(portalAndHitPos -> portalAndHitPos.getSecond() != null && predicate.test((Portal)portalAndHitPos.getFirst()))
         .min(Comparator.comparingDouble(portalAndHitPos -> ((RayTraceResult)portalAndHitPos.getSecond()).hitPos().squaredDistanceTo(from)));
   }

   public static Optional<Pair<Portal, RayTraceResult>> raytracePortalFromEntityView(
      Entity player, float partialTick, double maxDistance, boolean includeGlobalPortal, Predicate<Portal> predicate
   ) {
      Vec3d from = player.getCameraPosVec(partialTick);
      Vec3d to = from.add(player.getRotationVec(partialTick).multiply(maxDistance));
      World world = player.getEntityWorld();
      return raytracePortals(world, from, to, includeGlobalPortal, predicate);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(Entity entity, double maxDistance) {
      return portalAwareRayTrace(entity.getEntityWorld(), entity.getEyePos(), entity.getRotationVec(1.0F), maxDistance, entity);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(World world, Vec3d startingPoint, Vec3d direction, double maxDistance, Entity entity) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, List.of());
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      World world, Vec3d startingPoint, Vec3d direction, double maxDistance, Entity entity, ShapeType clipContextBlock
   ) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, clipContextBlock, List.of());
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      World world, Vec3d startingPoint, Vec3d direction, double maxDistance, Entity entity, @NotNull List<Portal> portalsPassingThrough
   ) {
      return portalAwareRayTrace(world, startingPoint, direction, maxDistance, entity, ShapeType.OUTLINE, portalsPassingThrough);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTrace(
      World world,
      Vec3d startingPoint,
      Vec3d direction,
      double maxDistance,
      Entity entity,
      ShapeType clipContextBlock,
      @NotNull List<Portal> portalsPassingThrough
   ) {
      return portalAwareRayTraceFull(world, startingPoint, direction, maxDistance, entity, clipContextBlock, FluidHandling.NONE, portalsPassingThrough, 5);
   }

   @Nullable
   public static PortalUtils.PortalAwareRaytraceResult portalAwareRayTraceFull(
      World world,
      Vec3d startingPoint,
      Vec3d direction,
      double maxDistance,
      Entity entity,
      ShapeType clipContextBlock,
      FluidHandling clipContextFluid,
      @NotNull List<Portal> portalsPassingThrough,
      int maxPortalLayer
   ) {
      if (portalsPassingThrough.size() > maxPortalLayer) {
         return null;
      } else {
         Vec3d endingPoint = startingPoint.add(direction.multiply(maxDistance));
         Optional<Pair<Portal, RayTraceResult>> portalHit = raytracePortals(
            world, startingPoint, endingPoint, true, p -> entity instanceof PlayerEntity player ? p.isInteractableBy(player) : p.isVisible()
         );
         RaycastContext context = new RaycastContext(startingPoint, endingPoint, clipContextBlock, clipContextFluid, entity);
         BlockHitResult blockHitResult = world.raycast(context);
         boolean portalHitFound = portalHit.isPresent();
         boolean blockHitFound = blockHitResult.getType() == Type.BLOCK;
         boolean shouldContinueRaytraceInsidePortal = false;
         if (portalHitFound && blockHitFound) {
            RayTraceResult rtResult = (RayTraceResult)portalHit.get().getSecond();
            double portalDistance = rtResult.hitPos().distanceTo(startingPoint);
            double blockDistance = blockHitResult.getPos().distanceTo(startingPoint);
            if (!(portalDistance < blockDistance + 1.0E-4)) {
               return new PortalUtils.PortalAwareRaytraceResult(world, blockHitResult, portalsPassingThrough);
            }

            shouldContinueRaytraceInsidePortal = true;
         } else {
            if (!portalHitFound && blockHitFound) {
               return new PortalUtils.PortalAwareRaytraceResult(world, blockHitResult, portalsPassingThrough);
            }

            if (portalHitFound && !blockHitFound) {
               shouldContinueRaytraceInsidePortal = true;
            }
         }

         if (shouldContinueRaytraceInsidePortal) {
            RayTraceResult rtResult = (RayTraceResult)portalHit.get().getSecond();
            double portalDistance = rtResult.hitPos().distanceTo(startingPoint);
            Portal portal = (Portal)portalHit.get().getFirst();
            Vec3d newStartingPoint = portal.transformPoint(rtResult.hitPos()).add(rtResult.surfaceNormal().multiply(-0.001));
            Vec3d newDirection = portal.transformLocalVecNonScale(direction);
            double restDistance = maxDistance - portalDistance;
            return restDistance < 0.0
               ? null
               : portalAwareRayTraceFull(
                  portal.getDestinationWorld(),
                  newStartingPoint,
                  newDirection,
                  restDistance,
                  entity,
                  clipContextBlock,
                  clipContextFluid,
                  Stream.concat(portalsPassingThrough.stream(), Stream.of(portal)).collect(Collectors.toList()),
                  maxPortalLayer
               );
         } else {
            return null;
         }
      }
   }

   public record PortalAwareRaytraceResult(World world, @NotNull BlockHitResult hitResult, List<Portal> portalsPassingThrough) {
   }
}
