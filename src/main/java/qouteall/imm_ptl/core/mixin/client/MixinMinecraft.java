package qouteall.imm_ptl.core.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import java.util.List;
import java.util.function.Function;
import net.minecraft.util.profiler.Profilers;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.BufferBuilderStorage;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.WorldRenderer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.At.Shift;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientDimensionPrewarm;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPCGlobal;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEMinecraftClient;
import qouteall.imm_ptl.core.miscellaneous.ClientPerformanceMonitor;
import qouteall.imm_ptl.core.miscellaneous.IPortalInitialScreen;
import qouteall.imm_ptl.core.platform_specific.IPConfig;
import qouteall.imm_ptl.core.portal.animation.ClientPortalAnimationManagement;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.imm_ptl.core.render.context_management.RenderStates;
import qouteall.imm_ptl.core.render.context_management.WorldRenderInfo;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;

@Mixin({MinecraftClient.class})
public abstract class MixinMinecraft implements IEMinecraftClient {
   @Final
   @Shadow
   @Mutable
   private Framebuffer framebuffer;
   @Shadow
   public Screen currentScreen;
   @Mutable
   @Shadow
   @Final
   public WorldRenderer worldRenderer;
   @Shadow
   private static int currentFps;
   @Shadow
   @Nullable
   public ClientWorld world;
   @Mutable
   @Shadow
   @Final
   private BufferBuilderStorage bufferBuilders;
   @Shadow
   @Final
   private static Logger LOGGER;
   @Shadow
   private Thread thread;

   @WrapOperation(
      method = {"Lnet/minecraft/MinecraftClient;run()V"},
      at = {@At(
         value = "INVOKE",
         target = "Ljava/lang/Thread;currentThread()Ljava/lang/Thread;"
      )}
   )
   private Thread testMixinExtra(Operation<Thread> original) {
      LOGGER.info("[ImmPtl] MixinExtra is working!");
      return (Thread)original.call(new Object[0]);
   }

   @Inject(
      method = {"tick"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ClientWorld;tickEntities()V"
      )}
   )
   private void onBeforeTickingEntities(CallbackInfo ci) {
   }

   @Inject(
      method = {"Lnet/minecraft/MinecraftClient;tick()V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/ClientWorld;tick(Ljava/util/function/BooleanSupplier;)V",
         shift = Shift.AFTER
      )}
   )
   private void onAfterClientTick(CallbackInfo ci) {
      Profilers.get().push("imm_ptl_client_tick");
      ClientWorldLoader.tick();
      RenderStates.setPartialTick(0.0F);
      StableClientTimer.tick();
      StableClientTimer.update(this.world.getTime(), RenderStates.getPartialTick());
      ClientPortalAnimationManagement.tick();
      ClientTeleportationManager.manageTeleportation(true);
      IPGlobal.POST_CLIENT_TICK_EVENT.invoker().run();
      Profilers.get().pop();
   }

   @Inject(
      method = {"Lnet/minecraft/MinecraftClient;render(Z)V"},
      at = {@At(
         value = "FIELD",
         target = "Lnet/minecraft/MinecraftClient;currentFps:I",
         shift = Shift.AFTER
      )}
   )
   private void onSnooperUpdate(boolean tick, CallbackInfo ci) {
      ClientPerformanceMonitor.updateEverySecond(currentFps);
   }

   @Inject(
      method = {"Lnet/minecraft/MinecraftClient;setWorld(Lnet/minecraft/ClientWorld;Z)V"},
      at = {@At("HEAD")}
   )
   private void onSetWorld(ClientWorld clientLevel, boolean stopSounds, CallbackInfo ci) {
      ClientDimensionPrewarm.onVanillaLevelSet();
      if (ClientWorldLoader.getIsInitialized()) {
         LOGGER.info("Client cleanup");
         IPCGlobal.CLIENT_CLEANUP_EVENT.invoker().run();
         if (clientLevel == null) {
            LOGGER.info("Client exit world");
            IPCGlobal.CLIENT_EXIT_EVENT.invoker().run();
         }

         ClientWorldLoader.cleanUp();
      } else {
         LOGGER.info("Client world updated but not counted as cleanup");
      }
   }

   @Inject(
      method = {"Lnet/minecraft/MinecraftClient;usesImprovedTransparency()Z"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private static void onIsFabulousGraphicsOrBetter(CallbackInfoReturnable<Boolean> cir) {
      if (WorldRenderInfo.isRendering()) {
         cir.setReturnValue(false);
      }
   }

   @Inject(
      method = {"createInitScreens"},
      at = {@At("RETURN")}
   )
   private void onAddInitialScreens(List<Function<Runnable, Screen>> output, CallbackInfoReturnable<Boolean> cir) {
      IPConfig config = IPConfig.getConfig();
      if (!config.initialScreenShown) {
         output.add(IPortalInitialScreen::new);
      }
   }

   @Override
   public void ip_setFrameBuffer(Framebuffer buffer) {
      this.framebuffer = buffer;
   }

   @Override
   public Screen ip_getCurrentScreen() {
      return this.currentScreen;
   }

   @Override
   public void ip_setWorldRenderer(WorldRenderer r) {
      this.worldRenderer = r;
   }

   @Override
   public void ip_setRenderBuffers(BufferBuilderStorage arg) {
      this.bufferBuilders = arg;
   }

   @Override
   public Thread ip_getRunningThread() {
      return this.thread;
   }
}
