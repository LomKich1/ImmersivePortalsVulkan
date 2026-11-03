package qouteall.imm_ptl.core.mixin.common.interaction;

import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.class_242;
import net.minecraft.world.RaycastContext.class_3960;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RaycastContext.class})
public interface IEClipContext {
   @Accessor("shapeType")
   class_3960 ip_getBlock();

   @Accessor("fluid")
   class_242 ip_getFluid();
}
