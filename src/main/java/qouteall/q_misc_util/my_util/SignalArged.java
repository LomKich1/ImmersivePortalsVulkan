package qouteall.q_misc_util.my_util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import qouteall.q_misc_util.Helper;

public class SignalArged<Arg> {
   private List<Consumer<Arg>> funcList = new ArrayList<>();
   private boolean isEmitting = false;

   public synchronized void emit(Arg arg) {
      this.isEmitting = true;

      try {
         this.funcList.forEach(runnable -> runnable.accept(arg));
      } finally {
         this.isEmitting = false;
      }
   }

   public synchronized <T> void connectWithWeakRef(T owner, BiConsumer<T, Arg> func) {
      WeakReference<T> weakRef = new WeakReference<>(owner);
      Helper.SimpleBox<Consumer<Arg>> boxOfRunnable = new Helper.SimpleBox<>(null);
      boxOfRunnable.obj = arg -> {
         T currentTarget = weakRef.get();
         if (currentTarget != null) {
            func.accept(currentTarget, arg);
         } else {
            this.disconnect(boxOfRunnable.obj);
         }
      };
      this.connect(boxOfRunnable.obj);
   }

   public synchronized void connect(Consumer<Arg> func) {
      this.copyDataWhenEmitting();
      this.funcList.add(func);
   }

   public synchronized void disconnect(Consumer<Arg> func) {
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
