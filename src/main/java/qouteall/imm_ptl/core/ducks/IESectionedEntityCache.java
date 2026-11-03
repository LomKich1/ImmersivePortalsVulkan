package qouteall.imm_ptl.core.ducks;

import java.util.function.Function;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.jetbrains.annotations.Nullable;

public interface IESectionedEntityCache<T extends EntityLike> {
   @Nullable
   <R> R ip_traverseSectionInBox(int var1, int var2, int var3, int var4, int var5, int var6, Function<EntityTrackingSection<T>, R> var7);
}
