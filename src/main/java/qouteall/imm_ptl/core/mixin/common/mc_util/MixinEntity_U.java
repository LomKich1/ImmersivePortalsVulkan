package qouteall.imm_ptl.core.mixin.common.mc_util;

import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.class_5529;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.mc_utils.IPEntityEventListenableEntity;

@Mixin({Entity.class})
public class MixinEntity_U {
   @Inject(
      method = {"Lnet/minecraft/Entity;setPos(DDD)V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/EntityChangeListener;updateEntityPosition()V"
      )}
   )
   private void onUpdateEntityPosition(double x, double y, double z, CallbackInfo ci) {
      if (this instanceof IPEntityEventListenableEntity) {
         ((IPEntityEventListenableEntity)this).ip_onEntityPositionUpdated();
      }
   }

   @Inject(
      method = {"Lnet/minecraft/Entity;setRemoved(Lnet/minecraft/Entity.RemovalReason;)V"},
      at = {@At("RETURN")}
   )
   private void onSetRemoved(class_5529 reason, CallbackInfo ci) {
      if (this instanceof IPEntityEventListenableEntity) {
         ((IPEntityEventListenableEntity)this).ip_onRemoved(reason);
      }
   }
}
