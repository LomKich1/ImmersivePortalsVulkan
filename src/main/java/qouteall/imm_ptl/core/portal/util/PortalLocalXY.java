package qouteall.imm_ptl.core.portal.util;

import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;

public record PortalLocalXY(double localX, double localY) {
   public static PortalLocalXY fromPos(Portal portal, Vec3d pos) {
      Vec3d delta = pos.subtract(portal.getOriginPos());
      return fromOffset(portal, delta);
   }

   public static PortalLocalXY fromPos(UnilateralPortalState state, Vec3d pos) {
      Vec3d delta = pos.subtract(state.position());
      return fromOffset(state, delta);
   }

   public static PortalLocalXY fromOffset(Portal portal, Vec3d offset) {
      return new PortalLocalXY(portal.getAxisW().dotProduct(offset), portal.getAxisH().dotProduct(offset));
   }

   public static PortalLocalXY fromOffset(UnilateralPortalState state, Vec3d offset) {
      Vec3d result = state.orientation().getConjugated().rotate(offset);
      return new PortalLocalXY(result.x, result.y);
   }

   public Vec3d getOffset(Portal portal) {
      return portal.getAxisW().multiply(this.localX).add(portal.getAxisH().multiply(this.localY));
   }

   public Vec3d getOffset(UnilateralPortalState state) {
      return state.orientation().rotate(new Vec3d(this.localX, this.localY, 0.0));
   }

   public Vec3d getPos(Portal portal) {
      return portal.getOriginPos().add(this.getOffset(portal));
   }

   public Vec3d getPos(UnilateralPortalState state) {
      return state.position().add(this.getOffset(state));
   }
}
