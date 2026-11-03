package qouteall.imm_ptl.core.mixin.common.portal_generation;

import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.IPPerServerInfo;
import qouteall.imm_ptl.core.portal.custom_portal_gen.CustomPortalGenManager;

@Mixin({ItemStack.class})
public class MixinItemStack {
   @Inject(
      method = {"Lnet/minecraft/item/ItemStack;useOnBlock(Lnet/minecraft/item/ItemUsageContext;)Lnet/minecraft/util/ActionResult;"},
      at = {@At("RETURN")}
   )
   private void onUseOnBlockEnded(ItemUsageContext context, CallbackInfoReturnable<ActionResult> cir) {
      World world = context.getWorld();
      if (!world.isClient()) {
         CustomPortalGenManager customPortalGenManager = IPPerServerInfo.of(world.getServer()).customPortalGenManager;
         if (customPortalGenManager != null) {
            customPortalGenManager.onItemUse(context, (ActionResult)cir.getReturnValue());
         }
      }
   }
}
