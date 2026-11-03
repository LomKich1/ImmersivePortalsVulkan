package qouteall.imm_ptl.core.portal.animation;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import net.minecraft.util.math.Vec3d;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.my_util.Vec2d;

public class NormalAnimation implements PortalAnimationDriver {
   public static final int INFINITE_THRESHOLD = 100000;
   public final List<NormalAnimation.Phase> phases;
   public final long startingGameTime;
   public final int loopCount;
   public final boolean isBuilding;
   private final long ticksPerRound;

   public static void init() {
      PortalAnimationDriver.registerDeserializer(McHelper.newResourceLocation("imm_ptl:normal"), NormalAnimation::deserialize);
   }

   public NormalAnimation(List<NormalAnimation.Phase> phases, long startingGameTime, int loopCount, boolean isBuilding) {
      this.phases = phases;
      this.startingGameTime = startingGameTime;
      this.loopCount = loopCount;
      this.isBuilding = isBuilding;
      long totalTicks = 0L;

      for (NormalAnimation.Phase phase : phases) {
         totalTicks += phase.durationTicks;
      }

      this.ticksPerRound = totalTicks;
   }

   private static NormalAnimation deserialize(NbtCompound compoundTag) {
      UnilateralPortalState initialState = UnilateralPortalState.fromTag(compoundTag.getCompoundOrEmpty("initialState"));
      List<NormalAnimation.Phase> phases = Helper.listTagToList(Helper.getCompoundList(compoundTag, "phases"), NormalAnimation.Phase::fromTag);
      long startingGameTime = compoundTag.getLong("startingGameTime", 0L);
      int loopCount = compoundTag.getInt("loopCount", 0);
      boolean isBuilding = compoundTag.getBoolean("isBuilding", false);
      if (isBuilding || !phases.isEmpty() && loopCount >= 0) {
         return new NormalAnimation(phases, startingGameTime, loopCount, isBuilding);
      } else {
         throw new RuntimeException("invalid NormalAnimation");
      }
   }

   @Override
   public NbtCompound toTag() {
      NbtCompound tag = new NbtCompound();
      tag.putString("type", "imm_ptl:normal");
      tag.put("phases", Helper.listToListTag(this.phases, NormalAnimation.Phase::toTag));
      tag.putLong("startingGameTime", this.startingGameTime);
      tag.putInt("loopCount", this.loopCount);
      tag.putBoolean("isBuilding", this.isBuilding);
      return tag;
   }

   private long getTotalDuration() {
      return this.loopCount >= 100000 ? Long.MAX_VALUE : this.ticksPerRound * this.loopCount;
   }

   @NotNull
   @Override
   public AnimationResult getAnimationResult(long tickTime, float partialTicks, AnimationContext context) {
      if (this.isBuilding) {
         return new AnimationResult(null, false);
      } else if (this.ticksPerRound != 0L && !this.phases.isEmpty()) {
         double passedTicks = (double)(tickTime - 1L - this.startingGameTime) + partialTicks;
         long totalDuration = this.getTotalDuration();
         boolean ends = false;
         if (passedTicks >= totalDuration) {
            passedTicks = totalDuration;
            ends = true;
         }

         if (passedTicks < 0.0) {
            return new AnimationResult(null, false);
         } else {
            double passedTicksInThisRound = ends ? this.ticksPerRound : passedTicks % this.ticksPerRound;
            long roundIndex = Math.floorDiv((long)passedTicks, this.ticksPerRound);
            long traversedTicks = 0L;
            DeltaUnilateralPortalState lastDelta = DeltaUnilateralPortalState.identity;

            for (NormalAnimation.Phase phase : this.phases) {
               if (phase.durationTicks != 0L && passedTicksInThisRound < traversedTicks + phase.durationTicks) {
                  double phaseProgress = (passedTicksInThisRound - traversedTicks) / phase.durationTicks;
                  phaseProgress = phase.timingFunction.mapProgress(phaseProgress);
                  DeltaUnilateralPortalState interpolated = DeltaUnilateralPortalState.interpolate(lastDelta, phase.delta, phaseProgress);
                  return new AnimationResult(interpolated, ends);
               }

               lastDelta = phase.delta();
               traversedTicks += phase.durationTicks;
            }

            return new AnimationResult(lastDelta, ends);
         }
      } else {
         Helper.err("No phase");
         return new AnimationResult(null, true);
      }
   }

   @Nullable
   @Override
   public DeltaUnilateralPortalState getEndingResult(long tickTime, AnimationContext context) {
      return this.phases.isEmpty() ? null : this.phases.get(this.phases.size() - 1).delta();
   }

   @Override
   public PortalAnimationDriver getFlippedVersion() {
      return new NormalAnimation(
         this.phases.stream().map(phase -> phase.getFlippedVersion()).collect(Collectors.toList()), this.startingGameTime, this.loopCount, this.isBuilding
      );
   }

   public static NormalAnimation createSizeAnimation(
      Portal portal, Vec2d startSizeScale, Vec2d toSizeScale, long startingGameTime, long durationTicks, TimingFunction timingFunction
   ) {
      NormalAnimation.Phase initialPhase = new NormalAnimation.Phase.Builder()
         .durationTicks(0L)
         .timingFunction(timingFunction)
         .delta(new DeltaUnilateralPortalState.Builder().scaleSize(startSizeScale).build())
         .build();
      NormalAnimation.Phase endingPhase = new NormalAnimation.Phase.Builder()
         .durationTicks(durationTicks)
         .timingFunction(timingFunction)
         .delta(new DeltaUnilateralPortalState.Builder().scaleSize(toSizeScale).build())
         .build();
      return new NormalAnimation.Builder().phases(List.of(initialPhase, endingPhase)).startingGameTime(startingGameTime).loopCount(1).build();
   }

   public static NormalAnimation createOscillationAnimation(Vec3d vec, int cycleTicks, long startingGameTime) {
      int partTickNum = cycleTicks / 4;
      return new NormalAnimation.Builder()
         .startingGameTime(startingGameTime)
         .infiniteLoop()
         .phases(
            List.of(
               new NormalAnimation.Phase.Builder()
                  .delta(new DeltaUnilateralPortalState.Builder().offset(vec).build())
                  .durationTicks(partTickNum)
                  .timingFunction(TimingFunction.sine)
                  .build(),
               new NormalAnimation.Phase.Builder()
                  .delta(DeltaUnilateralPortalState.identity)
                  .durationTicks(partTickNum)
                  .timingFunction(TimingFunction.sineFlipped)
                  .build(),
               new NormalAnimation.Phase.Builder()
                  .delta(new DeltaUnilateralPortalState.Builder().offset(vec.multiply(-1.0)).build())
                  .durationTicks(partTickNum)
                  .timingFunction(TimingFunction.sine)
                  .build(),
               new NormalAnimation.Phase.Builder()
                  .delta(DeltaUnilateralPortalState.identity)
                  .durationTicks(partTickNum)
                  .timingFunction(TimingFunction.sineFlipped)
                  .build()
            )
         )
         .build();
   }

   @Override
   public Text getInfo() {
      MutableText component = Text.literal("Normal[\n");

      for (NormalAnimation.Phase phase : this.phases) {
         component.append(" ");
         component.append(phase.getInfo());
         component.append("\n");
      }

      component.append("] %s times".formatted(this.loopCount >= 100000 ? "∞" : this.loopCount));
      return component;
   }

   public static class Builder {
      private List<NormalAnimation.Phase> phases = new ArrayList<>();
      private long startingGameTime;
      private int loopCount;
      private boolean isBuilding = false;

      public NormalAnimation.Builder from(NormalAnimation animation) {
         this.phases = animation.phases;
         this.startingGameTime = animation.startingGameTime;
         this.loopCount = animation.loopCount;
         return this;
      }

      public NormalAnimation.Builder phases(List<NormalAnimation.Phase> phases) {
         this.phases = phases;
         return this;
      }

      public NormalAnimation.Builder startingGameTime(long startingGameTime) {
         this.startingGameTime = startingGameTime;
         return this;
      }

      public NormalAnimation.Builder loopCount(int loopCount) {
         this.loopCount = loopCount;
         return this;
      }

      public NormalAnimation.Builder infiniteLoop() {
         this.loopCount = 100000;
         return this;
      }

      public NormalAnimation.Builder isBuilding(boolean isBuilding) {
         this.isBuilding = isBuilding;
         return this;
      }

      public NormalAnimation build() {
         return new NormalAnimation(this.phases, this.startingGameTime, this.loopCount, this.isBuilding);
      }
   }

   public record Phase(long durationTicks, DeltaUnilateralPortalState delta, TimingFunction timingFunction) {
      public static NormalAnimation.Phase fromTag(NbtCompound tag) {
         long durationTicks = tag.getLong("durationTicks", 0L);
         DeltaUnilateralPortalState delta = DeltaUnilateralPortalState.fromTag(tag.getCompoundOrEmpty("delta"));
         TimingFunction timingFunction = TimingFunction.fromString(tag.getString("timingFunction", ""));
         return new NormalAnimation.Phase(durationTicks, delta, timingFunction);
      }

      public NbtCompound toTag() {
         NbtCompound tag = new NbtCompound();
         tag.putLong("durationTicks", this.durationTicks);
         tag.put("delta", this.delta.toTag());
         tag.putString("timingFunction", this.timingFunction.name());
         return tag;
      }

      public NormalAnimation.Phase getFlippedVersion() {
         return new NormalAnimation.Phase(this.durationTicks, this.delta.getFlipped(), this.timingFunction);
      }

      public Text getInfo() {
         return Text.literal("Phase(%d,".formatted(this.durationTicks)).append(this.delta.toString()).append(")");
      }

      public static class Builder {
         private long durationTicks = 0L;
         private DeltaUnilateralPortalState delta = DeltaUnilateralPortalState.identity;
         private TimingFunction timingFunction = TimingFunction.linear;

         public NormalAnimation.Phase.Builder durationTicks(long durationTicks) {
            this.durationTicks = durationTicks;
            return this;
         }

         public NormalAnimation.Phase.Builder delta(DeltaUnilateralPortalState delta) {
            this.delta = delta;
            return this;
         }

         public NormalAnimation.Phase.Builder timingFunction(TimingFunction timingFunction) {
            this.timingFunction = timingFunction;
            return this;
         }

         public NormalAnimation.Phase build() {
            return new NormalAnimation.Phase(this.durationTicks, this.delta, this.timingFunction);
         }
      }
   }
}
