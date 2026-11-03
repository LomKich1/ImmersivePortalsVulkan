package qouteall.imm_ptl.core.mixin.common.interaction;

import net.minecraft.world.RaycastContext;
import net.minecraft.world.RaycastContext.FluidHandling;
import net.minecraft.world.RaycastContext.ShapeType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin({RaycastContext.class})
public interface IEClipContext {
   @Accessor("shapeType")
   ShapeType ip_getBlock();

   @Accessor("fluid")
   FluidHandling ip_getFluid();
}
