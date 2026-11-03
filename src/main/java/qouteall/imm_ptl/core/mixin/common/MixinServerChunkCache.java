package qouteall.imm_ptl.core.mixin.common;

import net.minecraft.server.world.ChunkLevelManager;
import net.minecraft.server.world.ServerChunkManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import qouteall.imm_ptl.core.ducks.IEServerChunkCache;

@Mixin({ServerChunkManager.class})
public abstract class MixinServerChunkCache implements IEServerChunkCache {
   @Shadow
   @Final
   private ChunkLevelManager levelManager;

   @Override
   public ChunkLevelManager ip_getDistanceManager() {
      return this.levelManager;
   }
}
