package qouteall.imm_ptl.core.mixin.common.other_sync;

import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin({PlayerEntity.class})
public class MixinPlayer_Pose {
   @Inject(
      method = {"updatePose"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onUpdatePlayerPose(CallbackInfo ci) {
      PlayerEntity this_ = (PlayerEntity)(Object)this;
      if (!this_.getEntityWorld().isClient() && ((IEEntity)(Object)this_).ip_isRecentlyCollidingWithPortal()) {
         ci.cancel();
      }
   }
}
