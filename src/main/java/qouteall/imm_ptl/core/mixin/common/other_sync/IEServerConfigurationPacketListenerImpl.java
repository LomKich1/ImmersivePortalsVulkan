package qouteall.imm_ptl.core.mixin.common.other_sync;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.network.ServerConfigurationNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ServerConfigurationNetworkHandler.class})
public interface IEServerConfigurationPacketListenerImpl {
   @Accessor("profile")
   GameProfile ip_getGameProfile();
}
