package qouteall.imm_ptl.core.mixin.client.collisions;

import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin({ClientPlayerEntity.class})
public class MixinLocalPlayer {
   @Inject(
      method = {"Lnet/minecraft/client/network/ClientPlayerEntity;wouldCollideAt(Lnet/minecraft/util/math/BlockPos;)Z"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onCannotFitAt(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (((IEEntity)(Object)this).ip_isRecentlyCollidingWithPortal()) {
         cir.setReturnValue(false);
         cir.cancel();
      }
   }
}
