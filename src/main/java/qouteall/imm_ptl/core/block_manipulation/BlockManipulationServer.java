package qouteall.imm_ptl.core.block_manipulation;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.util.Hand;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Pair;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.ActionResult.class_9860;
import net.minecraft.util.ActionResult.class_9861;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.class_2847;
import net.minecraft.world.RaycastContext.class_3960;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.ScaleUtils;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalUtils;
import qouteall.imm_ptl.core.portal.global_portals.GlobalPortalStorage;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class BlockManipulationServer {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static final ThreadLocal<BlockManipulationServer.Context> REDIRECT_CONTEXT = ThreadLocal.withInitial(() -> null);
   public static final Event<Predicate<PlayerEntity>> canDoCrossPortalInteractionEvent = EventFactory.createArrayBacked(Predicate.class, handlers -> player -> {
      for (Predicate<PlayerEntity> handler : handlers) {
         if (!handler.test(player)) {
            return false;
         }
      }

      return true;
   });

   private static boolean canPlayerReach(RegistryKey<World> dimension, ServerPlayerEntity player, BlockPos requestPos) {
      if (!canDoCrossPortalInteractionEvent.invoker().test(player)) {
         return false;
      } else {
         double playerScale = ScaleUtils.computeBlockReachScale(player);
         Vec3d pos = Vec3d.ofCenter(requestPos);
         Vec3d playerPos = player.getEntityPos();
         double distanceSquare = 576.0 * playerScale * playerScale;
         return player.getServerWorld().getRegistryKey() == dimension && playerPos.squaredDistanceTo(pos) < distanceSquare
            ? true
            : IPMcHelper.getNearbyPortals(player, IPGlobal.maxNormalPortalRadius)
               .anyMatch(
                  portal -> portal.getDestDim() == dimension
                     && portal.isInteractableBy(player)
                     && portal.transformPoint(playerPos).squaredDistanceTo(pos) < distanceSquare * portal.getScale() * portal.getScale()
               );
      }
   }

   public static Pair<BlockHitResult, RegistryKey<World>> getHitResultForPlacing(World world, BlockHitResult blockHitResult) {
      Direction side = blockHitResult.getSide();
      Vec3d sideVec = Vec3d.of(side.getVector());
      BlockPos hitPos = blockHitResult.getBlockPos();
      Vec3d hitCenter = Vec3d.ofCenter(hitPos);
      List<Portal> globalPortals = GlobalPortalStorage.getGlobalPortals(world);
      Portal portal = globalPortals.stream()
         .filter(
            p -> p.getNormal().dotProduct(sideVec) < -0.9
               && p.getPortalShape().isBoxInPortalProjection(p.getThisSideState(), new Box(hitPos))
               && p.getDistanceToPlane(hitCenter) < 0.6
         )
         .findFirst()
         .orElse(null);
      if (portal == null) {
         return new Pair(blockHitResult, world.getRegistryKey());
      } else {
         Vec3d newCenter = portal.transformPoint(hitCenter.add(sideVec.multiply(0.501)));
         BlockPos placingBlockPos = BlockPos.ofFloored(newCenter);
         BlockHitResult newHitResult = new BlockHitResult(Vec3d.ZERO, side.getOpposite(), placingBlockPos, blockHitResult.isInsideBlock());
         return new Pair(newHitResult, portal.getDestDim());
      }
   }

   public static void init() {
   }

   private static void withRedirect(BlockManipulationServer.Context context, Runnable runnable) {
      BlockManipulationServer.Context original = REDIRECT_CONTEXT.get();
      REDIRECT_CONTEXT.set(context);

      try {
         PacketRedirection.withForceRedirect(context.world(), runnable);
      } finally {
         REDIRECT_CONTEXT.set(original);
      }
   }

   @IPVanillaCopy
   private static void doProcessPlayerAction(ServerWorld world, ServerPlayerEntity player, PlayerActionC2SPacket packet) {
      player.updateLastActionTime();
      BlockPos blockPos = packet.getPos();
      class_2847 action = packet.getAction();
      if (!canPlayerReach(world.getRegistryKey(), player, blockPos)) {
         LOGGER.error("Reject cross-portal action {} {} {}", new Object[]{player, world, blockPos});
      } else {
         if (isAttackingAction(action)) {
            player.interactionManager.processBlockBreakingAction(blockPos, action, packet.getDirection(), world.getTopYInclusive() + 1, packet.getSequence());
            player.networkHandler.updateSequence(packet.getSequence());
         }
      }
   }

   public static boolean isAttackingAction(class_2847 action) {
      return action == class_2847.field_12968 || action == class_2847.field_12971 || action == class_2847.field_12973;
   }

   @IPVanillaCopy
   private static void doProcessUseItemOn(ServerWorld world, ServerPlayerEntity player, PlayerInteractBlockC2SPacket packet) {
      player.networkHandler.updateSequence(packet.getSequence());
      Hand hand = packet.getHand();
      BlockHitResult blockHitResult = packet.getBlockHitResult();
      RegistryKey<World> dimension = world.getRegistryKey();
      ItemStack itemStack = player.getStackInHand(hand);
      if (itemStack.isItemEnabled(world.getEnabledFeatures())) {
         BlockPos blockPos = blockHitResult.getBlockPos();
         Direction direction = blockHitResult.getSide();
         player.updateLastActionTime();
         if (world.canEntityModifyAt(player, blockPos)) {
            if (!canPlayerReach(dimension, player, blockPos)) {
               LOGGER.error("Reject cross-portal action {} {} {}", new Object[]{player, world, blockPos});
               return;
            }

            if (player.interactionManager.interactBlock(player, world, itemStack, hand, blockHitResult) instanceof class_9860 success
               && success.comp_2909() == class_9861.field_52428) {
               player.swingHand(hand, true);
            }
         }

         PacketRedirection.sendRedirectedMessage(player, dimension, new BlockUpdateS2CPacket(world, blockPos));
         BlockPos offseted = blockPos.method_10093(direction);
         if (offseted.getY() >= world.getBottomY() && offseted.getY() < world.getTopYInclusive() + 1) {
            PacketRedirection.sendRedirectedMessage(player, dimension, new BlockUpdateS2CPacket(world, offseted));
         }
      }
   }

   public static boolean validateReach(PlayerEntity player, World targetWorld, BlockPos targetPos) {
      PortalUtils.PortalAwareRaytraceResult result = PortalUtils.portalAwareRayTrace(
         player.getEntityWorld(), player.getEyePos(), player.getRotationVec(1.0F), 32.0, player, class_3960.field_17558
      );
      return result != null && result.world() == targetWorld && result.hitResult().getBlockPos().getManhattanDistance(targetPos) < 8;
   }

   public record Context(ServerWorld world, @Nullable BlockHitResult blockHitResult) {
   }

   public static class RemoteCallables {
      public static void processPlayerActionPacket(ServerPlayerEntity player, RegistryKey<World> dimension, byte[] packetBytes) {
         PacketByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
         PlayerActionC2SPacket packet = (PlayerActionC2SPacket)PlayerActionC2SPacket.CODEC.decode(buf);
         ServerWorld world = player.server.getWorld(dimension);
         Validate.notNull(world, "missing %s", new Object[]{dimension.getValue()});
         BlockManipulationServer.withRedirect(
            new BlockManipulationServer.Context(world, null), () -> BlockManipulationServer.doProcessPlayerAction(world, player, packet)
         );
      }

      public static void processUseItemOnPacket(ServerPlayerEntity player, RegistryKey<World> dimension, byte[] packetBytes) {
         PacketByteBuf buf = IPMcHelper.bytesToBuf(packetBytes);
         PlayerInteractBlockC2SPacket packet = (PlayerInteractBlockC2SPacket)PlayerInteractBlockC2SPacket.CODEC.decode(buf);
         ServerWorld world = player.server.getWorld(dimension);
         Validate.notNull(world, "missing %s", new Object[]{dimension.getValue()});
         BlockManipulationServer.withRedirect(
            new BlockManipulationServer.Context(world, packet.getBlockHitResult()), () -> BlockManipulationServer.doProcessUseItemOn(world, player, packet)
         );
      }
   }
}
