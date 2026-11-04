package qouteall.imm_ptl.core.mixin.common.position_sync;

import java.util.Set;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPosition;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.TeleportConfirmC2SPacket;
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.ducks.IEPlayerMoveC2SPacket;
import qouteall.imm_ptl.core.ducks.IEPlayerPositionLookS2CPacket;
import qouteall.imm_ptl.core.ducks.IEServerPlayNetworkHandler;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.my_util.CountDownInt;

@Mixin(
   value = {ServerPlayNetworkHandler.class},
   priority = 900
)
public abstract class MixinServerGamePacketListenerImpl implements IEServerPlayNetworkHandler {
   @Shadow
   public ServerPlayerEntity player;
   @Shadow
   private Vec3d requestedTeleportPos;
   @Shadow
   private int requestedTeleportId;
   @Shadow
   private int lastTeleportCheckTicks;
   @Shadow
   private int ticks;
   @Shadow
   private double updatedRiddenX;
   @Shadow
   private double updatedRiddenY;
   @Shadow
   private double updatedRiddenZ;
   @Shadow
   private double lastTickRiddenX;
   @Shadow
   private double lastTickRiddenY;
   @Shadow
   private double lastTickRiddenZ;
   @Shadow
   private Entity topmostRiddenEntity;
   @Shadow
   private boolean vehicleFloating;
   @Shadow
   @Final
   static Logger LOGGER;
   @Shadow
   private boolean floating;
   @Unique
   private static final CountDownInt LOG_LIMIT = new CountDownInt(20);
   @Unique
   private int ip_wrongMovePacketCount = 0;
   @Unique
   @Nullable
   private RegistryKey<World> ip_dimOfAwaitingPosition;

   @Shadow
   public abstract ServerPlayerEntity getPlayer();

   @Inject(
      method = {"Lnet/minecraft/server/network/ServerPlayNetworkHandler;onPlayerMove(Lnet/minecraft/network/packet/c2s/play/PlayerMoveC2SPacket;)V"},
      at = @At(
         value = "INVOKE",
         shift = Shift.AFTER,
         target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/server/world/ServerWorld;)V"
      ),
      cancellable = true
   )
   private void onProcessMovePacket(PlayerMoveC2SPacket packet, CallbackInfo ci) {
      RegistryKey<World> packetDimension = ((IEPlayerMoveC2SPacket)(Object)packet).ip_getPlayerDimension();
      if (packetDimension == null) {
         LOGGER.error("Player move packet is missing dimension info. Maybe the player client doesn't install iPortal");
         ServerTaskList.of(this.player.server).addTask(() -> {
            this.player.networkHandler.disconnect(Text.literal("The client does not have Immersive Portals mod"));
            return true;
         });
      } else {
         if (this.player.getEntityWorld().getRegistryKey() != packetDimension) {
            if (LOG_LIMIT.tryDecrement()) {
               LOGGER.info(
                  "[ImmPtl] Ignoring player move packet. Player: {} Packet: {} {} {} {}",
                  new Object[]{
                     this.player, packetDimension.getValue(), packet.getX(this.player.getX()), packet.getY(this.player.getY()), packet.getZ(this.player.getZ())
                  }
               );
            }

            this.ip_wrongMovePacketCount++;
            if (this.ip_wrongMovePacketCount > 10) {
               LOGGER.info(
                  "[ImmPtl] Force move player {} {} {}",
                  new Object[]{this.player, this.player.getEntityWorld().getRegistryKey().getValue(), this.player.getEntityPos()}
               );
               ServerTeleportationManager.of(this.player.server)
                  .forceTeleportPlayer(this.player, this.player.getEntityWorld().getRegistryKey(), this.player.getEntityPos());
               this.ip_wrongMovePacketCount = 0;
            }

            ci.cancel();
         } else {
            this.ip_wrongMovePacketCount = 0;
         }
      }
   }

   @Overwrite
   @IPVanillaCopy
   public void requestTeleport(EntityPosition posMoveRot, Set<PositionFlag> relativeAttrs) {
      if (this.player.getRemovalReason() != null) {
         LOGGER.error("[ImmPtl] Tries to send player pos packet to a removed player {}", this.player, new Throwable());
      } else {
         if (IPConfig.getConfig().serverTeleportLogging) {
            LOGGER.info(
               "Teleporting player {} to {} {} {} {}",
               new Object[]{
                  this.player,
                  this.player.getEntityWorld().getRegistryKey().getValue(),
                  posMoveRot.position().x,
                  posMoveRot.position().y,
                  posMoveRot.position().z
               }
            );
         }

         this.lastTeleportCheckTicks = this.ticks;
         if (++this.requestedTeleportId == Integer.MAX_VALUE) {
            this.requestedTeleportId = 0;
         }

         this.player.setPosition(posMoveRot, relativeAttrs);
         this.requestedTeleportPos = this.player.getEntityPos();
         this.ip_dimOfAwaitingPosition = this.player.getEntityWorld().getRegistryKey();
         PlayerPositionLookS2CPacket lookPacket = PlayerPositionLookS2CPacket.of(this.requestedTeleportId, posMoveRot, relativeAttrs);
         ((IEPlayerPositionLookS2CPacket)(Object)lookPacket).ip_setPlayerDimension(this.player.getEntityWorld().getRegistryKey());
         this.player.networkHandler.sendPacket(lookPacket);
      }
   }

   @Inject(
      method = {"isEntityNotCollidingWithBlocks"},
      at = @At("HEAD"),
      cancellable = true
   )
   @IPVanillaCopy
   private void onIsPlayerCollidingWithAnythingNew(
      WorldView level, Entity entity, Box playerBB, double newX, double newY, double newZ, CallbackInfoReturnable<Boolean> cir
   ) {
      if (IPGlobal.crossPortalCollision) {
         Box activePlayerBB = ((IEEntity)(Object)entity).ip_getActiveCollisionBox(playerBB);
         if (activePlayerBB == null) {
            cir.setReturnValue(false);
         } else {
            Box newBB = entity.getBoundingBox().offset(newX - entity.getX(), newY - entity.getY(), newZ - entity.getZ());
            Box activeNewBB = ((IEEntity)(Object)entity).ip_getActiveCollisionBox(newBB);
            if (activeNewBB == null) {
               cir.setReturnValue(false);
            } else {
               Iterable<VoxelShape> newBBCollisions = level.getCollisions(entity, activeNewBB.contract(1.0E-5F), playerBB.getHorizontalCenter());
               VoxelShape activePlayerBBShape = VoxelShapes.cuboid(activePlayerBB.contract(1.0E-5F));

               for (VoxelShape shape : newBBCollisions) {
                  if (!VoxelShapes.matchesAnywhere(shape, activePlayerBBShape, BooleanBiFunction.AND)) {
                     cir.setReturnValue(true);
                     return;
                  }
               }

               cir.setReturnValue(false);
            }
         }
      }
   }

   @Inject(
      method = {"tick"},
      at = @At("HEAD")
   )
   private void onTick(CallbackInfo ci) {
      if (((IEEntity)(Object)this.player).ip_isRecentlyCollidingWithPortal()) {
         this.floating = false;
      }
   }

   @Inject(
      method = {"onTeleportConfirm"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/network/ServerPlayerEntity;updatePositionAndAngles(DDDFF)V"
      )
   )
   private void onHandleAcceptTeleportPacket(TeleportConfirmC2SPacket packet, CallbackInfo ci) {
      if (this.ip_dimOfAwaitingPosition == null) {
         LOGGER.error("[ImmPtl] ip_dimOfAwaitingPosition is null {}", this.player);
      } else {
         if (this.ip_dimOfAwaitingPosition != this.player.getEntityWorld().getRegistryKey()) {
            LOGGER.info("Accepted teleport to another dimension {} {}", this.ip_dimOfAwaitingPosition, this.requestedTeleportPos);
            ServerWorld destWorld = this.player.server.getWorld(this.ip_dimOfAwaitingPosition);
            if (destWorld == null) {
               LOGGER.error("[ImmPtl] Cannot find destination world {}", this.ip_dimOfAwaitingPosition.getValue());
               return;
            }

            ServerTeleportationManager.of(this.player.server).forceTeleportPlayer(this.player, this.ip_dimOfAwaitingPosition, this.requestedTeleportPos, false);
            this.ip_dimOfAwaitingPosition = null;
         }
      }
   }

   @Inject(
      method = {"onClientCommand"},
      at = @At(
         value = "FIELD",
         target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;requestedTeleportPos:Lnet/minecraft/util/math/Vec3d;",
         opcode = 181
      )
   )
   private void onTeleportPlayerCancelSleeping(ClientCommandC2SPacket packet, CallbackInfo ci) {
      this.ip_dimOfAwaitingPosition = this.player.getEntityWorld().getRegistryKey();
   }

   @Override
   public boolean ip_hasAwaitingTeleport() {
      return this.requestedTeleportPos != null;
   }
}
