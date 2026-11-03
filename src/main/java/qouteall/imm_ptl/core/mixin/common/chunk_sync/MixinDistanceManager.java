package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import java.util.List;
import net.minecraft.server.world.ChunkTicketManager;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicket;
import net.minecraft.server.world.ServerChunkLoadingManager;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTickets;
import qouteall.imm_ptl.core.ducks.IEChunkMap;
import qouteall.imm_ptl.core.platform_specific.IPConfig;

@Mixin({ChunkLevelManager.class})
public abstract class MixinDistanceManager implements qouteall.imm_ptl.core.ducks.IEDistanceManager {
   @Shadow
   @Final
   private Long2ObjectMap<ObjectSet<ServerPlayerEntity>> playersByChunkPos;
   @Shadow
   @Final
   private ChunkTicketManager ticketManager;

   @Inject(
      method = {"Lnet/minecraft/ChunkLevelManager;handleChunkLeave(Lnet/minecraft/ChunkSectionPos;Lnet/minecraft/ServerPlayerEntity;)V"},
      at = {@At("HEAD")}
   )
   private void onHandleChunkLeave(ChunkSectionPos sectionPos, ServerPlayerEntity serverPlayer, CallbackInfo ci) {
      long chunkPos = sectionPos.toChunkPos().toLong();
      this.playersByChunkPos.computeIfAbsent(chunkPos, k -> new ObjectOpenHashSet());
   }

   @Inject(
      method = {"update"},
      at = {@At("RETURN")}
   )
   private void onRunAllUpdates(ServerChunkLoadingManager chunkManager, CallbackInfoReturnable<Boolean> cir) {
      if (IPConfig.getConfig().enableImmPtlChunkLoading) {
         ServerWorld world = ((IEChunkMap)chunkManager).ip_getWorld();
         ImmPtlChunkTickets.get(world).flushThrottling(world);
      }
   }

   @Override
   public List<ChunkTicket> portal_getTicketSet(long chunkPos) {
      return this.ticketManager.getTickets(chunkPos);
   }
}
