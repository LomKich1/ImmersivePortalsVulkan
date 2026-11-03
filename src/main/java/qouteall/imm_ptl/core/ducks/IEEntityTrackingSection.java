package qouteall.imm_ptl.core.ducks;

import java.util.function.Function;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.util.TypeFilter;
import org.jetbrains.annotations.Nullable;

public interface IEEntityTrackingSection<T extends EntityLike> {
   @Nullable
   <Sub extends T, R> R ip_traverse(TypeFilter<T, Sub> var1, Function<Sub, R> var2);
}
