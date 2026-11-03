package qouteall.q_misc_util.platform.event;

import java.util.function.Function;
import net.minecraft.util.Identifier;

public final class EventFactory {
   private EventFactory() {
   }

   public static <T> Event<T> createArrayBacked(Class<? super T> type, Function<T[], T> invokerFactory) {
      return EventFactoryImpl.createArrayBacked(type, invokerFactory);
   }

   public static <T> Event<T> createArrayBacked(Class<T> type, T emptyInvoker, Function<T[], T> invokerFactory) {
      return createArrayBacked(type, listeners -> {
         if (listeners.length == 0) {
            return emptyInvoker;
         } else {
            return listeners.length == 1 ? listeners[0] : invokerFactory.apply((T)listeners);
         }
      });
   }

   public static <T> Event<T> createWithPhases(Class<? super T> type, Function<T[], T> invokerFactory, Identifier... defaultPhases) {
      EventFactoryImpl.ensureContainsDefault(defaultPhases);
      EventFactoryImpl.ensureNoDuplicates(defaultPhases);
      Event<T> event = createArrayBacked(type, invokerFactory);

      for (int i = 1; i < defaultPhases.length; i++) {
         event.addPhaseOrdering(defaultPhases[i - 1], defaultPhases[i]);
      }

      return event;
   }

   @Deprecated
   public static String getHandlerName(Object handler) {
      return handler.getClass().getName();
   }
}
