package qouteall.imm_ptl.core.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
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
         CHelper.printChat(Text.translatable("imm_ptl.missing_gravity_changer").append(McHelper.getLinkText("https://modrinth.com/mod/gravity-api-fork")));
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
         return Direction.DOWN;
      }

      public Direction getBaseGravityDirection(Entity entity) {
         return Direction.DOWN;
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

}
