package qouteall.q_misc_util.mixin;

import com.mojang.datafixers.DataFixer;
import java.lang.ref.WeakReference;
import java.net.Proxy;
import java.util.concurrent.Executor;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.util.ApiServices;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.chunk.ChunkLoadProgress;
import net.minecraft.world.level.storage.LevelStorage.Session;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.q_misc_util.MiscGlobals;
import qouteall.q_misc_util.dimension.DimensionIntId;
import qouteall.q_misc_util.ducks.IEMinecraftServer_Misc;

@Mixin({MinecraftServer.class})
public abstract class MixinMinecraftServer_Misc extends ReentrantThreadExecutor implements IEMinecraftServer_Misc {
   @Shadow
   @Final
   public Session session;
   @Shadow
   @Final
   private Executor workerExecutor;

   public MixinMinecraftServer_Misc(String string) {
      super(string);
      throw new RuntimeException();
   }

   @Shadow
   public abstract boolean isDedicated();

   @Shadow
   protected abstract void runTasksTillTickEnd();

   @Inject(
      method = {"<init>"},
      at = {@At("RETURN")}
   )
   private void onConstruct(
      Thread thread,
      Session levelStorageAccess,
      ResourcePackManager packRepository,
      SaveLoader worldStem,
      Proxy proxy,
      DataFixer dataFixer,
      ApiServices services,
      ChunkLoadProgress levelLoadListener,
      CallbackInfo ci
   ) {
      MiscGlobals.refMinecraftServer = new WeakReference<>((MinecraftServer)this);
   }

   @Inject(
      method = {"createWorlds"},
      at = {@At("RETURN")}
   )
   private void onWorldsCreated(CallbackInfo ci) {
      DimensionIntId.onServerStarted((MinecraftServer)this);
   }

   @Override
   public Session ip_getStorageSource() {
      return this.session;
   }
}
