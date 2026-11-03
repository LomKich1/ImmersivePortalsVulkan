package qouteall.imm_ptl.core.teleportation;

import java.util.Comparator;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.math.random.Random;
import net.minecraft.client.world.ClientWorld;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.render.context_management.RenderStates;

@Environment(EnvType.CLIENT)
public class CrossPortalSound {
   public static final float VOLUME_RADIUS_MULT = 16.0F;
   public static final float MIN_SOUND_RADIUS = 16.0F;

   public static boolean isPlayerWorld(ClientWorld world) {
      return world.getRegistryKey() == RenderStates.originalPlayerDimension;
   }

   @Nullable
   public static PositionedSoundInstance createCrossPortalSound(
      ClientWorld soundWorld, SoundEvent soundEvent, SoundCategory soundSource, Vec3d soundPos, float soundVol, float soundPitch, long seed
   ) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.player == null) {
         return null;
      } else {
         Profilers.get().push("cross_portal_sound");
         double soundRadius = Math.min(64.0F, Math.max(16.0F * soundVol, 16.0F));
         Vec3d playerCameraPos = RenderStates.originalPlayerPos.add(McHelper.getEyeOffset(client.player));
         PositionedSoundInstance result = IPMcHelper.getNearbyPortalList(
               soundWorld,
               soundPos,
               IPGlobal.maxNormalPortalRadius,
               portal -> portal.getDestDim() == RenderStates.originalPlayerDimension && isPlayerInRange(portal, soundPos, soundRadius, playerCameraPos)
            )
            .stream()
            .min(Comparator.comparingDouble(portal -> getPortalDistance(portal, soundPos)))
            .map(
               portal -> {
                  Vec3d soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
                  Vec3d soundExitPortalPoint = portal.transformPoint(soundEnterPortalPoint);
                  float volumeToEnterPortal = (float)soundEnterPortalPoint.distanceTo(soundPos) / 16.0F;
                  float volumeMultiplier = Math.max(0.0F, 1.0F - (float)(playerCameraPos.distanceTo(soundExitPortalPoint) / 16.0));
                  float volumeAtPortal = Math.max(0.0F, soundVol - volumeToEnterPortal) * volumeMultiplier;
                  return new PositionedSoundInstance(
                     soundEvent,
                     soundSource,
                     volumeAtPortal,
                     soundPitch,
                     Random.create(seed),
                     soundExitPortalPoint.getX(),
                     soundExitPortalPoint.getY(),
                     soundExitPortalPoint.getZ()
                  );
               }
            )
            .orElse(null);
         Profilers.get().pop();
         return result;
      }
   }

   private static boolean isPlayerInRange(Portal portal, Vec3d soundPos, double soundRadius, Vec3d playerCameraPos) {
      Vec3d soundExitPoint = portal.transformPoint(portal.getNearestPointInPortal(soundPos));
      return soundExitPoint.isInRange(playerCameraPos, soundRadius);
   }

   private static double getPortalDistance(Portal portal, Vec3d soundPos) {
      Vec3d soundEnterPortalPoint = portal.getNearestPointInPortal(soundPos);
      return soundEnterPortalPoint.squaredDistanceTo(soundPos);
   }
}
