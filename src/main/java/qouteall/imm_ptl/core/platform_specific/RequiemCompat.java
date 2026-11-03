package qouteall.imm_ptl.core.platform_specific;

import java.lang.reflect.Method;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.ClientPlayerEntity;
import org.apache.commons.lang3.Validate;
import qouteall.imm_ptl.core.McHelper;
import qouteall.imm_ptl.core.teleportation.ClientTeleportationManager;
import qouteall.imm_ptl.core.teleportation.ServerTeleportationManager;
import qouteall.q_misc_util.Helper;
import qouteall.q_misc_util.platform.Platform;

public class RequiemCompat {
   private static boolean isRequiemPresent = false;
   private static Class class_RequiemPlayer;
   private static Class class_PossessionComponent;
   private static Method method_asPossessor;
   private static Method method_getPossessedEntity;

   public static boolean getIsRequiemPresent() {
      return isRequiemPresent;
   }

   public static void init() {
      isRequiemPresent = Platform.get().isModLoaded("requiem");
      if (isRequiemPresent) {
         class_RequiemPlayer = Helper.noError(() -> Class.forName("ladysnake.requiem.api.v1.RequiemPlayer"));
         class_PossessionComponent = Helper.noError(() -> Class.forName("ladysnake.requiem.api.v1.possession.PossessionComponent"));
         method_asPossessor = Helper.noError(() -> class_RequiemPlayer.getDeclaredMethod("asPossessor"));
         method_getPossessedEntity = Helper.noError(() -> class_PossessionComponent.getDeclaredMethod("getPossessedEntity"));
      }
   }

   public static MobEntity getPossessedEntity(PlayerEntity player) {
      Validate.isTrue(isRequiemPresent);
      Object possessionComponent = Helper.noError(() -> method_asPossessor.invoke(player));
      Object possessedEntity = Helper.noError(() -> method_getPossessedEntity.invoke(possessionComponent));
      return (MobEntity)possessedEntity;
   }

   @Environment(EnvType.CLIENT)
   public static void onPlayerTeleportedClient() {
      if (isRequiemPresent) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         MobEntity possessedEntity = getPossessedEntity(player);
         if (possessedEntity != null && possessedEntity.getEntityWorld() != player.getEntityWorld()) {
            Helper.LOGGER.info("Move Requiem Possessed Entity at Client");
            ClientTeleportationManager.moveClientEntityAcrossDimension(possessedEntity, (ClientWorld)player.getEntityWorld(), player.getEntityPos());
         }
      }
   }

   public static void onPlayerTeleportedServer(ServerPlayerEntity player) {
      if (isRequiemPresent) {
         MobEntity possessedEntity = getPossessedEntity(player);
         if (possessedEntity != null && possessedEntity.getEntityWorld() != player.getServerWorld()) {
            Helper.log("Move Requiem Posessed Entity at Server");
            ServerTeleportationManager.of(player.server)
               .changeEntityDimension(possessedEntity, player.getServerWorld().getRegistryKey(), McHelper.getEyePos(player), false);
         }
      }
   }
}
