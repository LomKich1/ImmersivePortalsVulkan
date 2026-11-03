package qouteall.imm_ptl.core.mixin.common.container_gui;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.entity.ViewerCountManager;
import net.minecraft.entity.ContainerUser;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ViewerCountManager.class})
public abstract class MixinContainerOpenersCounter {
   @Shadow
   public abstract boolean isPlayerViewing(PlayerEntity var1);

   @Shadow
   protected abstract boolean hasViewingUsers(Entity var1, BlockPos var2);

   @Inject(
      method = {"getViewingUsers"},
      at = {@At("HEAD")},
      cancellable = true
   )
   private void getOpenCount(World level, BlockPos pos, CallbackInfoReturnable<List<ContainerUser>> cir) {
      List<ContainerUser> list = new ArrayList<>();
      MinecraftServer server = level.getServer();

      assert server != null;

      for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
         if (this.hasViewingUsers(player, pos)) {
            list.add(player);
         }
      }

      cir.setReturnValue(list);
   }
}
