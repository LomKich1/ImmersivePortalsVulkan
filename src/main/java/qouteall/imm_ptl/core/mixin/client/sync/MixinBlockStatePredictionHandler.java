package qouteall.imm_ptl.core.mixin.client.sync;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.ducks.IEClientWorld;

@Mixin({PendingUpdateManager.class})
public class MixinBlockStatePredictionHandler {
   @Shadow
   private int sequence;

   @Inject(
      method = {"incrementSequence"},
      at = {@At("RETURN")}
   )
   private void onStartPredictingEnd(CallbackInfoReturnable<PendingUpdateManager> cir) {
      for (ClientWorld clientWorld : ClientWorldLoader.getClientWorlds()) {
         PendingUpdateManager handler = ((IEClientWorld)clientWorld).ip_getBlockStatePredictionHandler();
         ((IEBlockStatePredictionHandler)handler).ip_setCurrentSequenceNumber(this.sequence);
      }
   }
}
