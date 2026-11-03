package qouteall.imm_ptl.core.ducks;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.collision.PortalCollisionHandler;
import qouteall.imm_ptl.core.portal.Portal;

public interface IEEntity {
   void ip_notifyCollidingWithPortal(Entity var1);

   @Nullable
   PortalCollisionHandler ip_getPortalCollisionHandler();

   PortalCollisionHandler ip_getOrCreatePortalCollisionHandler();

   void ip_setPortalCollisionHandler(@Nullable PortalCollisionHandler var1);

   @Deprecated
   @Nullable
   Portal ip_getCollidingPortal();

   void ip_tickCollidingPortal();

   boolean ip_isCollidingWithPortal();

   boolean ip_isRecentlyCollidingWithPortal();

   void ip_clearCollidingPortal();

   void ip_unsetRemoved();

   @Nullable
   Box ip_getActiveCollisionBox(Box var1);

   void ip_setPositionWithoutTriggeringCallback(Vec3d var1);

   void ip_setWorld(World var1);
}
