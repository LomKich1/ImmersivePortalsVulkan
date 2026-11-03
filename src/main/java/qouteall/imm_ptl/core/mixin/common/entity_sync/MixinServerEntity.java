package qouteall.imm_ptl.core.mixin.common.entity_sync;

import net.minecraft.entity.Entity;
import net.minecraft.entity.TrackedPosition;
import net.minecraft.network.packet.Packet;
import net.minecraft.server.network.EntityTrackerEntry;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEEntityTrackerEntry;
import qouteall.imm_ptl.core.network.PacketRedirection;
import qouteall.imm_ptl.core.portal.Portal;

@Mixin(
   value = {EntityTrackerEntry.class},
   priority = 1200
)
public abstract class MixinServerEntity implements IEEntityTrackerEntry {
   @Shadow
   @Final
   private Entity entity;
   @Shadow
   @Final
   private TrackedPosition trackedPos;
   @Shadow
   @Final
   private static Logger LOGGER;

   @Inject(
      method = {"Lnet/minecraft/server/network/EntityTrackerEntry;tick()V"},
      at = {@At("HEAD")}
   )
   private void onTick(CallbackInfo ci) {
      PacketRedirection.validateForceRedirecting();
   }

   @Redirect(
      method = {"stopTracking"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
      )}
   )
   private void onSendRemoveEntityPacket(ServerPlayNetworkHandler networkHandler, Packet packet) {
      if (IPGlobal.entityUntrackDebug) {
         LOGGER.info("Entity remove packet sent {} {}", this.entity.getEntityWorld().getTime(), this.entity);
      }

      PacketRedirection.sendRedirectedPacket(networkHandler, packet, this.entity.getEntityWorld().getRegistryKey());
   }

   @Redirect(
      method = {"startTracking"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/network/ServerPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
      )}
   )
   private void onSendAddEntityPacket(ServerPlayNetworkHandler networkHandler, Packet packet) {
      if (IPGlobal.entityTrackDebug) {
         LOGGER.info("Entity add packet sent {} {}", this.entity.getEntityWorld().getTime(), this.entity);
      }

      PacketRedirection.sendRedirectedPacket(networkHandler, packet, this.entity.getEntityWorld().getRegistryKey());
   }

   @Inject(
      method = {"tick"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onSendChanges(CallbackInfo ci) {
      if (this.entity instanceof Portal) {
         ci.cancel();
      }
   }

   @Override
   public void ip_updateTrackedEntityPosition() {
      this.trackedPos.setPos(this.entity.getSyncedPos());
   }
}
