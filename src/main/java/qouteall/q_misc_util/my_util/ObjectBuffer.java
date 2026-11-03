package qouteall.q_misc_util.my_util;

import java.util.ArrayDeque;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class ObjectBuffer<T> {
   private ArrayDeque<T> objects = new ArrayDeque<>();
   private int cacheSize;
   private Supplier<T> creator;
   private Consumer<T> destroyer;
   private int currentConsumption = 0;

   public ObjectBuffer(int cacheSize, Supplier<T> creator, Consumer<T> destroyer) {
      this.cacheSize = cacheSize;
      this.creator = creator;
      this.destroyer = destroyer;
   }

   public void setCacheSize(int newVal) {
      this.cacheSize = newVal;

      while (this.objects.size() > this.cacheSize) {
         T obj = this.objects.pollFirst();
         this.destroyer.accept(obj);
      }
   }

   public void reserveObjects(int num) {
      int requirement = this.cacheSize - this.objects.size();
      int supply = Math.max(0, Math.min(num, requirement));

      for (int i = 0; i < supply; i++) {
         this.objects.addFirst(this.creator.get());
      }

      this.currentConsumption = 0;
   }

   public void reserveObjectsByRatio(double ratio) {
      this.reserveObjects((int)(this.cacheSize * ratio));
   }

   public T takeObject() {
      if (this.objects.isEmpty()) {
         this.currentConsumption++;
         return this.creator.get();
      } else {
         return this.objects.pollFirst();
      }
   }

   public void returnObject(T t) {
      if (this.objects.size() >= this.cacheSize) {
         this.destroyer.accept(t);
      } else {
         this.objects.addFirst(t);
      }
   }

   public void destroyAll() {
      this.objects.forEach(this.destroyer);
      this.objects.clear();
   }
}
