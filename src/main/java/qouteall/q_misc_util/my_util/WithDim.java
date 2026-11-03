package qouteall.q_misc_util.my_util;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.World;

public record WithDim<T>(RegistryKey<World> dimension, T value) {
}
