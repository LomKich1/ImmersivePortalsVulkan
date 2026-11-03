package qouteall.imm_ptl.core.mixin.client.debug;

import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;

@Mixin({ClientPlayNetworkHandler.class})
public class MixinClientPacketListener_Debug {
}
