package qouteall.imm_ptl.core.mixin.common.chunk_sync;

import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerCommonNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerCommonNetworkHandler.class})
public interface IEServerCommonPacketListenerImpl {
   @Accessor("connection")
   ClientConnection ip_getConnection();
}
