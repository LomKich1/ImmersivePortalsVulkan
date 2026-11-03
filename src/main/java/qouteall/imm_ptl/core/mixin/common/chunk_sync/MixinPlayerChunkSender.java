package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ChunkDataSender;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEPlayerChunkSender;
import qouteall.q_misc_util.platform.Platform;

@Mixin({ChunkDataSender.class})
public class MixinPlayerChunkSender implements IEPlayerChunkSender {
   @Shadow
   @Final
   private static Logger LOGGER;
   @Unique
   @Nullable
   private ServerPlayNetworkHandler ip_listener;
   @Unique
   private static final AtomicBoolean ip_unexpectedCallerReported = new AtomicBoolean();
   @Unique
   private static volatile Boolean ip_vmpLoaded;

   @Overwrite
   public void add(WorldChunk levelChunk) {
   }

   @Overwrite
   public void unload(ServerPlayerEntity serverPlayer, ChunkPos chunkPos) {
   }

   @Inject(
      method = {"sendChunkBatches"},
      at = {@At("HEAD")},
      cancellable = true
   )
   public void sendNextChunks(ServerPlayerEntity serverPlayer, CallbackInfo ci) {
      ImmPtlChunkTracking.getPlayerInfo(serverPlayer).doChunkSending(serverPlayer);
      ci.cancel();
   }

   @Overwrite
   public void onAcknowledgeChunks(float f) {
   }

   @Override
   public void ip_setListener(ServerPlayNetworkHandler listener) {
      this.ip_listener = listener;
   }

   @Overwrite
   public boolean isInNextBatch(long l) {
      ServerPlayNetworkHandler listener = this.ip_listener;
      ServerPlayerEntity player = listener == null ? null : listener.player;
      if (player == null) {
         LOGGER.error("This should not be called", new Throwable());
         return false;
      } else {
         if (!ip_isVmpLoaded() && ip_unexpectedCallerReported.compareAndSet(false, true)) {
            LOGGER.warn(
               "PlayerChunkSender.isPending was called without Very Many Players loaded: some code bypasses ImmPtl's chunk tracking. Answered from ImmPtl's own records; reported once per run.",
               new Throwable("isPending caller")
            );
         }

         return ImmPtlChunkTracking.isChunkPendingToPlayer(player, player.getServerWorld().getRegistryKey(), l);
      }
   }

   @Unique
   private static boolean ip_isVmpLoaded() {
      Boolean cached = ip_vmpLoaded;
      if (cached == null) {
         cached = Platform.get().isModLoaded("vmp");
         ip_vmpLoaded = cached;
      }

      return cached;
   }
}
