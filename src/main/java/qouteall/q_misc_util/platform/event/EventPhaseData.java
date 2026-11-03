package qouteall.q_misc_util.platform.event;

import java.lang.reflect.Array;
import java.util.Arrays;
import net.minecraft.util.Identifier;

class EventPhaseData<T> extends SortableNode<EventPhaseData<T>> {
   final Identifier id;
   T[] listeners;

   EventPhaseData(Identifier id, Class<?> listenerClass) {
      this.id = id;
      this.listeners = (T[])((Object[])Array.newInstance(listenerClass, 0));
   }

   void addListener(T listener) {
      int oldLength = this.listeners.length;
      this.listeners = (T[])Arrays.copyOf(this.listeners, oldLength + 1);
      this.listeners[oldLength] = listener;
   }

   @Override
   protected String getDescription() {
      return this.id.toString();
   }
}
