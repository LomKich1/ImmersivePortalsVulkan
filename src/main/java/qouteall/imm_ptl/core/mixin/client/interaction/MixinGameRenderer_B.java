package qouteall.imm_ptl.core.mixin.client.interaction;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationClient;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;

@Mixin({GameRenderer.class})
public class MixinGameRenderer_B {
   @Inject(
      method = {"Lnet/minecraft/client/render/GameRenderer;updateCrosshairTarget(F)V"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onUpdateTargetedEntity(float partialTick, CallbackInfo ci) {
      if (MinecraftClient.getInstance().world != null && WorldRenderInfo.isRendering()) {
         ci.cancel();
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/render/GameRenderer;updateCrosshairTarget(F)V"},
      at = {@At("RETURN")}
   )
   private void onUpdateTargetedEntityFinish(float partialTick, CallbackInfo ci) {
      if (MinecraftClient.getInstance().world != null) {
         BlockManipulationClient.updatePointedBlock(partialTick);
      }
   }
}
