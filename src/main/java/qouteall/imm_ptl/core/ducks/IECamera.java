package qouteall.imm_ptl.core.ducks;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.world.ClientWorld;

public interface IECamera {
   void ip_resetState(Vec3d var1, ClientWorld var2);

   void portal_setPos(Vec3d var1);

   float ip_getCameraY();

   float ip_getLastCameraY();

   void ip_setCameraY(float var1, float var2);

   void portal_setFocusedEntity(Entity var1);

   void portal_tickAttributeProbe();
}
