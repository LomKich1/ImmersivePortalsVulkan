package qouteall.imm_ptl.core.mixin.common.debug;

import net.minecraft.server.world.LevelPrioritizedQueue;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({LevelPrioritizedQueue.class})
public class MixinChunkTaskPriorityQueue<T> {
}
