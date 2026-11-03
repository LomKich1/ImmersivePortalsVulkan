package qouteall.imm_ptl.core.ducks;

import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Vec3d;

public interface IEServerPlayerEntity {
   void ip_stopRidingWithoutTeleportRequest();

   void ip_startRidingWithoutTeleportRequest(Entity var1);

   void portal_worldChanged(ServerWorld var1, Vec3d var2);
}
