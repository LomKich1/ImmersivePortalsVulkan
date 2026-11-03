package qouteall.imm_ptl.core.portal;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix3d;
import org.joml.Matrix4d;
import org.joml.Quaterniond;
import qouteall.q_misc_util.my_util.DQuaternion;

public class Mirror extends Portal {
   public static final EntityType<Mirror> ENTITY_TYPE = Portal.createPortalEntityType(Mirror::new, Portal.portalEntityTypeKey("mirror"));

   public Mirror(EntityType<?> entityType, World world) {
      super(entityType, world);
   }

   @Override
   public void tick() {
      super.tick();
      this.setTeleportable(false);
      this.setInteractable(false);
   }

   @Override
   public Vec3d transformLocalVecNonScale(Vec3d localVec) {
      return this.getMirrored(super.transformLocalVecNonScale(localVec));
   }

   @Override
   public boolean canTeleportEntity(Entity entity) {
      return false;
   }

   public Vec3d getMirrored(Vec3d vec) {
      Vec3d normal = this.getNormal();
      return mirroredVec(vec, normal);
   }

   public static Vec3d mirroredVec(Vec3d vec, Vec3d normal) {
      double len = vec.dotProduct(normal);
      return vec.add(normal.multiply(len * -2.0));
   }

   @Override
   public Vec3d inverseTransformLocalVecNonScale(Vec3d localVec) {
      return super.inverseTransformLocalVecNonScale(this.getMirrored(localVec));
   }

   @Override
   public Matrix4d getFullSpaceTransformation() {
      Vec3d originPos = this.getOriginPos();
      Vec3d destPos = this.getDestPos();
      DQuaternion rot = this.getRotationD();
      return new Matrix4d()
         .translation(destPos.x, destPos.y, destPos.z)
         .reflect(this.getNormal().x, this.getNormal().y, this.getNormal().z, 0.0)
         .scale(this.getScale())
         .rotate(rot.toMcQuaternion())
         .translate(-originPos.x, -originPos.y, -originPos.z);
   }

   public void setRotationTransformationForMirror(DQuaternion visualRotation) {
      Matrix3d mirrorTrans = new Matrix3d().reflect(this.getNormal().x, this.getNormal().y, this.getNormal().z);
      Matrix3d visualRotationTrans = new Matrix3d().rotate(visualRotation.toMcQuaternion());
      Matrix3d newRotation = new Matrix3d().mul(visualRotationTrans).mul(mirrorTrans);
      Quaterniond mirrorRotation = new Quaterniond().setFromNormalized(newRotation);
      this.setRotation(DQuaternion.fromMcQuaternion(mirrorRotation));
   }
}
