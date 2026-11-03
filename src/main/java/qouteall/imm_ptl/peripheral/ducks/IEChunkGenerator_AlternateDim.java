package qouteall.imm_ptl.peripheral.ducks;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.world.gen.feature.util.PlacedFeatureIndexer.IndexedFeatures;

public interface IEChunkGenerator_AlternateDim {
   void ip_setFeaturesPerStep(Supplier<List<IndexedFeatures>> var1);
}
