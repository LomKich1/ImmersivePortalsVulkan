package qouteall.imm_ptl.core.mixin.client.sync;

import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.thread.ReentrantThreadExecutor;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.miscellaneous.IPVanillaCopy;
import qouteall.imm_ptl.core.network.PacketRedirectionClient;

@Mixin({MinecraftClient.class})
public abstract class MixinMinecraft_RedirectedPacket extends ReentrantThreadExecutor<Runnable> {
   public MixinMinecraft_RedirectedPacket(String string) {
      super(string);
   }

   @Inject(
      method = {"Lnet/minecraft/client/MinecraftClient;createTask(Ljava/lang/Runnable;)Ljava/lang/Runnable;"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void onCreateTask(Runnable runnable, CallbackInfoReturnable<Runnable> cir) {
      MinecraftClient this_ = (MinecraftClient)this;
      RegistryKey<World> redirectedDimension = PacketRedirectionClient.clientTaskRedirection.get();
      if (redirectedDimension != null) {
         Runnable newRunnable = () -> ClientWorldLoader.withSwitchedWorldFailSoft(redirectedDimension, runnable);
         cir.setReturnValue(newRunnable);
      }
   }

   @IPVanillaCopy
   public boolean shouldExecuteAsync() {
      boolean onThread = this.isOnThread();
      return onThread && PacketRedirectionClient.getIsProcessingRedirectedMessage() ? false : this.hasRunningTasks() || !onThread;
   }
}
