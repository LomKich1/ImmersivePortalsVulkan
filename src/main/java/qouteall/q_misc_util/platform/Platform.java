package qouteall.q_misc_util.platform;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.command.argument.serialize.ArgumentSerializer.ArgumentTypeProperties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemGroup.Builder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

public interface Platform {
   static Platform get() {
      return Platform.Holder.INSTANCE;
   }

   Path getGameDir();

   Path getConfigDir();

   boolean isDedicatedServer();

   boolean isDevelopmentEnvironment();

   boolean isForgeLike();

   boolean isModLoaded(String var1);

   Optional<ModInfo> getModContainer(String var1);

   List<ModInfo> getAllMods();

   int compareVersions(String var1, String var2);

   <T> void registerDynamicRegistry(RegistryKey<Registry<T>> var1, Codec<T> var2);

   <A extends ArgumentType<?>, T extends ArgumentTypeProperties<A>, I extends ArgumentSerializer<A, T>> void registerArgumentType(
      Identifier var1, Class<A> var2, I var3
   );

   <T> void registerBuiltIn(Registry<T> var1, Identifier var2, T var3);

   Builder createCreativeTabBuilder();

   <T extends Entity> net.minecraft.entity.EntityType.Builder<T> forceTrackedVelocityUpdates(net.minecraft.entity.EntityType.Builder<T> var1, boolean var2);

   void onServerTickEnd(Consumer<MinecraftServer> var1);

   void onServerStarted(Consumer<MinecraftServer> var1);

   void onServerDataPackReloadEnd(Consumer<MinecraftServer> var1);

   default void onPlayerChangedDimension(ServerPlayerEntity player, RegistryKey<World> from, RegistryKey<World> to) {
   }

   void onRegisterServerCommands(Platform.ServerCommandRegistrar var1);

   void onAttackBlock(Platform.AttackBlockHandler var1);

   @FunctionalInterface
   public interface AttackBlockHandler {
      ActionResult interact(PlayerEntity var1, World var2, Hand var3, BlockPos var4, Direction var5);
   }

   public static class Holder {
      private static final Platform INSTANCE = ServiceLoader.load(Platform.class)
         .findFirst()
         .orElseThrow(() -> new IllegalStateException("No Platform implementation found"));
   }

   @FunctionalInterface
   public interface ServerCommandRegistrar {
      void register(CommandDispatcher<ServerCommandSource> var1, CommandRegistryAccess var2);
   }
}
