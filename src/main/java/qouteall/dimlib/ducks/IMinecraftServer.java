package qouteall.dimlib.ducks;

import java.util.concurrent.Executor;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import net.minecraft.world.level.storage.LevelStorage.Session;

public interface IMinecraftServer {
   Session dimlib_getStorageSource();

   Executor dimlib_getExecutor();

   void dimlib_addDimensionToWorldMap(RegistryKey<World> var1, ServerWorld var2);

   void dimlib_removeDimensionFromWorldMap(RegistryKey<World> var1);

   void dimlib_waitUntilNextTick();

   boolean dimlib_getCanDirectlyRegisterDimensions();

   boolean dimlib_getIsFinishedCreatingWorlds();

   void dimlib_addTask(Runnable var1);

   void dimlib_processTasks();
}
