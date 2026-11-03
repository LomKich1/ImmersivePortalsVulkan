package qouteall.imm_ptl.core.mixin.client.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.util.hit.HitResult;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;

@Mixin({MinecraftClient.class})
public abstract class MixinMinecraft_B {
   @Shadow
   public ClientWorld world;
   @Shadow
   public HitResult crosshairTarget;
   @Shadow
   protected int attackCooldown;

   @Shadow
   protected abstract void doItemPick();

   @Shadow
   protected abstract boolean doAttack();

   @Shadow
   protected abstract void handleBlockBreaking(boolean var1);

   @Shadow
   protected abstract void doItemUse();

   @WrapOperation(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/MinecraftClient;doAttack()Z"
      )}
   )
   private boolean wrapStartAttack(MinecraftClient instance, Operation<Boolean> original) {
      ClientWorld remoteWorld = BlockManipulationClient.getRemotePointedWorld();
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> (Boolean)original.call(new Object[]{instance}), false);
         return false;
      } else {
         return (Boolean)original.call(new Object[]{instance});
      }
   }

   @WrapOperation(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/MinecraftClient;handleBlockBreaking(Z)V"
      )}
   )
   private void wrapContinueAttack(MinecraftClient instance, boolean leftClick, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> {
            original.call(new Object[]{instance, leftClick});
            return null;
         }, false);
      } else {
         original.call(new Object[]{instance, leftClick});
      }
   }

   @WrapOperation(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/MinecraftClient;doItemUse()V"
      )}
   )
   private void wrapStartUseItem(MinecraftClient instance, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         BlockManipulationClient.withSwitchedContext(() -> {
            original.call(new Object[]{instance});
            return null;
         }, true);
      } else {
         original.call(new Object[]{instance});
      }
   }

   @WrapOperation(
      method = {"handleInputEvents"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/MinecraftClient;doItemPick()V"
      )}
   )
   private void wrapPickBlock(MinecraftClient instance, Operation<Void> original) {
      if (BlockManipulationClient.isPointingToPortal()) {
         ClientWorld remoteWorld = ClientWorldLoader.getWorld(BlockManipulationClient.remotePointedDim);
         ClientWorld oldWorld = this.world;
         HitResult oldTarget = this.crosshairTarget;
         this.world = remoteWorld;
         this.crosshairTarget = BlockManipulationClient.remoteHitResult;

         try {
            original.call(new Object[]{instance});
         } finally {
            this.world = oldWorld;
            this.crosshairTarget = oldTarget;
         }
      } else {
         original.call(new Object[]{instance});
      }
   }
}
