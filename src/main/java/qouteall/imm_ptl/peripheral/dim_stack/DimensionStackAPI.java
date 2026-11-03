package qouteall.imm_ptl.peripheral.dim_stack;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.DynamicRegistryManager.Immutable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import net.minecraft.world.gen.GeneratorOptions;
import org.jetbrains.annotations.Nullable;
import qouteall.q_misc_util.platform.event.Event;
import qouteall.q_misc_util.platform.event.EventFactory;

public class DimensionStackAPI {
   public static final Event<DimensionStackAPI.DimensionCollectionCallback> DIMENSION_STACK_CANDIDATE_COLLECTION_EVENT = EventFactory.createArrayBacked(
      DimensionStackAPI.DimensionCollectionCallback.class, listeners -> (registryAccess, options) -> {
         Set<RegistryKey<World>> totalResult = new LinkedHashSet<>();

         for (DimensionStackAPI.DimensionCollectionCallback listener : listeners) {
            totalResult.addAll(listener.getExtraDimensionKeys(registryAccess, options));
         }

         return totalResult;
      }
   );
   public static final Event<DimensionStackAPI.DimensionStackPreUpdateCallback> DIMENSION_STACK_PRE_UPDATE_EVENT = EventFactory.createArrayBacked(
      DimensionStackAPI.DimensionStackPreUpdateCallback.class, listeners -> (server, dimStackInfo) -> {
         for (DimensionStackAPI.DimensionStackPreUpdateCallback listener : listeners) {
            listener.run(server, dimStackInfo);
         }
      }
   );

   public interface DimensionCollectionCallback {
      Collection<RegistryKey<World>> getExtraDimensionKeys(Immutable var1, GeneratorOptions var2);
   }

   public interface DimensionStackPreUpdateCallback {
      void run(MinecraftServer var1, @Nullable DimStackInfo var2);
   }
}
