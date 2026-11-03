package qouteall.imm_ptl.core.mixin.common.mc_util;

import java.util.Collection;
import java.util.function.Function;
import net.minecraft.util.TypeFilter;
import net.minecraft.util.collection.TypeFilterableList;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEEntityTrackingSection;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@Mixin({EntityTrackingSection.class})
public class MixinEntitySection<T extends EntityLike> implements IEEntityTrackingSection<T> {
   @Shadow
   @Final
   private TypeFilterableList<T> collection;

   @IPVanillaCopy
   @Nullable
   @Override
   public <Sub extends T, R> R ip_traverse(TypeFilter<T, Sub> type, Function<Sub, R> func) {
      Class<? extends T> baseClass = type.getBaseClass();
      Collection<? extends T> collection = this.collection.getAllOfType(baseClass);
      if (collection.isEmpty()) {
         return null;
      } else {
         for (T entity1 : collection) {
            Sub entity2 = (Sub)type.downcast(entity1);
            if (entity2 != null) {
               R result = func.apply(entity2);
               if (result != null) {
                  return result;
               }
            }
         }

         return null;
      }
   }
}
