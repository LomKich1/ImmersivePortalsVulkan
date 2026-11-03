package qouteall.imm_ptl.core.mixin.common.collision;

import java.util.List;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin({Entity.class})
public interface IEEntity_Collision {
   @Invoker("adjustMovementForCollisions")
   static Vec3d ip_CollideWithShapes(Vec3d vec3, Box aABB, List<VoxelShape> list) {
      throw new UnsupportedOperationException();
   }
}
