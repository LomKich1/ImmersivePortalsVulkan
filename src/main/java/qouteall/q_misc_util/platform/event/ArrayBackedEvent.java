package qouteall.q_misc_util.platform.event;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import net.minecraft.util.Identifier;

class ArrayBackedEvent<T> extends Event<T> {
   private final Function<T[], T> invokerFactory;
   private final Object lock = new Object();
   private T[] handlers;
   private final Map<Identifier, EventPhaseData<T>> phases = new LinkedHashMap<>();
   private final List<EventPhaseData<T>> sortedPhases = new ArrayList<>();

   ArrayBackedEvent(Class<? super T> type, Function<T[], T> invokerFactory) {
      this.invokerFactory = invokerFactory;
      this.handlers = (T[])((Object[])Array.newInstance(type, 0));
      this.update();
   }

   void update() {
      this.invoker = this.invokerFactory.apply(this.handlers);
   }

   @Override
   public void register(T listener) {
      this.register(DEFAULT_PHASE, listener);
   }

   @Override
   public void register(Identifier phaseIdentifier, T listener) {
      Objects.requireNonNull(phaseIdentifier, "Tried to register a listener for a null phase!");
      Objects.requireNonNull(listener, "Tried to register a null listener!");
      synchronized (this.lock) {
         this.getOrCreatePhase(phaseIdentifier, true).addListener(listener);
         this.rebuildInvoker(this.handlers.length + 1);
      }
   }

   private EventPhaseData<T> getOrCreatePhase(Identifier id, boolean sortIfCreate) {
      EventPhaseData<T> phase = this.phases.get(id);
      if (phase == null) {
         phase = new EventPhaseData<>(id, this.handlers.getClass().getComponentType());
         this.phases.put(id, phase);
         this.sortedPhases.add(phase);
         if (sortIfCreate) {
            NodeSorting.sort(this.sortedPhases, "event phases", Comparator.comparing(data -> data.id));
         }
      }

      return phase;
   }

   private void rebuildInvoker(int newLength) {
      if (this.sortedPhases.size() == 1) {
         this.handlers = this.sortedPhases.get(0).listeners;
      } else {
         T[] newHandlers = (T[])((Object[])Array.newInstance(this.handlers.getClass().getComponentType(), newLength));
         int newHandlersIndex = 0;

         for (EventPhaseData<T> existingPhase : this.sortedPhases) {
            int length = existingPhase.listeners.length;
            System.arraycopy(existingPhase.listeners, 0, newHandlers, newHandlersIndex, length);
            newHandlersIndex += length;
         }

         this.handlers = newHandlers;
      }

      this.update();
   }

   @Override
   public void addPhaseOrdering(Identifier firstPhase, Identifier secondPhase) {
      Objects.requireNonNull(firstPhase, "Tried to add an ordering for a null phase.");
      Objects.requireNonNull(secondPhase, "Tried to add an ordering for a null phase.");
      if (firstPhase.equals(secondPhase)) {
         throw new IllegalArgumentException("Tried to add a phase that depends on itself.");
      } else {
         synchronized (this.lock) {
            EventPhaseData<T> first = this.getOrCreatePhase(firstPhase, false);
            EventPhaseData<T> second = this.getOrCreatePhase(secondPhase, false);
            EventPhaseData.link(first, second);
            NodeSorting.sort(this.sortedPhases, "event phases", Comparator.comparing(data -> data.id));
            this.rebuildInvoker(this.handlers.length);
         }
      }
   }
}
