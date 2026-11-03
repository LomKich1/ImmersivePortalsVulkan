package qouteall.imm_ptl.core.mixin.common.interaction;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket.Action;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;

@Mixin({ServerPlayerInteractionManager.class})
public class MixinServerPlayerGameMode {
   @Shadow
   protected ServerWorld world;
   @Shadow
   @Final
   protected ServerPlayerEntity player;
   @Shadow
   private boolean failedToMine;
   @Shadow
   private boolean mining;
   @Unique
   private ServerWorld ip_destroyPosLevel;

   @Unique
   private ServerWorld ip_getActualWorld() {
      BlockManipulationServer.Context redirect = BlockManipulationServer.REDIRECT_CONTEXT.get();
      return redirect != null ? redirect.world() : this.world;
   }

   @Redirect(
      method = {"continueMining", "processBlockBreakingAction", "finishMining", "tryBreakBlock"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;world:Lnet/minecraft/server/world/ServerWorld;"
      )}
   )
   private ServerWorld redirectGetLevel(ServerPlayerInteractionManager serverPlayerGameMode) {
      return this.ip_getActualWorld();
   }

   @Redirect(
      method = {"continueMining", "processBlockBreakingAction"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/network/ServerPlayerEntity;getEntityWorld()Lnet/minecraft/server/world/ServerWorld;"
      )}
   )
   private ServerWorld redirectGetLevel(ServerPlayerEntity instance) {
      return this.ip_getActualWorld();
   }

   @Redirect(
      method = {"interactBlock"},
      at = {@At(
         value = "NEW",
         target = "(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/item/ItemUsageContext;"
      )}
   )
   private ItemUsageContext redirectNewUseOnContext(PlayerEntity player, Hand interactionHand, BlockHitResult blockHitResult) {
      return new ItemUsageContext(this.ip_getActualWorld(), player, interactionHand, player.getStackInHand(interactionHand), blockHitResult);
   }

   @WrapOperation(
      method = {"processBlockBreakingAction"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/server/network/ServerPlayerEntity;canInteractWithBlockAt(Lnet/minecraft/util/math/BlockPos;D)Z"
      )}
   )
   private boolean wrapDistanceInHandleBlockBreakAction(ServerPlayerEntity instance, BlockPos blockPos, double v, Operation<Boolean> original) {
      BlockManipulationServer.Context redirect = BlockManipulationServer.REDIRECT_CONTEXT.get();
      return redirect != null ? true : (Boolean)original.call(new Object[]{instance, blockPos, v});
   }

   @Inject(
      method = {"processBlockBreakingAction"},
      at = {@At(
         value = "FIELD",
         opcode = 181,
         target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;miningPos:Lnet/minecraft/util/math/BlockPos;"
      )}
   )
   private void onSetDestroyPos(BlockPos pos, Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo ci) {
      this.ip_destroyPosLevel = this.ip_getActualWorld();
   }

   @Inject(
      method = {"processBlockBreakingAction"},
      at = {@At(
         value = "FIELD",
         opcode = 181,
         target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;failedMiningPos:Lnet/minecraft/util/math/BlockPos;"
      )}
   )
   private void onSetDelayedDestroyPos(BlockPos pos, Action action, Direction face, int maxBuildHeight, int sequence, CallbackInfo ci) {
      this.ip_destroyPosLevel = this.ip_getActualWorld();
   }

   @Redirect(
      method = {"update"},
      at = {@At(
         value = "FIELD",
         opcode = 180,
         target = "Lnet/minecraft/server/network/ServerPlayerInteractionManager;world:Lnet/minecraft/server/world/ServerWorld;"
      )}
   )
   private ServerWorld redirectLevelOnTicking(ServerPlayerInteractionManager instance) {
      return this.ip_destroyPosLevel != null ? this.ip_destroyPosLevel : this.world;
   }

   @Inject(
      method = {"update"},
      at = {@At("RETURN")}
   )
   private void onTickingEnd(CallbackInfo ci) {
      if (!this.failedToMine && !this.mining) {
         this.ip_destroyPosLevel = null;
      }
   }
}
