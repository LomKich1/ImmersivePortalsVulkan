package qouteall.imm_ptl.peripheral.mixin.common.alternate_dimension;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.class_6827;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.peripheral.ducks.IEChunkGenerator_AlternateDim;

@Mixin({ChunkGenerator.class})
public abstract class MixinChunkGenerator_AlternateDim implements IEChunkGenerator_AlternateDim {
   @Shadow
   @Final
   @Mutable
   private Supplier<List<class_6827>> indexedFeaturesListSupplier;

   @Override
   public void ip_setFeaturesPerStep(Supplier<List<class_6827>> arg) {
      this.indexedFeaturesListSupplier = arg;
   }
}
