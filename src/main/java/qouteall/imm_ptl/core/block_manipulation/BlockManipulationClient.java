package qouteall.imm_ptl.core.block_manipulation;

import java.util.function.Supplier;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Pair;
import net.minecraft.fluid.FluidState;
import net.minecraft.world.RaycastContext;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.registry.RegistryKey;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.hit.HitResult.class_240;
import net.minecraft.world.RaycastContext.class_242;
import net.minecraft.world.RaycastContext.class_3960;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalPlaceholderBlock;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.q_misc_util.my_util.RayTraceResult;

public class BlockManipulationClient {
   private static final MinecraftClient client = MinecraftClient.getInstance();
   public static RegistryKey<World> remotePointedDim;
   public static HitResult remoteHitResult;

   public static boolean isPointingToPortal() {
      return remotePointedDim != null;
   }

   @Nullable
   public static ClientWorld getRemotePointedWorld() {
      return remotePointedDim == null ? null : ClientWorldLoader.getWorld(remotePointedDim);
   }

   private static BlockHitResult createMissedHitResult(Vec3d from, Vec3d to) {
      Vec3d dir = to.subtract(from).normalize();
      return BlockHitResult.createMissed(to, Direction.getFacing(dir.x, dir.y, dir.z), BlockPos.ofFloored(to));
   }

   private static boolean hitResultIsMissedOrNull(HitResult bhr) {
      return bhr == null || bhr.getType() == class_240.field_1333;
   }

   public static void updatePointedBlock(float partialTick) {
      if (client.interactionManager != null && client.world != null && client.player != null) {
         remotePointedDim = null;
         remoteHitResult = null;
         if (BlockManipulationServer.canDoCrossPortalInteractionEvent.invoker().test(client.player)) {
            Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
            double reachDistance = client.player.getBlockInteractionRange();
            PortalUtils.raytracePortalFromEntityView(
                  client.player, partialTick, reachDistance, true, portal1 -> portal1.isInteractableBy(client.player)
               )
               .ifPresent(
                  pair -> {
                     Portal portal = (Portal)pair.getFirst();
                     Vec3d hitPos = ((RayTraceResult)pair.getSecond()).hitPos();
                     double distanceToPortalPointing = hitPos.distanceTo(cameraPos);
                     if (distanceToPortalPointing < getCurrentTargetDistance() + 0.2) {
                        client.crosshairTarget = createMissedHitResult(cameraPos, hitPos);
                        updateTargetedBlockThroughPortal(
                           cameraPos,
                           client.player.getRotationVec(partialTick),
                           client.player.getEntityWorld().getRegistryKey(),
                           distanceToPortalPointing,
                           reachDistance,
                           portal
                        );
                     }
                  }
               );
         }
      }
   }

   private static double getCurrentTargetDistance() {
      Vec3d cameraPos = client.gameRenderer.getCamera().getCameraPos();
      if (hitResultIsMissedOrNull(client.crosshairTarget)) {
         return 23333.0;
      } else {
         if (client.crosshairTarget instanceof BlockHitResult) {
            BlockPos hitPos = ((BlockHitResult)client.crosshairTarget).getBlockPos();
            if (client.world.getBlockState(hitPos).getBlock() == PortalPlaceholderBlock.instance) {
               return 23333.0;
            }
         }

         return cameraPos.distanceTo(client.crosshairTarget.getPos());
      }
   }

   private static void updateTargetedBlockThroughPortal(
      Vec3d cameraPos, Vec3d viewVector, RegistryKey<World> playerDimension, double beginDistance, double endDistance, Portal portal
   ) {
      Vec3d from = portal.transformPoint(cameraPos.add(viewVector.multiply(beginDistance)));
      Vec3d to = portal.transformPoint(cameraPos.add(viewVector.multiply(endDistance)));
      RaycastContext context = new RaycastContext(from, to, class_3960.field_17559, class_242.field_1348, client.player);
      ClientWorld world = ClientWorldLoader.getWorld(portal.getDestDim());
      remoteHitResult = (HitResult)BlockView.raycast(
         from,
         to,
         context,
         (rayTraceContext, blockPos) -> {
            BlockState blockState = world.getBlockState(blockPos);
            if (blockState.getBlock() == PortalPlaceholderBlock.instance) {
               return null;
            } else if (blockState.getBlock() == Blocks.field_10499) {
               return null;
            } else {
               FluidState fluidState = world.getFluidState(blockPos);
               Vec3d start = rayTraceContext.getStart();
               Vec3d end = rayTraceContext.getEnd();
               Vec3d correctedStart = start.subtract(end.subtract(start).multiply(0.0015));
               VoxelShape solidShape = rayTraceContext.getBlockShape(blockState, world, blockPos);
               BlockHitResult blockHitResult = world.raycastBlock(correctedStart, end, blockPos, solidShape, blockState);
               VoxelShape fluidShape = rayTraceContext.getFluidShape(fluidState, world, blockPos);
               BlockHitResult fluidHitResult = fluidShape.raycast(start, end, blockPos);
               double d = blockHitResult == null ? Double.MAX_VALUE : rayTraceContext.getStart().squaredDistanceTo(blockHitResult.getPos());
               double e = fluidHitResult == null ? Double.MAX_VALUE : rayTraceContext.getStart().squaredDistanceTo(fluidHitResult.getPos());
               return d <= e ? blockHitResult : fluidHitResult;
            }
         },
         rayTraceContext -> {
            Vec3d vec3d = rayTraceContext.getStart().subtract(rayTraceContext.getEnd());
            return BlockHitResult.createMissed(
               rayTraceContext.getEnd(),
               Direction.getFacing(vec3d.x, vec3d.y, vec3d.z),
               BlockPos.ofFloored(rayTraceContext.getEnd())
            );
         }
      );
      if (remoteHitResult.getPos().y < world.getBottomY() + 0.1) {
         remoteHitResult = new BlockHitResult(
            remoteHitResult.getPos(), Direction.field_11033, ((BlockHitResult)remoteHitResult).getBlockPos(), ((BlockHitResult)remoteHitResult).isInsideBlock()
         );
      }

      if (remoteHitResult != null && !world.getBlockState(((BlockHitResult)remoteHitResult).getBlockPos()).isAir()) {
         client.crosshairTarget = createMissedHitResult(from, to);
         remotePointedDim = portal.getDestDim();
      }
   }

   public static <T> T withSwitchedContext(Supplier<T> func, boolean transformHitResult) {
      Validate.notNull(remoteHitResult);
      ClientWorld remoteWorld = getRemotePointedWorld();
      Validate.notNull(remoteWorld);
      HitResult effectiveHitResult;
      if (transformHitResult && remoteHitResult instanceof BlockHitResult blockHitResult) {
         Pair<BlockHitResult, RegistryKey<World>> r = BlockManipulationServer.getHitResultForPlacing(remoteWorld, blockHitResult);
         effectiveHitResult = (HitResult)r.getLeft();
         remoteWorld = ClientWorldLoader.getWorld((RegistryKey<World>)r.getRight());
         Validate.notNull(remoteWorld);
         Validate.notNull(effectiveHitResult);
      } else {
         effectiveHitResult = remoteHitResult;
      }

      return ClientWorldLoader.withSwitchedWorld(remoteWorld, () -> {
         HitResult originalHitResult = client.crosshairTarget;
         client.crosshairTarget = effectiveHitResult;

         Object var3x;
         try {
            var3x = func.get();
         } finally {
            client.crosshairTarget = originalHitResult;
         }

         return (T)var3x;
      });
   }

   @Nullable
   public static String getDebugString() {
      if (remotePointedDim == null) {
         return null;
      } else {
         return remoteHitResult instanceof BlockHitResult blockHitResult
            ? "Point:%s %d %d %d"
               .formatted(
                  remotePointedDim.getValue(),
                  blockHitResult.getBlockPos().getX(),
                  blockHitResult.getBlockPos().getY(),
                  blockHitResult.getBlockPos().getZ()
               )
            : null;
      }
   }
}
