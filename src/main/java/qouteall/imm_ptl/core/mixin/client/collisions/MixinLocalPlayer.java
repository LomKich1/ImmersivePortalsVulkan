package qouteall.imm_ptl.core.mixin.client.collisions;

import net.minecraft.util.math.BlockPos;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ducks.IEEntity;

@Mixin({ClientPlayerEntity.class})
public class MixinLocalPlayer {
   @Inject(
      method = {"Lnet/minecraft/ClientPlayerEntity;wouldCollideAt(Lnet/minecraft/BlockPos;)Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCannotFitAt(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (((IEEntity)this).ip_isRecentlyCollidingWithPortal()) {
         cir.setReturnValue(false);
         cir.cancel();
      }
   }
}
