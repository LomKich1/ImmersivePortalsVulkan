package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({LivingEntity.class})
public class MixinLivingEntity {
   @Inject(
      method = {"Lnet/minecraft/LivingEntity;tick()V"},
      at = {@At("RETURN")}
   )
   private void onTickEnded(CallbackInfo ci) {
      LivingEntity this_ = (LivingEntity)this;
      if (this_.getAttacker() != null && this_.getAttacker().getEntityWorld() != this_.getEntityWorld()) {
         this_.setAttacker(null);
      }

      if (this_.getAttacking() != null && this_.getAttacking().getEntityWorld() != this_.getEntityWorld()) {
         this_.setAttacking((PlayerEntity)null, 0);
      }
   }
}
