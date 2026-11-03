package qouteall.imm_ptl.core.mixin.common.entity_sync;

import com.mojang.authlib.GameProfile;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.World;
import net.minecraft.util.math.Vec3d;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEServerPlayerEntity;

@Mixin({ServerPlayerEntity.class})
public abstract class MixinServerPlayer extends PlayerEntity implements IEServerPlayerEntity {
   @Shadow
   public ServerPlayNetworkHandler networkHandler;
   @Shadow
   private Vec3d enteredNetherPos;
   @Shadow
   private boolean inTeleportationState;

   public MixinServerPlayer(World level, GameProfile gameProfile) {
      super(level, gameProfile);
   }

   @Shadow
   protected abstract void worldChanged(ServerWorld var1);

   @Override
   public void ip_stopRidingWithoutTeleportRequest() {
      super.stopRiding();
   }

   @Override
   public void ip_startRidingWithoutTeleportRequest(Entity newVehicle) {
      super.startRiding(newVehicle, true, false);
   }

   @Override
   public void portal_worldChanged(ServerWorld fromWorld, Vec3d fromPos) {
      if (fromWorld.getRegistryKey() == World.OVERWORLD && this.getEntityWorld().getRegistryKey() == World.NETHER) {
         this.enteredNetherPos = fromPos;
      }

      this.worldChanged(fromWorld);
   }
}
