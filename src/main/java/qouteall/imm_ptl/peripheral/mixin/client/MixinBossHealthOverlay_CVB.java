package qouteall.imm_ptl.peripheral.mixin.client;

import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({BossBarHud.class})
public class MixinBossHealthOverlay_CVB {
   @Inject(
      method = {"Lnet/minecraft/BossBarHud;shouldThickenFog()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onShouldThickenFog(CallbackInfoReturnable<Boolean> cir) {
      cir.setReturnValue(false);
      cir.cancel();
   }
}
