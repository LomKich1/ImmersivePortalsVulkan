package qouteall.imm_ptl.core.portal.animation;

import org.jetbrains.annotations.NotNull;

public enum TimingFunction {
   linear,
   sine,
   sineFlipped,
   circle,
   easeInOutCubic;

   @NotNull
   static TimingFunction fromString(String c) {
      return switch (c) {
         case "linear" -> linear;
         case "sine" -> sine;
         case "sineFlipped" -> sineFlipped;
         case "circle" -> circle;
         case "easeInOutCubic" -> easeInOutCubic;
         default -> sine;
      };
   }

   public double mapProgress(double progress) {
      switch (this) {
         case linear:
            return progress;
         case sine:
            return Math.sin(progress * (Math.PI / 2));
         case sineFlipped:
            return 1.0 - Math.sin((1.0 - progress) * (Math.PI / 2));
         case circle:
            return Math.sqrt(1.0 - (1.0 - progress) * (1.0 - progress));
         case easeInOutCubic:
            return -2.0 * (progress * progress * progress) + 3.0 * (progress * progress);
         default:
            throw new RuntimeException();
      }
   }
}
