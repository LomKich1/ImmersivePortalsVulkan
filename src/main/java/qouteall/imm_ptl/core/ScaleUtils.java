package qouteall.imm_ptl.core;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.attribute.EntityAttributeModifier.Operation;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.ducks.IECamera;
import qouteall.imm_ptl.core.portal.Portal;

public class ScaleUtils {
   public static final Identifier IPORTAL_SCALING = Identifier.of("iportal", "scaling");

   @Environment(EnvType.CLIENT)
   public static void onClientPlayerTeleported(Portal portal) {
      if (portal.hasScaling() && portal.isTeleportChangesScale()) {
         MinecraftClient client = MinecraftClient.getInstance();
         ClientPlayerEntity player = client.player;
         Validate.notNull(player, "Player is null", new Object[0]);
         doScalingForEntity(player, portal);
         IECamera camera = (IECamera)(Object)client.gameRenderer.getCamera();
         camera.ip_setCameraY((float)(camera.ip_getCameraY() * portal.getScaling()), (float)(camera.ip_getLastCameraY() * portal.getScaling()));
      }
   }

   public static void onServerEntityTeleported(Entity entity, Portal portal) {
      if (portal.hasScaling() && portal.isTeleportChangesScale()) {
         doScalingForEntity(entity, portal);
         if (entity.getVehicle() != null) {
            doScalingForEntity(entity.getVehicle(), portal);
         }
      }
   }

   @Nullable
   public static EntityAttributeInstance getScaleAttr(Entity entity) {
      return entity instanceof LivingEntity livingEntity ? livingEntity.getAttributes().getCustomInstance(EntityAttributes.SCALE) : null;
   }

   public static double getScale(Entity entity) {
      return entity instanceof LivingEntity livingEntity ? livingEntity.getScale() : 1.0;
   }

   public static double getBaseScale(Entity entity) {
      EntityAttributeInstance scaleAttr = getScaleAttr(entity);
      return scaleAttr != null ? scaleAttr.getBaseValue() : 1.0;
   }

   public static double getIPortalScaling(Entity entity) {
      EntityAttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr == null) {
         return 1.0;
      } else {
         EntityAttributeModifier modifier = scaleAttr.getModifier(IPORTAL_SCALING);
         return modifier == null ? 1.0 : modifier.value() + 1.0;
      }
   }

   public static void setIPortalScaling(Entity entity, double newScale) {
      EntityAttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr != null) {
         if (Math.abs(newScale - 1.0) < 1.0E-4) {
            scaleAttr.removeModifier(IPORTAL_SCALING);
            entity.calculateDimensions();
         } else {
            scaleAttr.overwritePersistentModifier(new EntityAttributeModifier(IPORTAL_SCALING, newScale - 1.0, Operation.ADD_MULTIPLIED_TOTAL));
            entity.calculateDimensions();
         }
      }
   }

   public static void setBaseScale(Entity entity, double scale) {
      EntityAttributeInstance scaleAttr = getScaleAttr(entity);
      if (scaleAttr != null) {
         scaleAttr.setBaseValue(scale);
         entity.calculateDimensions();
      }
   }

   public static double computeThirdPersonScale(Entity entity) {
      return getScale(entity);
   }

   public static double computeBlockReachScale(Entity entity) {
      return getScale(entity);
   }

   public static double computeMotionScale(Entity entity) {
      return getScale(entity);
   }

   private static void doScalingForEntity(Entity entity, Portal portal) {
      Vec3d eyePos = McHelper.getEyePos(entity);
      Vec3d lastTickEyePos = McHelper.getLastTickEyePos(entity);
      double oldScale = getIPortalScaling(entity);
      double newScale = transformScale(portal, oldScale);
      if (!entity.getEntityWorld().isClient() && isScaleIllegal(newScale)) {
         newScale = 1.0;
         if (entity instanceof ServerPlayerEntity serverPlayer) {
            serverPlayer.sendMessage(Text.literal("Scale out of range"));
         }
      }

      setIPortalScaling(entity, newScale);
      if (!entity.getEntityWorld().isClient()) {
         McHelper.setEyePos(entity, eyePos, lastTickEyePos);
         McHelper.updateBoundingBox(entity);
      } else {
         McHelper.setEyePos(entity, eyePos, lastTickEyePos);
         McHelper.updateBoundingBox(entity);
      }
   }

   private static double transformScale(Portal portal, double oldScale) {
      double result = oldScale * portal.getScaling();
      if (Math.abs(result - 1.0) < 1.0E-4) {
         result = 1.0;
      }

      return result;
   }

   private static boolean isScaleIllegal(double scale) {
      return scale > IPGlobal.scaleLimit || scale < 1.0 / (IPGlobal.scaleLimit * 2);
   }
}
