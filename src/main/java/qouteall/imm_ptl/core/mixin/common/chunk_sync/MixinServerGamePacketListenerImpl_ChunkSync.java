package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.network.packet.c2s.play.AcknowledgeChunksC2SPacket;
import net.minecraft.server.network.ChunkDataSender;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEPlayerChunkSender;

@Mixin({ServerPlayNetworkHandler.class})
public class MixinServerGamePacketListenerImpl_ChunkSync {
   @Shadow
   public ServerPlayerEntity player;
   @Shadow
   @Final
   public ChunkDataSender chunkDataSender;

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void ip_attachChunkSender(CallbackInfo ci) {
      ((IEPlayerChunkSender)this.chunkDataSender).ip_setListener((ServerPlayNetworkHandler)this);
   }

   @Inject(
      method = {"onAcknowledgeChunks"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ChunkDataSender;onAcknowledgeChunks(F)V"
      )}
   )
   private void redirectOnChunkBatchReceivedByClient(AcknowledgeChunksC2SPacket packet, CallbackInfo ci) {
      ImmPtlChunkTracking.getPlayerInfo(this.player).onChunkBatchReceivedByClient(packet.comp_1730());
   }
}
