package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.List;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.HeightLimitView;
import net.minecraft.server.world.ChunkHolder.class_3897;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.chunk_loading.ImmPtlChunkTracking;
import qouteall.imm_ptl.core.ducks.IEChunkHolder;
import qouteall.imm_ptl.core.network.PacketRedirection;

@Mixin({ChunkHolder.class})
public class MixinChunkHolder implements IEChunkHolder {
   @Shadow
   @Final
   private HeightLimitView world;

   @ModifyVariable(
      method = {"sendPacketToPlayers"},
      at = {@At("HEAD")},
      argsOnly = true
   )
   private Packet<?> modifyPacket(Packet<?> packet) {
      ServerWorld serverWorld = (ServerWorld)this.world;
      return PacketRedirection.createRedirectedMessage(serverWorld.getServer(), serverWorld.getRegistryKey(), (Packet<ClientPlayPacketListener>)packet);
   }

   @Redirect(
      method = {"flushUpdates"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ChunkHolder.PlayersWatchingChunkProvider;getPlayersWatchingChunk(Lnet/minecraft/ChunkPos;Z)Ljava/util/List;"
      )}
   )
   private List<ServerPlayerEntity> redirectGetPlayers(class_3897 playerProvider, ChunkPos chunkPos, boolean boundaryOnly) {
      return ImmPtlChunkTracking.getPlayersViewingChunk(((World)this.world).getRegistryKey(), chunkPos.x, chunkPos.z, boundaryOnly);
   }
}
