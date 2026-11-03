package qouteall.imm_ptl.core.mixin.common.debug;

import org.spongepowered.asm.mixin.Mixin;

@Mixin(
   targets = {"net/minecraft/server/world/ChunkLevelManager$NearbyChunkTicketUpdater"}
)
public class MixinPlayerTicketTracker_Debug {
}
