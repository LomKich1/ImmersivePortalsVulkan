package qouteall.imm_ptl.core.mixin.client;

import java.util.List;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.world.ClientWorld.Properties;
import net.minecraft.entity.Entity;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.item.map.MapState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.EntityList;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.tick.TickManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPGlobal;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.ducks.IEEntity;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.q_misc_util.my_util.LimitedLogger;

@Mixin({ClientWorld.class})
public abstract class MixinClientLevel implements IEClientWorld {
   private List<Portal> portal_globalPortals;
   private static final LimitedLogger limitedLogger = new LimitedLogger(100);
   @Shadow
   @Final
   @Mutable
   private ClientPlayNetworkHandler networkHandler;
   @Mutable
   @Shadow
   @Final
   private ClientChunkManager chunkManager;
   @Shadow
   @Final
   private MinecraftClient client;
   @Mutable
   @Shadow
   @Final
   private WorldRenderer worldRenderer;
   @Shadow
   @Final
   private EntityList entityList;
   @Shadow
   @Final
   private PendingUpdateManager pendingUpdateManager;
   @Shadow
   @Final
   @Mutable
   private TickManager tickManager;

   @Shadow
   public abstract Entity getEntityById(int var1);

   @Shadow
   protected abstract Map<String, MapState> getMapStates();

   @Shadow
   protected abstract void putMapStates(Map<String, MapState> var1);

   @Override
   public List<Portal> ip_getGlobalPortals() {
      return this.portal_globalPortals;
   }

   @Override
   public void ip_setGlobalPortals(List<Portal> arg) {
      this.portal_globalPortals = arg;
   }

   @Inject(
      method = {"<init>"},
      at = @At("RETURN")
   )
   void onConstructed(
      ClientPlayNetworkHandler clientPacketListener,
      Properties clientLevelData,
      RegistryKey resourceKey,
      RegistryEntry holder,
      int loadDistance,
      int j,
      WorldRenderer levelRenderer,
      boolean bl,
      long l,
      int seaLevel,
      CallbackInfo ci
   ) {
      ClientWorld clientWorld = (ClientWorld)(Object)this;
      ClientChunkManager myClientChunkManager = O_O.createMyClientChunkManager(clientWorld, loadDistance);
      this.chunkManager = myClientChunkManager;
   }

   @Inject(
      method = {"addEntity"},
      at = @At("TAIL")
   )
   private void onOnEntityAdded(Entity entityIn, CallbackInfo ci) {
      if (ClientWorldLoader.getIsInitialized()) {
         for (ClientWorld world : ClientWorldLoader.getClientWorlds()) {
            if (world != this) {
               world.removeEntity(entityIn.getId(), RemovalReason.DISCARDED);
            }
         }
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/world/ClientWorld;isChunkLoaded(II)Z"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onHasChunk(int chunkX, int chunkZ, CallbackInfoReturnable<Boolean> cir) {
      if (IPGlobal.tickOnlyIfChunkLoaded) {
         WorldChunk chunk = this.chunkManager.getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
         if (chunk == null || chunk instanceof EmptyChunk) {
            cir.setReturnValue(false);
         }
      }
   }

   @Inject(
      method = {"Lnet/minecraft/client/world/ClientWorld;toString()Ljava/lang/String;"},
      at = @At("HEAD"),
      cancellable = true
   )
   private void onToString(CallbackInfoReturnable<String> cir) {
      ClientWorld this_ = (ClientWorld)(Object)this;
      cir.setReturnValue("ClientWorld " + this_.getRegistryKey().getValue());
   }

   @Inject(
      method = {"tickEntity"},
      at = @At("HEAD")
   )
   private void onTickNonPassenger(Entity entity, CallbackInfo ci) {
      ((IEEntity)(Object)entity).ip_tickCollidingPortal();
   }

   @Override
   public void ip_resetWorldRendererRef() {
      this.worldRenderer = null;
   }

   @Override
   public EntityList ip_getEntityList() {
      return this.entityList;
   }

   @Override
   public Map<String, MapState> ip_getAllMapData() {
      return this.getMapStates();
   }

   @Override
   public void ip_addMapData(Map<String, MapState> map) {
      this.putMapStates(map);
   }

   @Override
   public PendingUpdateManager ip_getBlockStatePredictionHandler() {
      return this.pendingUpdateManager;
   }

   @Override
   public void ip_setTickRateManager(TickManager cond) {
      this.tickManager = cond;
   }
}
