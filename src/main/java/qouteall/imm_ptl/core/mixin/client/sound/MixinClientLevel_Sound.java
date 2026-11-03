package qouteall.imm_ptl.core.mixin.client.sound;

import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.util.math.Vec3d;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundCategory;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.teleportation.CrossPortalSound;

@Mixin({ClientWorld.class})
public class MixinClientLevel_Sound {
   @Shadow
   @Final
   private MinecraftClient client;

   @Inject(
      method = {"playSound"},
      at = {@At("HEAD")},
      cancellable = true
   )
   @IPVanillaCopy
   private void onPlaySound(
      double x, double y, double z, SoundEvent soundEvent, SoundCategory soundSource, float volume, float pitch, boolean distanceDelay, long seed, CallbackInfo ci
   ) {
      if (IPGlobal.enableCrossPortalSound) {
         ClientWorld this_ = (ClientWorld)this;
         Vec3d soundPos = new Vec3d(x, y, z);
         if (!this.portal_isPosNearPlayer(soundPos)) {
            PositionedSoundInstance crossPortalSound = CrossPortalSound.createCrossPortalSound(this_, soundEvent, soundSource, soundPos, volume, pitch, seed);
            if (crossPortalSound != null) {
               this.portal_playSound(crossPortalSound, distanceDelay);
               ci.cancel();
            } else if (!CrossPortalSound.isPlayerWorld(this_)) {
               ci.cancel();
            }
         }
      }
   }

   private void portal_playSound(PositionedSoundInstance sound, boolean distanceDelay) {
      double d = this.client.gameRenderer.getCamera().getCameraPos().squaredDistanceTo(sound.getX(), sound.getY(), sound.getZ());
      if (distanceDelay && d > 100.0) {
         double e = Math.sqrt(d) / 40.0;
         this.client.getSoundManager().play(sound, (int)(e * 20.0));
      } else {
         this.client.getSoundManager().play(sound);
      }
   }

   private boolean portal_isPosNearPlayer(Vec3d pos) {
      ClientWorld this_ = (ClientWorld)this;
      ClientPlayerEntity player = this.client.player;
      if (player == null) {
         return false;
      } else {
         return this_ != player.getEntityWorld() ? false : pos.squaredDistanceTo(player.getEntityPos()) < 4096.0;
      }
   }
}
