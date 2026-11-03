package qouteall.imm_ptl.core.mixin.client.interaction;

import io.netty.buffer.Unpooled;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import qouteall.imm_ptl.core.ClientWorldLoader;
import qouteall.imm_ptl.core.IPMcHelper;
import qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer;
import qouteall.imm_ptl.core.ducks.IEClientPlayerInteractionManager;
import qouteall.q_misc_util.api.McRemoteProcedureCall;

@Mixin({ClientPlayerInteractionManager.class})
public abstract class MixinMultiPlayerGameMode implements IEClientPlayerInteractionManager {
   @Shadow
   @Final
   private ClientPlayNetworkHandler networkHandler;
   @Shadow
   @Final
   private MinecraftClient client;

   @Redirect(
      method = {"lambda$startDestroyBlock$1", "method_41930"},
      require = 1,
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEntityWorld()Lnet/minecraft/world/World;"
      )}
   )
   private World redirectPlayerLevel1(ClientPlayerEntity instance) {
      return MinecraftClient.getInstance().world;
   }

   @Redirect(
      method = {"updateBlockBreakingProgress"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayerEntity;getEntityWorld()Lnet/minecraft/world/World;"
      )}
   )
   private World redirectPlayerLevel2(ClientPlayerEntity instance) {
      return MinecraftClient.getInstance().world;
   }

   @Redirect(
      method = {"interactBlockInternal"},
      at = {@At(
         value = "NEW",
         target = "(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/item/ItemUsageContext;"
      )}
   )
   private ItemUsageContext redirectNewUseOnContext(PlayerEntity player, Hand interactionHand, BlockHitResult blockHitResult) {
      return new ItemUsageContext(MinecraftClient.getInstance().world, player, interactionHand, player.getStackInHand(interactionHand), blockHitResult);
   }

   @ModifyArg(
      method = {"sendSequencedPacket"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
      )}
   )
   private Packet<?> modifyPacketInStartPrediction(Packet<?> packet) {
      return ip_redirectPacket(packet);
   }

   @ModifyArg(
      method = {"attackBlock"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
      )}
   )
   private Packet redirectSendInStartDestroyBlock(Packet packet) {
      return ip_redirectPacket(packet);
   }

   @ModifyArg(
      method = {"cancelBlockBreaking"},
      at = {@At(
         value = "INVOKE",
         target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V"
      )}
   )
   private Packet redirectSendInStopDestroyBlock(Packet packet) {
      return ip_redirectPacket(packet);
   }

   private static Packet<?> ip_redirectPacket(Packet<?> packet) {
      if (ClientWorldLoader.getIsWorldSwitched()) {
         RegistryKey<World> dimension = MinecraftClient.getInstance().world.getRegistryKey();
         if (packet instanceof PlayerActionC2SPacket playerActionPacket) {
            if (BlockManipulationServer.isAttackingAction(playerActionPacket.getAction())) {
               PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
               PlayerActionC2SPacket.CODEC.encode(buf, playerActionPacket);
               return McRemoteProcedureCall.createPacketToSendToServer(
                  "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processPlayerActionPacket",
                  dimension,
                  IPMcHelper.bufToBytes(buf)
               );
            }
         } else if (packet instanceof PlayerInteractBlockC2SPacket useItemOnPacket) {
            PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());
            PlayerInteractBlockC2SPacket.CODEC.encode(buf, useItemOnPacket);
            return McRemoteProcedureCall.createPacketToSendToServer(
               "qouteall.imm_ptl.core.block_manipulation.BlockManipulationServer.RemoteCallables.processUseItemOnPacket", dimension, IPMcHelper.bufToBytes(buf)
            );
         }
      }

      return packet;
   }
}
