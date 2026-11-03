package qouteall.q_misc_util.my_util;

import java.util.HashMap;

public class KeyedTaskList<K> {
   private HashMap<K, KeyedTaskList.Task<K>> tasks = new HashMap<>();

   public synchronized void addTask(K key, KeyedTaskList.Task<K> func) {
      this.tasks.put(key, func);
   }

   public synchronized void processTasks() {
      HashMap<K, KeyedTaskList.Task<K>> oldTasks = this.tasks;
      this.tasks = new HashMap<>();
      oldTasks.forEach((key, func) -> {
         if (!func.runAndGetIsSucceeded((K)key)) {
            this.tasks.putIfAbsent((K)key, (KeyedTaskList.Task<K>)func);
         }
      });
   }

   public synchronized boolean isTaskExist(K key) {
      return this.tasks.containsKey(key);
   }

   public interface Task<Key> {
      boolean runAndGetIsSucceeded(Key var1);
   }
}
