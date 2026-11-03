package qouteall.q_misc_util.platform.event;

import java.util.function.Function;
import net.minecraft.util.Identifier;

final class EventFactoryImpl {
   private EventFactoryImpl() {
   }

   static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
      return new ArrayBackedEvent<>(type, invokerFactory);
   }

   static void ensureContainsDefault(Identifier[] defaultPhases) {
      for (Identifier id : defaultPhases) {
         if (id.equals(Event.DEFAULT_PHASE)) {
            return;
         }
      }

      throw new IllegalArgumentException("The event phases must contain Event.DEFAULT_PHASE.");
   }

   static void ensureNoDuplicates(Identifier[] defaultPhases) {
      for (int i = 0; i < defaultPhases.length; i++) {
         for (int j = i + 1; j < defaultPhases.length; j++) {
            if (defaultPhases[i].equals(defaultPhases[j])) {
               throw new IllegalArgumentException("Duplicate event phase: " + defaultPhases[i]);
            }
         }
      }
   }
}
