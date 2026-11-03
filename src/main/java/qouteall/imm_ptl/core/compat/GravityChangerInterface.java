package qouteall.imm_ptl.core.compat;

import gravity_changer.api.GravityChangerAPI;
import gravity_changer.util.RotationUtil;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.client.MinecraftClient;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.CHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.DQuaternion;

public class GravityChangerInterface {
   public static GravityChangerInterface.Invoker invoker = new GravityChangerInterface.Invoker();
   private static boolean warned = false;

   @Environment(EnvType.CLIENT)
   private static void warnGravityChangerNotPresent() {
      if (!warned) {
         warned = true;
         CHelper.printChat(
            Text.translatable("imm_ptl.missing_gravity_changer").append(McHelper.getLinkText("https://modrinth.com/mod/gravity-api-fork"))
         );
      }
   }

   public static class Invoker {
      public boolean isGravityChangerPresent() {
         return false;
      }

      public Vec3d getEyeOffset(Entity entity) {
         return new Vec3d(0.0, entity.getStandingEyeHeight(), 0.0);
      }

      public Direction getGravityDirection(Entity entity) {
         return Direction.field_11033;
      }

      public Direction getBaseGravityDirection(Entity entity) {
         return Direction.field_11033;
      }

      public void setClientPlayerGravityDirection(PlayerEntity player, Direction direction) {
         GravityChangerInterface.warnGravityChangerNotPresent();
      }

      public void setBaseGravityDirectionServer(Entity entity, Direction direction) {
      }

      @Nullable
      public DQuaternion getExtraCameraRotation(Direction gravityDirection) {
         return null;
      }

      public Vec3d getWorldVelocity(Entity entity) {
         return entity.getVelocity();
      }

      public void setWorldVelocity(Entity entity, Vec3d newVelocity) {
         entity.setVelocity(newVelocity);
      }

      public Vec3d transformPlayerToWorld(Direction gravity, Vec3d vec3d) {
         return vec3d;
      }

      public Vec3d transformWorldToPlayer(Direction gravity, Vec3d vec3d) {
         return vec3d;
      }

      public Direction transformDirPlayerToWorld(Direction gravity, Direction direction) {
         return direction;
      }

      public Direction transformDirWorldToPlayer(Direction gravity, Direction direction) {
         return direction;
      }
   }

   public static class OnGravityChangerPresent extends GravityChangerInterface.Invoker {
      @Override
      public boolean isGravityChangerPresent() {
         return true;
      }

      @Override
      public Vec3d getEyeOffset(Entity entity) {
         return GravityChangerAPI.getEyeOffset(entity);
      }

      @Override
      public Direction getGravityDirection(Entity entity) {
         return GravityChangerAPI.getGravityDirection(entity);
      }

      @Override
      public Direction getBaseGravityDirection(Entity entity) {
         return GravityChangerAPI.getBaseGravityDirection(entity);
      }

      @Override
      public void setBaseGravityDirectionServer(Entity entity, Direction direction) {
         GravityChangerAPI.setBaseGravityDirection(entity, direction);
      }

      @Override
      public void setClientPlayerGravityDirection(PlayerEntity player, Direction direction) {
         this.setClientPlayerGravityDirectionClientOnly(player, direction);
      }

      @Environment(EnvType.CLIENT)
      private void setClientPlayerGravityDirectionClientOnly(PlayerEntity player, Direction direction) {
         Validate.isTrue(MinecraftClient.getInstance().isOnThread());
         GravityChangerAPI.instantlySetClientBaseGravityDirection(player, direction);
      }

      @Nullable
      @Override
      public DQuaternion getExtraCameraRotation(Direction gravityDirection) {
         return gravityDirection == Direction.field_11033 ? null : DQuaternion.fromMcQuaternion(RotationUtil.getWorldRotationQuaternion(gravityDirection));
      }

      @Override
      public Vec3d getWorldVelocity(Entity entity) {
         return GravityChangerAPI.getWorldVelocity(entity);
      }

      @Override
      public void setWorldVelocity(Entity entity, Vec3d newVelocity) {
         GravityChangerAPI.setWorldVelocity(entity, newVelocity);
      }

      @Override
      public Vec3d transformPlayerToWorld(Direction gravity, Vec3d vec3d) {
         return RotationUtil.vecPlayerToWorld(vec3d, gravity);
      }

      @Override
      public Vec3d transformWorldToPlayer(Direction gravity, Vec3d vec3d) {
         return RotationUtil.vecWorldToPlayer(vec3d, gravity);
      }

      @Override
      public Direction transformDirPlayerToWorld(Direction gravity, Direction direction) {
         return RotationUtil.dirPlayerToWorld(direction, gravity);
      }

      @Override
      public Direction transformDirWorldToPlayer(Direction gravity, Direction direction) {
         return RotationUtil.dirWorldToPlayer(direction, gravity);
      }
   }
}
