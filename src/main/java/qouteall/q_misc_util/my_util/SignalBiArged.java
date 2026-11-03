package qouteall.q_misc_util.my_util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.util.TriConsumer;
import qouteall.q_misc_util.Helper;

public class SignalBiArged<A, B> {
   private List<BiConsumer<A, B>> funcList = new ArrayList<>();
   private boolean isEmitting = false;

   public synchronized void emit(A a, B b) {
      this.isEmitting = true;

      try {
         this.funcList.forEach(runnable -> runnable.accept(a, b));
      } finally {
         this.isEmitting = false;
      }
   }

   public synchronized <T> void connectWithWeakRef(T owner, TriConsumer<T, A, B> func) {
      WeakReference<T> weakRef = new WeakReference<>(owner);
      Helper.SimpleBox<BiConsumer<A, B>> boxOfRunnable = new Helper.SimpleBox<>(null);
      boxOfRunnable.obj = (a, b) -> {
         T currentTarget = weakRef.get();
         if (currentTarget != null) {
            func.accept(currentTarget, a, b);
         } else {
            this.disconnect(boxOfRunnable.obj);
         }
      };
      this.connect(boxOfRunnable.obj);
   }

   public synchronized void connect(BiConsumer<A, B> func) {
      this.copyDataWhenEmitting();
      this.funcList.add(func);
   }

   public synchronized void disconnect(BiConsumer<A, B> func) {
      this.copyDataWhenEmitting();
      boolean removed = this.funcList.remove(func);

      assert removed;
   }

   private void copyDataWhenEmitting() {
      if (this.isEmitting) {
         this.funcList = new ArrayList<>(this.funcList);
      }
   }
}
