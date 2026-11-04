package qouteall.imm_ptl.core.mixin.common.portal_generation;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LazyEntityReference;
import net.minecraft.item.ItemStack;
import net.minecraft.util.profiler.Profilers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

@Mixin({ItemEntity.class})
public abstract class MixinItemEntity_P {
   @Shadow
   @Nullable
   private LazyEntityReference<Entity> thrower;

   @Shadow
   public abstract ItemStack getStack();

   @Inject(
      method = {"Lnet/minecraft/entity/ItemEntity;tick()V"},
      at = @At("TAIL")
   )
   private void onItemTickEnded(CallbackInfo ci) {
      ItemEntity this_ = (ItemEntity)(Object)this;
      if (!this_.isRemoved()) {
         if (!this_.getEntityWorld().isClient()) {
            if (this.thrower != null) {
               Profilers.get().push("imm_ptl_item_tick");
               CustomPortalGenManager customPortalGenManager = IPPerServerInfo.of(this_.getEntityWorld().getServer()).customPortalGenManager;
               if (customPortalGenManager != null) {
                  customPortalGenManager.onItemTick(this_);
               }

               Profilers.get().pop();
            }
         }
      }
   }
}
