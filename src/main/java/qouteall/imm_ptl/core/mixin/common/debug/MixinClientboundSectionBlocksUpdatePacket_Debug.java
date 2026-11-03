package qouteall.imm_ptl.core.mixin.common.debug;

import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ChunkDeltaUpdateS2CPacket.class})
public class MixinClientboundSectionBlocksUpdatePacket_Debug {
}
