package qouteall.q_misc_util.platform.event;

import net.minecraft.util.Identifier;

public abstract class Event<T> {
   protected volatile T invoker;
   public static final Identifier DEFAULT_PHASE = Identifier.of("fabric", "default");

   public final T invoker() {
      return this.invoker;
   }

   public abstract void register(T var1);

   public void register(Identifier phase, T listener) {
      this.register(listener);
   }

   public void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase) {
   }
}
