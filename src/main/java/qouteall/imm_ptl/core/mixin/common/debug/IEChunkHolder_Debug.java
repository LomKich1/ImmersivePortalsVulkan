package qouteall.imm_ptl.core.mixin.common.debug;

import java.util.concurrent.Executor;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerChunkLoadingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({ChunkHolder.class})
public interface IEChunkHolder_Debug {
   @Invoker("updateFutures")
   void ip_updateFutures(ServerChunkLoadingManager var1, Executor var2);
}
