package qouteall.dimlib.mixin.common;

import com.google.common.collect.Maps;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executor;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerTask;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage.Session;
import org.apache.commons.lang3.Validate;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.dimlib.api.DimensionAPI;
import qouteall.dimlib.ducks.IMinecraftServer;

@Mixin({MinecraftServer.class})
public abstract class MixinMinecraftServer extends ReentrantThreadExecutor<ServerTask> implements IMinecraftServer {
   @Mutable
   @Shadow
   @Final
   private Map<RegistryKey<World>, ServerWorld> worlds;
   @Shadow
   @Final
   protected Session session;
   @Shadow
   @Final
   private Executor workerExecutor;
   @Unique
   private boolean ip_canDirectlyRegisterDimension = false;
   @Unique
   private boolean ip_finishedCreatingWorlds = false;
   @Unique
   private List<Runnable> dimlib_taskList;

   public MixinMinecraftServer(String name) {
      super(name);
      throw new RuntimeException();
   }

   @Shadow
   public abstract boolean isStopped();

   @Shadow
   public abstract boolean isDedicated();

   @Shadow
   protected abstract void runTasksTillTickEnd();

   @Inject(
      method = {"Lnet/minecraft/server/MinecraftServer;createWorlds()V"},
      at = {@At("HEAD")}
   )
   private void onBeforeCreateWorlds(CallbackInfo ci) {
      Validate.isTrue(!this.ip_canDirectlyRegisterDimension, "invalid server initialization status", new Object[0]);
      this.ip_canDirectlyRegisterDimension = true;
      DimensionAPI.SERVER_DIMENSIONS_LOAD_EVENT.invoker().run((MinecraftServer)this);
      this.ip_canDirectlyRegisterDimension = false;
   }

   @Inject(
      method = {"Lnet/minecraft/server/MinecraftServer;createWorlds()V"},
      at = {@At("RETURN")}
   )
   private void onFinishedLoadingAllWorlds(CallbackInfo ci) {
      this.ip_finishedCreatingWorlds = true;
   }

   @Override
   public void dimlib_addDimensionToWorldMap(RegistryKey<World> dim, ServerWorld world) {
      LinkedHashMap<RegistryKey<World>, ServerWorld> newMap = Maps.newLinkedHashMap();
      Map<RegistryKey<World>, ServerWorld> oldMap = this.worlds;
      newMap.putAll(oldMap);
      newMap.put(dim, world);
      this.worlds = newMap;
   }

   @Override
   public void dimlib_removeDimensionFromWorldMap(RegistryKey<World> dimension) {
      LinkedHashMap<RegistryKey<World>, ServerWorld> newMap = Maps.newLinkedHashMap();
      Map<RegistryKey<World>, ServerWorld> oldMap = this.worlds;

      for (Entry<RegistryKey<World>, ServerWorld> entry : oldMap.entrySet()) {
         if (entry.getKey() != dimension) {
            newMap.put(entry.getKey(), entry.getValue());
         }
      }

      this.worlds = newMap;
   }

   @Override
   public boolean dimlib_getCanDirectlyRegisterDimensions() {
      return this.ip_canDirectlyRegisterDimension;
   }

   @Override
   public boolean dimlib_getIsFinishedCreatingWorlds() {
      return this.ip_finishedCreatingWorlds;
   }

   @Override
   public Session dimlib_getStorageSource() {
      return this.session;
   }

   @Override
   public Executor dimlib_getExecutor() {
      return this.workerExecutor;
   }

   @Override
   public void dimlib_waitUntilNextTick() {
      Validate.isTrue(!this.hasRunningTasks());
      this.runTasksTillTickEnd();
   }

   @Override
   public void dimlib_addTask(Runnable task) {
      if (this.dimlib_taskList == null) {
         this.dimlib_taskList = new ArrayList<>();
      }

      this.dimlib_taskList.add(task);
   }

   @Override
   public void dimlib_processTasks() {
      if (this.dimlib_taskList != null) {
         for (Runnable task : this.dimlib_taskList) {
            task.run();
         }

         this.dimlib_taskList = null;
      }
   }
}
