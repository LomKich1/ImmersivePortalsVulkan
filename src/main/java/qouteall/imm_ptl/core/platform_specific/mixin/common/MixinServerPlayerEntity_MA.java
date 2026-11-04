package qouteall.imm_ptl.core.platform_specific.mixin.common;

import java.util.Set;
import net.minecraft.network.packet.s2c.play.PositionFlag;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.TeleportTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.mc_utils.ServerTaskList;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

@Mixin({ServerPlayerEntity.class})
public class MixinServerPlayerEntity_MA {
   @Inject(
      method = {"teleportTo(Lnet/minecraft/world/TeleportTarget;)Lnet/minecraft/server/network/ServerPlayerEntity;"},
      at = @At("HEAD")
   )
   private void onChangeDimensionByVanilla(TeleportTarget dimensionTransition, CallbackInfoReturnable<ServerPlayerEntity> cir) {
      ServerPlayerEntity this_ = (ServerPlayerEntity)(Object)this;
      if (dimensionTransition.world() != this_.getEntityWorld()) {
         onBeforeDimensionTravel(this_);
      }
   }

   @Inject(
      method = {"teleport(Lnet/minecraft/server/world/ServerWorld;DDDLjava/util/Set;FFZ)Z"},
      at = @At("HEAD")
   )
   private void onTeleported(
      ServerWorld targetWorld,
      double x,
      double y,
      double z,
      Set<PositionFlag> relatives,
      float yaw,
      float pitch,
      boolean setCamera,
      CallbackInfoReturnable<Boolean> cir
   ) {
      ServerPlayerEntity this_ = (ServerPlayerEntity)(Object)this;
      if (this_.getEntityWorld() != targetWorld) {
         onBeforeDimensionTravel(this_);
      }
   }

   private static void onBeforeDimensionTravel(ServerPlayerEntity player) {
      CustomPortalGenManager customPortalGenManager = IPPerServerInfo.of(player.server).customPortalGenManager;
      if (customPortalGenManager != null) {
         customPortalGenManager.onBeforeConventionalDimensionChange(player);
         ImmPtlChunkTracking.removePlayerFromChunkTrackersAndEntityTrackers(player);
         ServerTaskList.of(player.server).addTask(() -> {
            customPortalGenManager.onAfterConventionalDimensionChange(player);
            return true;
         });
      }
   }
}
