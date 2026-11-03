package qouteall.q_misc_util.my_util;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import qouteall.q_misc_util.Helper;

public class Signal {
   private List<Runnable> funcList = new ArrayList<>();
   private boolean isEmitting = false;

   public synchronized void emit() {
      this.isEmitting = true;

      try {
         this.funcList.forEach(runnable -> runnable.run());
      } finally {
         this.isEmitting = false;
      }
   }

   public synchronized <T> void connectWithWeakRef(T owner, Consumer<T> func) {
      WeakReference<T> weakRef = new WeakReference<>(owner);
      Helper.SimpleBox<Runnable> boxOfRunnable = new Helper.SimpleBox<>(null);
      boxOfRunnable.obj = () -> {
         T currentTarget = weakRef.get();
         if (currentTarget != null) {
            func.accept(currentTarget);
         } else {
            this.disconnect(boxOfRunnable.obj);
         }
      };
      this.connect(boxOfRunnable.obj);
   }

   public synchronized void connect(Runnable func) {
      this.copyDataWhenEmitting();
      this.funcList.add(func);
   }

   public synchronized void disconnect(Runnable func) {
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
