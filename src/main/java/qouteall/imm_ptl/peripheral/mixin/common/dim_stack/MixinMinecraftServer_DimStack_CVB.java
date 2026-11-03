package qouteall.imm_ptl.peripheral.mixin.common.dim_stack;

import java.util.Map;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.peripheral.dim_stack.DimStackManagement;

@Mixin({MinecraftServer.class})
public abstract class MixinMinecraftServer_DimStack_CVB {
   @Shadow
   @Final
   private Map<RegistryKey<World>, ServerWorld> worlds;

   @Shadow
   public abstract ServerWorld getWorld(RegistryKey<World> var1);

   @Inject(
      method = {"Lnet/minecraft/server/MinecraftServer;createWorlds()V"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/MinecraftServer;setupSpawn(Lnet/minecraft/server/world/ServerWorld;Lnet/minecraft/world/level/ServerWorldProperties;ZZLnet/minecraft/world/chunk/ChunkLoadProgress;)V"
      )}
   )
   private void onBeforeSetupSpawn(CallbackInfo ci) {
      DimStackManagement.onServerEarlyInit((MinecraftServer)this);
   }

   @Inject(
      method = {"Lnet/minecraft/server/MinecraftServer;createWorlds()V"},
      at = {@At("RETURN")}
   )
   private void onCreateWorldsFinishes(CallbackInfo ci) {
      DimStackManagement.onServerCreatedWorlds((MinecraftServer)this);
   }
}
