package qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler.NoiseParameters;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctions;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({DensityFunctions.class})
public interface IENoiseRouterData {
   @Invoker("createEndNoiseRouter")
   static NoiseRouter ip_end(RegistryEntryLookup<DensityFunction> holderGetter) {
      throw new RuntimeException();
   }

   @Invoker("createMultiNoiseDependentNoiseRouter")
   static NoiseRouter ip_noNewCaves(
      RegistryEntryLookup<DensityFunction> holderGetter, RegistryEntryLookup<NoiseParameters> holderGetter2, DensityFunction densityFunction
   ) {
      throw new RuntimeException();
   }

   @Invoker("applyFloatingIslandsSlides")
   static DensityFunction ip_slideEndLike(DensityFunction densityFunction, int i, int j) {
      throw new RuntimeException();
   }

   @Invoker("entryHolder")
   static DensityFunction ip_getFunction(RegistryEntryLookup<DensityFunction> holderGetter, RegistryKey<DensityFunction> resourceKey) {
      throw new RuntimeException();
   }

   @Accessor("BASE_3D_NOISE_END")
   static RegistryKey<DensityFunction> get_BASE_3D_NOISE_END() {
      throw new RuntimeException();
   }
}
