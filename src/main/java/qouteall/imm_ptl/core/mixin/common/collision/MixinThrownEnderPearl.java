package qouteall.imm_ptl.core.mixin.common.collision;

import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;

@Mixin({EnderPearlEntity.class})
public class MixinThrownEnderPearl {
   @Inject(
      method = {"onCollision"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/EnderPearlEntity;discard()V"
      )}
   )
   private void onOnHitDiscard(HitResult result, CallbackInfo ci) {
      EnderPearlEntity this_ = (EnderPearlEntity)this;
      if (this_.getOwner() instanceof ServerPlayerEntity serverPlayer
         && serverPlayer.networkHandler.isConnectionOpen()
         && serverPlayer.getServerWorld() != this_.getEntityWorld()
         && !serverPlayer.isSleeping()) {
         Helper.log("Doing cross-dimensional ender pearl teleportation");
         ServerTeleportationManager.teleportEntityGeneral(serverPlayer, this_.getEntityPos(), (ServerWorld)this_.getEntityWorld());
      }
   }
}
