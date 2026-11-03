package qouteall.imm_ptl.core.portal.util;

import net.minecraft.util.math.Vec3d;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.UnilateralPortalState;

public record PortalLocalXYNormalized(double nx, double ny) {
   public Vec3d getOffset(UnilateralPortalState state) {
      return state.orientation().rotate(new Vec3d((this.nx - 0.5) * state.width(), (this.ny - 0.5) * state.height(), 0.0));
   }

   public Vec3d getPos(UnilateralPortalState state) {
      return state.position().add(this.getOffset(state));
   }

   public Vec3d getOffset(Portal portal) {
      return portal.getAxisW().multiply((this.nx - 0.5) * portal.getWidth()).add(portal.getAxisH().multiply((this.ny - 0.5) * portal.getHeight()));
   }

   public Vec3d getPos(Portal portal) {
      return portal.getOriginPos().add(this.getOffset(portal));
   }

   public static PortalLocalXYNormalized fromOffset(UnilateralPortalState state, Vec3d offset) {
      Vec3d localPos = state.orientation().getConjugated().rotate(offset);
      return new PortalLocalXYNormalized(localPos.x / state.width() + 0.5, localPos.y / state.height() + 0.5);
   }

   public static PortalLocalXYNormalized fromPos(UnilateralPortalState state, Vec3d pos) {
      return fromOffset(state, pos.subtract(state.position()));
   }

   public static PortalLocalXYNormalized fromOffset(Portal portal, Vec3d offset) {
      return new PortalLocalXYNormalized(
         portal.getAxisW().dotProduct(offset) / portal.getWidth() + 0.5, portal.getAxisH().dotProduct(offset) / portal.getHeight() + 0.5
      );
   }

   public static PortalLocalXYNormalized fromPos(Portal portal, Vec3d pos) {
      return fromOffset(portal, pos.subtract(portal.getOriginPos()));
   }

   public PortalLocalXYNormalized clamp() {
      return new PortalLocalXYNormalized(Math.min(Math.max(this.nx, 0.0), 1.0), Math.min(Math.max(this.ny, 0.0), 1.0));
   }

   public PortalLocalXYNormalized snapToGrid(int gridCount) {
      return new PortalLocalXYNormalized((double)Math.round(this.nx * gridCount) / gridCount, (double)Math.round(this.ny * gridCount) / gridCount);
   }

   public boolean isValid() {
      return this.nx >= 0.0 && this.nx <= 1.0 && this.ny >= 0.0 && this.ny <= 1.0;
   }

   public boolean isCloseTo(PortalLocalXYNormalized another, double maxDistance) {
      double distSq = (this.nx - another.nx) * (this.nx - another.nx) + (this.ny - another.ny) * (this.ny - another.ny);
      return distSq < maxDistance * maxDistance;
   }

   public PortalLocalXYNormalized add(PortalLocalXYNormalized another) {
      return new PortalLocalXYNormalized(this.nx + another.nx, this.ny + another.ny);
   }

   public PortalLocalXYNormalized subtract(PortalLocalXYNormalized another) {
      return new PortalLocalXYNormalized(this.nx - another.nx, this.ny - another.ny);
   }
}
