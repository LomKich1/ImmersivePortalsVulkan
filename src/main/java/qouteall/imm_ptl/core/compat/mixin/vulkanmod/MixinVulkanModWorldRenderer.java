package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanModAccess;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanTerrainBridge;

/**
 * Keeps VulkanMod's single terrain renderer bound to the world the player is in. See
 * VulkanTerrainBridge. No @Shadow members are used on purpose: the bound level is read and
 * rebound through VulkanModAccess, so a different VulkanMod build cannot make this mixin fail to
 * apply; injections that find no target are skipped (require = 0) and simply do nothing.
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.chunk.WorldRenderer", remap = false)
public class MixinVulkanModWorldRenderer {
   private boolean ip_foreign() {
      return VulkanTerrainBridge.isForeignWorld(VulkanModAccess.getWorldRendererLevel(this));
   }

   @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_gateSetLevel(ClientWorld requested, CallbackInfo ci) {
      if (!VulkanTerrainBridge.allowSetLevel(requested)) {
         ci.cancel();
      }
   }

   @Inject(method = "allChanged", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_gateAllChanged(CallbackInfo ci) {
      if (!VulkanTerrainBridge.allowAllChanged(VulkanModAccess.getWorldRendererLevel(this))) {
         ci.cancel();
      }
   }

   @Inject(method = "setupRenderer", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_bindOrSkipSetup(CallbackInfo ci) {
      if (!VulkanTerrainBridge.prepareForFrame(this)) {
         ci.cancel();
      }
   }

   @Inject(method = "renderSectionLayer", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_skipForeignLayer(CallbackInfo ci) {
      if (ip_foreign()) {
         ci.cancel();
      }
   }

   @Inject(method = "renderBlockEntities", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_skipForeignBlockEntities(CallbackInfo ci) {
      if (ip_foreign()) {
         ci.cancel();
      }
   }

   @Inject(method = "setSectionDirty", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_skipForeignDirty(CallbackInfo ci) {
      if (ip_foreign()) {
         ci.cancel();
      }
   }

   @Inject(method = "isSectionCompiled", at = @At("HEAD"), cancellable = true, require = 0)
   private void ip_foreignNotCompiled(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
      if (ip_foreign()) {
         cir.setReturnValue(false);
      }
   }
}
