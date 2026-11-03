package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import java.util.concurrent.Executor;
import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ChunkTicketManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ChunkLevelManager.class})
public interface IEDistanceManager {
   @Accessor("ticketManager")
   ChunkTicketManager ip_getTicketStorage();

   @Accessor("mainThreadExecutor")
   Executor ip_getMainThreadExecutor();
}
