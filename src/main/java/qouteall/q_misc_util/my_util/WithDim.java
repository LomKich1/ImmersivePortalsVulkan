package qouteall.q_misc_util.my_util;

import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;

public record WithDim<T>(RegistryKey<World> dimension, T value) {
}
