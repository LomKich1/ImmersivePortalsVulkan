package qouteall.dimlib.mixin.client;

import java.util.Set;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({ClientPlayNetworkHandler.class})
public interface IClientPacketListener {
   @Accessor("worldKeys")
   void ip_setLevels(Set<RegistryKey<World>> var1);
}
