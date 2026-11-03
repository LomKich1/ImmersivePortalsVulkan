package qouteall.imm_ptl.core.portal.animation;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.Vec3d;
import org.apache.commons.lang3.NotImplementedException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.Helper;

@Deprecated
public record OscillationAnimation(Vec3d vec, double frequency, long startGameTime, long cycleCount) implements PortalAnimationDriver {
   private static final Logger LOGGER = LogUtils.getLogger();

   public static void init() {
      PortalAnimationDriver.registerDeserializer(McHelper.newResourceLocation("imm_ptl:oscillation"), OscillationAnimation::fromTag);
   }

   public static OscillationAnimation fromTag(NbtCompound tag) {
      Vec3d vec = Helper.vec3FromListTag(tag.get("vec"));
      if (vec == null) {
         LOGGER.error("Invalid oscillation animation {}", tag);
         return null;
      } else {
         double frequency = tag.getDouble("frequency", 0.0);
         long startGameTime = tag.getLong("startGameTime", 0L);
         long cycleCount = tag.getLong("cycleCount", 0L);
         return new OscillationAnimation(vec, frequency, startGameTime, cycleCount);
      }
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("type", "imm_ptl:oscillation");
      tag.put("vec", Helper.vec3ToListTag(this.vec));
      tag.putDouble("frequency", this.frequency);
      tag.putLong("startGameTime", this.startGameTime);
      tag.putLong("cycleCount", this.cycleCount);
      return tag;
   }

   @NotNull
   @Override
   public AnimationResult getAnimationResult(long tickTime, float partialTicks, AnimationContext context) {
      double deltaTicks = (double)(tickTime - this.startGameTime) + partialTicks;
      double deltaSeconds = deltaTicks / 20.0;
      double deltaCycles = deltaSeconds * this.frequency;
      if (deltaCycles > this.cycleCount) {
         return new AnimationResult(null, true);
      } else {
         double v = Math.sin(deltaCycles * Math.PI * 2.0);
         throw new NotImplementedException();
      }
   }

   @Override
   public DeltaUnilateralPortalState getEndingResult(long tickTime, AnimationContext context) {
      return DeltaUnilateralPortalState.identity;
   }
}
