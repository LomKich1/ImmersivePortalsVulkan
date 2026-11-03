package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.render.chunk.ChunkBuilder.BuiltChunk.Task;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({Task.class})
public interface IEChunkCompileTask {
   @Accessor("cancelled")
   AtomicBoolean getIsCancelled();
}
