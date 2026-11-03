package qouteall.imm_ptl.core.ducks;

import net.minecraft.util.math.Vec3d;

public interface IERayTraceContext {
   IERayTraceContext ip_setStart(Vec3d var1);

   IERayTraceContext ip_setEnd(Vec3d var1);
}
