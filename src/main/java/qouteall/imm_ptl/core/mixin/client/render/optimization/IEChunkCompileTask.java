package qouteall.imm_ptl.core.mixin.client.render.optimization;

import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.render.chunk.ChunkBuilder.class_851.class_4577;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({class_4577.class})
public interface IEChunkCompileTask {
   @Accessor("cancelled")
   AtomicBoolean getIsCancelled();
}
