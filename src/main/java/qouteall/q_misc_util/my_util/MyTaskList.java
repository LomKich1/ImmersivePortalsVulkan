package qouteall.q_misc_util.my_util;

import com.google.common.collect.Iterators;
import com.google.common.collect.PeekingIterator;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import it.unimi.dsi.fastutil.objects.ObjectListIterator;
import java.util.Iterator;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.slf4j.Logger;
import qouteall.q_misc_util.Helper;

public class MyTaskList {
   private static final Logger LOGGER = LogUtils.getLogger();
   private final ObjectList<MyTaskList.MyTask> tasks = new ObjectArrayList();
   private final ObjectList<MyTaskList.MyTask> tasksToAdd = new ObjectArrayList();

   public synchronized void addTask(MyTaskList.MyTask task) {
      this.tasksToAdd.add(task);
   }

   public void addOneShotTask(Runnable runnable) {
      this.addTask(() -> {
         runnable.run();
         return true;
      });
   }

   public synchronized void processTasks() {
      this.tasks.addAll(this.tasksToAdd);
      this.tasksToAdd.clear();
      Helper.removeIf(this.tasks, task -> {
         try {
            return task.runAndGetIsFinished();
         } catch (Throwable var2) {
            LOGGER.error("Failed to process task {}", task, var2);
            return true;
         }
      });
   }

   public synchronized void forceClearTasks() {
      ObjectListIterator var1 = this.tasks.iterator();

      while (var1.hasNext()) {
         MyTaskList.MyTask task = (MyTaskList.MyTask)var1.next();
         task.onCancelled();
      }

      var1 = this.tasksToAdd.iterator();

      while (var1.hasNext()) {
         MyTaskList.MyTask task = (MyTaskList.MyTask)var1.next();
         task.onCancelled();
      }

      this.tasks.clear();
      this.tasksToAdd.clear();
   }

   public static MyTaskList.MyTask oneShotTask(Runnable runnable) {
      return () -> {
         runnable.run();
         return true;
      };
   }

   public static MyTaskList.MyTask nullTask() {
      return () -> true;
   }

   public static MyTaskList.MyTask chainTask(MyTaskList.MyTask a, MyTaskList.MyTask b) {
      return new MyTaskList.MyTask() {
         boolean aFinished = false;

         @Override
         public boolean runAndGetIsFinished() {
            if (!this.aFinished) {
               boolean finished = a.runAndGetIsFinished();
               if (!finished) {
                  return false;
               }

               this.aFinished = true;
            }

            return b.runAndGetIsFinished();
         }

         @Override
         public void onCancelled() {
            if (!this.aFinished) {
               a.onCancelled();
            }

            b.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withDelay(int iterations, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         private int counter = iterations;

         @Override
         public boolean runAndGetIsFinished() {
            if (this.counter > 0) {
               this.counter--;
               return false;
            } else {
               return task.runAndGetIsFinished();
            }
         }

         @Override
         public void onCancelled() {
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withCancelCondition(BooleanSupplier shouldCancel, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         @Override
         public boolean runAndGetIsFinished() {
            return shouldCancel.getAsBoolean() ? true : task.runAndGetIsFinished();
         }

         @Override
         public void onCancelled() {
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withDelayCondition(BooleanSupplier shouldDelay, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         @Override
         public boolean runAndGetIsFinished() {
            return shouldDelay.getAsBoolean() ? false : task.runAndGetIsFinished();
         }

         @Override
         public void onCancelled() {
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withTimeDelayedFromNow(double seconds, MyTaskList.MyTask task) {
      long startTime = System.nanoTime();
      return withDelayCondition(() -> System.nanoTime() - startTime < Helper.secondToNano(seconds), task);
   }

   public static MyTaskList.MyTask withRetryNumberLimit(int retryNumberLimit, MyTaskList.MyTask task, Runnable onLimitReached) {
      return new MyTaskList.MyTask() {
         private int retryNumber = 0;

         @Override
         public boolean runAndGetIsFinished() {
            boolean finished = task.runAndGetIsFinished();
            if (finished) {
               return true;
            } else {
               this.retryNumber++;
               if (this.retryNumber > retryNumberLimit) {
                  onLimitReached.run();
                  return true;
               } else {
                  return false;
               }
            }
         }

         @Override
         public void onCancelled() {
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withInterval(int interval, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         int i = 0;

         @Override
         public boolean runAndGetIsFinished() {
            if (this.i < interval) {
               this.i++;
               return false;
            } else {
               this.i = 0;
               return task.runAndGetIsFinished();
            }
         }

         @Override
         public void onCancelled() {
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withMacroLifecycle(Runnable beginAction, Runnable endAction, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         boolean began = false;

         @Override
         public boolean runAndGetIsFinished() {
            if (!this.began) {
               this.began = true;
               beginAction.run();
            }

            boolean finished = task.runAndGetIsFinished();
            if (finished) {
               endAction.run();
            }

            return finished;
         }

         @Override
         public void onCancelled() {
            if (this.began) {
               endAction.run();
            }

            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask withMicroLifecycle(Runnable beginAction, Runnable endAction, MyTaskList.MyTask task) {
      return new MyTaskList.MyTask() {
         @Override
         public boolean runAndGetIsFinished() {
            beginAction.run();
            boolean finished = task.runAndGetIsFinished();
            endAction.run();
            return finished;
         }

         @Override
         public void onCancelled() {
            endAction.run();
            task.onCancelled();
         }
      };
   }

   public static MyTaskList.MyTask chainTasks(Iterator<MyTaskList.MyTask> tasks) {
      final PeekingIterator<MyTaskList.MyTask> peekingIterator = Iterators.peekingIterator(tasks);
      return new MyTaskList.MyTask() {
         @Override
         public boolean runAndGetIsFinished() {
            if (peekingIterator.hasNext()) {
               MyTaskList.MyTask curr = (MyTaskList.MyTask)peekingIterator.peek();
               boolean finished = curr.runAndGetIsFinished();
               if (finished) {
                  peekingIterator.next();
               }
            }

            return !peekingIterator.hasNext();
         }

         @Override
         public void onCancelled() {
            while (peekingIterator.hasNext()) {
               MyTaskList.MyTask curr = (MyTaskList.MyTask)peekingIterator.next();
               curr.onCancelled();
            }
         }
      };
   }

   public static MyTaskList.MyTask repeat(int number, Supplier<MyTaskList.MyTask> taskConstructor) {
      return chainTasks(Stream.generate(taskConstructor).limit(number).iterator());
   }

   public interface MyTask {
      boolean runAndGetIsFinished();

      default void onCancelled() {
      }
   }
}
