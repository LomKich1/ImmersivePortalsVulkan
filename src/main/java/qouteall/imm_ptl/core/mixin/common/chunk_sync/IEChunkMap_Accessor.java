package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerChunkLoadingManager.class})
public interface IEChunkMap_Accessor {
   @Accessor("chunkHolders")
   Long2ObjectLinkedOpenHashMap<ChunkHolder> ip_getVisibleChunkMap();
}
