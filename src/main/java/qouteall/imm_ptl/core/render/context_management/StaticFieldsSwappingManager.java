package qouteall.imm_ptl.core.render.context_management;

import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.World;
import net.minecraft.registry.RegistryKey;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.Nullable;

public class StaticFieldsSwappingManager<Context> {
   private final Consumer<Context> copyFromObject;
   private final Consumer<Context> copyToObject;
   private final boolean strictCheck;
   @Nullable
   private final Supplier<Context> contextConstructor;
   private RegistryKey<World> outerDimension;
   private Stack<StaticFieldsSwappingManager.ContextRecord<Context>> swappedContext = new Stack<>();
   public final Map<RegistryKey<World>, StaticFieldsSwappingManager.ContextRecord<Context>> contextMap = new HashMap<>();

   public StaticFieldsSwappingManager(
      Consumer<Context> copyFromObject, Consumer<Context> copyToObject, boolean doStrictCheck, @Nullable Supplier<Context> contextConstructor
   ) {
      this.copyFromObject = copyFromObject;
      this.copyToObject = copyToObject;
      this.strictCheck = doStrictCheck;
      this.contextConstructor = contextConstructor;
   }

   public boolean isSwapped() {
      return !this.swappedContext.empty();
   }

   public void setOuterDimension(RegistryKey<World> dim) {
      Validate.isTrue(!this.isSwapped());
      this.outerDimension = dim;
   }

   public void resetChecks() {
      Validate.isTrue(!this.isSwapped());
      this.contextMap.values().forEach(record -> record.isHoldingLatestContext = record.dimension != this.outerDimension);
   }

   public RegistryKey<World> getCurrentDimension() {
      if (this.swappedContext.empty()) {
         Validate.notNull(this.outerDimension);
         return this.outerDimension;
      } else {
         return this.swappedContext.peek().dimension;
      }
   }

   public void pushSwapping(RegistryKey<World> newDimension) {
      RegistryKey<World> currentDimension = this.getCurrentDimension();
      StaticFieldsSwappingManager.ContextRecord<Context> oldContext = this.contextMap.get(currentDimension);
      StaticFieldsSwappingManager.ContextRecord<Context> newContext = this.contextMap
         .computeIfAbsent(newDimension, k -> new StaticFieldsSwappingManager.ContextRecord<>(newDimension, this.contextConstructor.get(), true));
      Validate.notNull(oldContext);
      Validate.notNull(newContext);
      this.swappedContext.push(newContext);
      this.transferDataFromStaticFieldsToObject(oldContext);
      this.transferDataFromObjectToStaticFields(newContext);
   }

   public void popSwapping() {
      StaticFieldsSwappingManager.ContextRecord<Context> outerContext = this.swappedContext.pop();
      StaticFieldsSwappingManager.ContextRecord<Context> innerContext = this.contextMap.get(this.getCurrentDimension());
      this.transferDataFromStaticFieldsToObject(outerContext);
      this.transferDataFromObjectToStaticFields(innerContext);
   }

   public void swapAndInvoke(RegistryKey<World> newDimension, Runnable func) {
      this.pushSwapping(newDimension);
      func.run();
      this.popSwapping();
   }

   private void transferDataFromObjectToStaticFields(StaticFieldsSwappingManager.ContextRecord<Context> newContext) {
      if (this.strictCheck || newContext != null) {
         if (this.strictCheck) {
            Validate.isTrue(newContext.isHoldingLatestContext);
         }

         newContext.isHoldingLatestContext = false;
         this.copyFromObject.accept(newContext.context);
      }
   }

   private void transferDataFromStaticFieldsToObject(StaticFieldsSwappingManager.ContextRecord<Context> oldContext) {
      if (this.strictCheck || oldContext != null) {
         if (this.strictCheck) {
            Validate.isTrue(!oldContext.isHoldingLatestContext);
         }

         oldContext.isHoldingLatestContext = true;
         this.copyToObject.accept(oldContext.context);
      }
   }

   public void updateOuterDimensionAndChangeContext(RegistryKey<World> newDimension) {
      Validate.isTrue(!this.isSwapped());
      Validate.notNull(this.outerDimension);
      RegistryKey<World> oldDimension = this.outerDimension;
      this.transferDataFromStaticFieldsToObject(this.contextMap.get(oldDimension));
      this.transferDataFromObjectToStaticFields(this.contextMap.get(newDimension));
      this.outerDimension = newDimension;
   }

   public static class ContextRecord<Ctx> {
      public RegistryKey<World> dimension;
      public Ctx context;
      public boolean isHoldingLatestContext = false;

      public ContextRecord(RegistryKey<World> dimension, Ctx context, boolean isHoldingLatestContext) {
         this.dimension = dimension;
         this.context = context;
         this.isHoldingLatestContext = isHoldingLatestContext;
      }
   }
}
