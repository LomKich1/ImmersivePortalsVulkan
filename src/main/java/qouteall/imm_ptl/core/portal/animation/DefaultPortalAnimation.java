package qouteall.imm_ptl.core.portal.animation;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.PortalState;
import qouteall.q_misc_util.Helper;

public class DefaultPortalAnimation {
   public TimingFunction timingFunction;
   public int durationTicks;
   public boolean inverseScale;
   public long disableUntil = 0L;

   public DefaultPortalAnimation(TimingFunction timingFunction, int durationTicks, boolean inverseScale, long disableUntil) {
      this.timingFunction = timingFunction;
      this.durationTicks = durationTicks;
      this.inverseScale = inverseScale;
      this.disableUntil = disableUntil;
   }

   public DefaultPortalAnimation(TimingFunction timingFunction, int durationTicks, boolean inverseScale) {
      this(timingFunction, durationTicks, inverseScale, 0L);
   }

   public static DefaultPortalAnimation createDefault() {
      return new DefaultPortalAnimation(TimingFunction.sine, 10, false);
   }

   public static DefaultPortalAnimation fromNbt(NbtCompound nbt) {
      String c = nbt.getString("curve", "");
      TimingFunction timingFunction = TimingFunction.fromString(c);
      int durationTicks = nbt.getInt("durationTicks", 0);
      boolean inverseScale = nbt.getBoolean("inverseScale", false);
      long disableUntil = nbt.getLong("disableUntil", 0L);
      return new DefaultPortalAnimation(timingFunction, durationTicks, inverseScale, disableUntil);
   }

   public void setDisableUntil(long disableUntil) {
      this.disableUntil = disableUntil;
   }

   @Environment(EnvType.CLIENT)
   public void startClientDefaultAnimation(Portal portal, PortalState animationStartState) {
      PortalState newState = portal.getPortalState();
      if (newState == null) {
         Helper.err("portal animation state abnormal");
      } else if (newState.fromWorld == animationStartState.fromWorld && newState.toWorld == animationStartState.toWorld) {
         if (this.disableUntil < portal.getEntityWorld().getTime()) {
            ClientPortalAnimationManagement.addDefaultAnimation(portal, animationStartState, newState, this);
            portal.setPortalState(animationStartState);
         }
      }
   }

   public NbtCompound toNbt() {
      NbtCompound nbtCompound = new NbtCompound();
      nbtCompound.putString("curve", this.timingFunction.toString());
      nbtCompound.putInt("durationTicks", this.durationTicks);
      nbtCompound.putBoolean("inverseScale", this.inverseScale);
      nbtCompound.putLong("disableUntil", this.disableUntil);
      return nbtCompound;
   }

   public DefaultPortalAnimation copy() {
      return new DefaultPortalAnimation(this.timingFunction, this.durationTicks, this.inverseScale);
   }
}
