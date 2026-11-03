package qouteall.q_misc_util.platform.fabric;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.serialization.Codec;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.EndDataPackReload;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.registry.DynamicRegistries;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.SemanticVersion;
import net.fabricmc.loader.api.Version;
import net.fabricmc.loader.api.VersionParsingException;
import net.minecraft.command.argument.serialize.ArgumentSerializer;
import net.minecraft.command.argument.serialize.ArgumentSerializer.ArgumentTypeProperties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType.Builder;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Identifier;
import qouteall.q_misc_util.platform.ModInfo;
import qouteall.q_misc_util.platform.Platform;

public class FabricPlatform implements Platform {
   @Override
   public Path getGameDir() {
      return FabricLoader.getInstance().getGameDir();
   }

   @Override
   public Path getConfigDir() {
      return FabricLoader.getInstance().getConfigDir();
   }

   @Override
   public boolean isDedicatedServer() {
      return FabricLoader.getInstance().getEnvironmentType() == EnvType.SERVER;
   }

   @Override
   public boolean isDevelopmentEnvironment() {
      return FabricLoader.getInstance().isDevelopmentEnvironment();
   }

   @Override
   public boolean isForgeLike() {
      return false;
   }

   @Override
   public boolean isModLoaded(String modId) {
      return FabricLoader.getInstance().isModLoaded(modId);
   }

   @Override
   public Optional<ModInfo> getModContainer(String modId) {
      return FabricLoader.getInstance().getModContainer(modId).map(FabricPlatform::toModInfo);
   }

   @Override
   public List<ModInfo> getAllMods() {
      return FabricLoader.getInstance().getAllMods().stream().map(FabricPlatform::toModInfo).toList();
   }

   private static ModInfo toModInfo(ModContainer container) {
      Version version = container.getMetadata().getVersion();
      int[] components = null;
      if (version instanceof SemanticVersion semanticVersion) {
         components = new int[semanticVersion.getVersionComponentCount()];

         for (int i = 0; i < components.length; i++) {
            components[i] = semanticVersion.getVersionComponent(i);
         }
      }

      return new ModInfo(
         container.getMetadata().getId(),
         container.getMetadata().getName(),
         version.toString(),
         components,
         container.getMetadata().getIconPath(512),
         container.getContainingMod().isPresent()
      );
   }

   @Override
   public int compareVersions(String a, String b) {
      try {
         return Version.parse(a).compareTo(Version.parse(b));
      } catch (VersionParsingException var4) {
         throw new IllegalArgumentException(var4);
      }
   }

   @Override
   public <T> void registerDynamicRegistry(RegistryKey<Registry<T>> key, Codec<T> codec) {
      DynamicRegistries.register(key, codec);
   }

   @Override
   public <A extends ArgumentType<?>, T extends ArgumentTypeProperties<A>, I extends ArgumentSerializer<A, T>> void registerArgumentType(
      Identifier id, Class<A> clazz, I info
   ) {
      ArgumentTypeRegistry.registerArgumentType(id, clazz, info);
   }

   @Override
   public <T> void registerBuiltIn(Registry<T> registry, Identifier id, T value) {
      Registry.register(registry, id, value);
   }

   @Override
   public net.minecraft.item.ItemGroup.Builder createCreativeTabBuilder() {
      return FabricItemGroup.builder();
   }

   @Override
   public <T extends Entity> Builder<T> forceTrackedVelocityUpdates(Builder<T> builder, boolean value) {
      return ((net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityType.Builder)builder).alwaysUpdateVelocity(value);
   }

   @Override
   public void onServerTickEnd(Consumer<MinecraftServer> listener) {
      ServerTickEvents.END_SERVER_TICK.register(listener::accept);
   }

   @Override
   public void onServerStarted(Consumer<MinecraftServer> listener) {
      ServerLifecycleEvents.SERVER_STARTED.register(listener::accept);
   }

   @Override
   public void onServerDataPackReloadEnd(Consumer<MinecraftServer> listener) {
      ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((EndDataPackReload)(server, resourceManager, success) -> listener.accept(server));
   }

   @Override
   public void onRegisterServerCommands(Platform.ServerCommandRegistrar registrar) {
      CommandRegistrationCallback.EVENT.register((CommandRegistrationCallback)(dispatcher, ctx, environment) -> registrar.register(dispatcher, ctx));
   }

   @Override
   public void onAttackBlock(Platform.AttackBlockHandler handler) {
      AttackBlockCallback.EVENT.register(handler::interact);
   }
}
