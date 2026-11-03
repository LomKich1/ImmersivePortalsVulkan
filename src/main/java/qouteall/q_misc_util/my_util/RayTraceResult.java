package qouteall.q_misc_util.my_util;

import net.minecraft.util.math.Vec3d;

public record RayTraceResult(double t, Vec3d hitPos, Vec3d surfaceNormal) {
}
