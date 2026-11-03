package qouteall.imm_ptl.core.mixin.client.render;

import net.minecraft.client.render.chunk.ChunkBuilder.class_851;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IERenderSection;

@Mixin({class_851.class})
public abstract class MixinRenderSection implements IERenderSection {
   private long portal_mark;
   @Shadow
   @Final
   @Mutable
   public int index;

   @Shadow
   protected abstract void clear();

   @Override
   public void portal_fullyReset() {
      this.clear();
   }

   @Override
   public long portal_getMark() {
      return this.portal_mark;
   }

   @Override
   public void portal_setMark(long arg) {
      this.portal_mark = arg;
   }

   @Override
   public void portal_setIndex(int arg) {
      this.index = arg;
   }
}
