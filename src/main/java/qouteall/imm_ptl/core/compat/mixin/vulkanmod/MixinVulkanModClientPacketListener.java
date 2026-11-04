package qouteall.imm_ptl.core.compat.mixin.vulkanmod;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.network.packet.s2c.play.UnloadChunkS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.compat.vulkanmod_compatibility.VulkanChunkStatusRouter;

/**
 * Records which world light data (or a chunk forget) is for, before VulkanMod marks the chunk. In
 * 1.21.11 the light method has four parameters (the 1.21.1 fork's version has three). Block data
 * needs no counterpart: ImmPtlClientChunkMap replaces the client chunk cache and reports it itself.
 */
@Mixin(ClientPlayNetworkHandler.class)
public class MixinVulkanModClientPacketListener {
   @Shadow
   private ClientWorld world;

   @Inject(method = "readLightData", at = @At("HEAD"))
   private void ip_markLightDimension(int x, int z, LightData lightData, boolean flag, CallbackInfo ci) {
      VulkanChunkStatusRouter.setWriteDimension(this.world.getRegistryKey());
   }

   @Inject(method = "unloadChunk", at = @At("HEAD"))
   private void ip_markForgetDimension(UnloadChunkS2CPacket packet, CallbackInfo ci) {
      VulkanChunkStatusRouter.setWriteDimension(this.world.getRegistryKey());
   }
}
