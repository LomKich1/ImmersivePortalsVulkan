package qouteall.imm_ptl.peripheral.mixin.client.portal_wand;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.peripheral.wand.PortalWandItem;

@Mixin({MinecraftClient.class})
public class MixinMinecraft_PortalWand {
   @Shadow
   @Nullable
   public ClientPlayerEntity player;

   @Inject(
      method = {"doAttack"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getStackInHand(Lnet/minecraft/util/Hand;)Lnet/minecraft/item/ItemStack;"
      ),
      cancellable = true
   )
   private void onStartingAttack(CallbackInfoReturnable<Boolean> cir) {
      assert this.player != null;

      ItemStack itemStack = this.player.getMainHandStack();
      if (itemStack.getItem() instanceof PortalWandItem) {
         PortalWandItem.onClientLeftClick(this.player, itemStack);
         cir.setReturnValue(false);
      }
   }
}
