package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanChunkStatusRouter;

/**
 * Turns VulkanMod's global chunk readiness table into one table per dimension. Only calls made on
 * the installed dispatcher are redirected; every real table runs the original code on its own
 * storage. Targets VulkanMod 0.6.8 (setChunkStatus/resetChunkStatus take (int, int, byte)).
 */
@Pseudo
@Mixin(targets = "net.vulkanmod.render.chunk.ChunkStatusMap", remap = false)
public class MixinVulkanModChunkStatusMap {
   @Inject(method = "createInstance", at = @At("HEAD"), cancellable = true)
   private static void ip_installDispatcher(int renderDistance, CallbackInfo ci) {
      if (VulkanChunkStatusRouter.ensureDispatcher(renderDistance)) {
         ci.cancel();
      }
   }

   @Inject(method = "setChunkStatus", at = @At("HEAD"), cancellable = true)
   private void ip_routeChunkReady(int chunkX, int chunkZ, byte flag, CallbackInfo ci) {
      if (VulkanChunkStatusRouter.isDispatcher(this)) {
         ci.cancel();
         VulkanChunkStatusRouter.forwardSet(chunkX, chunkZ, flag);
      }
   }

   @Inject(method = "resetChunkStatus", at = @At("HEAD"), cancellable = true)
   private void ip_routeChunkNotReady(int chunkX, int chunkZ, byte flag, CallbackInfo ci) {
      if (VulkanChunkStatusRouter.isDispatcher(this)) {
         ci.cancel();
         VulkanChunkStatusRouter.forwardReset(chunkX, chunkZ, flag);
      }
   }

   @Inject(method = "chunkRenderReady", at = @At("HEAD"), cancellable = true)
   private void ip_routeReadinessQuery(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
      if (VulkanChunkStatusRouter.isDispatcher(this)) {
         cir.setReturnValue(VulkanChunkStatusRouter.forwardIsReady(chunkX, chunkZ));
      }
   }
}
