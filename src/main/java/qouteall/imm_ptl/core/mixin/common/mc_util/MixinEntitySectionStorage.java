package qouteall.imm_ptl.core.mixin.common.mc_util;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.LongBidirectionalIterator;
import it.unimi.dsi.fastutil.longs.LongSortedSet;
import java.util.function.Function;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.entity.EntityLike;
import net.minecraft.world.entity.EntityTrackingSection;
import net.minecraft.world.entity.SectionedEntityCache;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IESectionedEntityCache;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;

@Mixin({SectionedEntityCache.class})
public class MixinEntitySectionStorage<T extends EntityLike> implements IESectionedEntityCache<T> {
   @Shadow
   @Final
   private LongSortedSet trackedPositions;
   @Shadow
   @Final
   private Long2ObjectMap<EntityTrackingSection<T>> trackingSections;

   @IPVanillaCopy
   @Nullable
   @Override
   public <R> R ip_traverseSectionInBox(
      int chunkXStart, int chunkXEnd, int chunkYStart, int chunkYEnd, int chunkZStart, int chunkZEnd, Function<EntityTrackingSection<T>, R> function
   ) {
      for (int cx = chunkXStart; cx <= chunkXEnd; cx++) {
         long xIndexStart = ChunkSectionPos.asLong(cx, 0, 0);
         long xIndexEnd = ChunkSectionPos.asLong(cx, -1, -1);
         LongBidirectionalIterator iterator = this.trackedPositions.subSet(xIndexStart, xIndexEnd + 1L).iterator();

         while (iterator.hasNext()) {
            long sectionPos = iterator.nextLong();
            int sectionY = ChunkSectionPos.unpackY(sectionPos);
            int sectionZ = ChunkSectionPos.unpackZ(sectionPos);
            if (sectionY >= chunkYStart && sectionY <= chunkYEnd && sectionZ >= chunkZStart && sectionZ <= chunkZEnd) {
               EntityTrackingSection<T> entityTrackingSection = (EntityTrackingSection<T>)this.trackingSections.get(sectionPos);
               if (entityTrackingSection != null && entityTrackingSection.getStatus().shouldTrack()) {
                  R result = function.apply(entityTrackingSection);
                  if (result != null) {
                     return result;
                  }
               }
            }
         }
      }

      return null;
   }
}
