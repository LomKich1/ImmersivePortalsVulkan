package qouteall.imm_ptl.core.mixin.common.debug;

import net.minecraft.util.collection.IndexedIterable;
import net.minecraft.world.chunk.BiMapPalette;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({BiMapPalette.class})
public class MixinHashMapPalette<T> {
   @Redirect(
      method = {"writePacket"},
      at = @At(
         value = "INVOKE",
         target = "Lnet/minecraft/util/collection/IndexedIterable;getRawId(Ljava/lang/Object;)I"
      )
   )
   private int onGetId(IndexedIterable<T> idMap, T object) {
      int id = idMap.getRawId(object);
      if (id == -1) {
         throw new RuntimeException(
            "(Note: ImmPtl is just doing checking to help debugging) Cannot find id for %s. Something is wrong with registries.".formatted(object)
         );
      } else {
         return id;
      }
   }
}
