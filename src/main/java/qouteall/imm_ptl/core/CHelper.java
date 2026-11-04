package qouteall.imm_ptl.core;

import qouteall.imm_ptl.core.compat.RenderBackendCompat;
import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ConfirmLinkScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.ducks.IEClientWorld;
import qouteall.imm_ptl.core.platform_specific.O_O;
import qouteall.imm_ptl.core.portal.Portal;
import qouteall.imm_ptl.core.portal.animation.StableClientTimer;
import qouteall.q_misc_util.Helper;

@Environment(EnvType.CLIENT)
public class CHelper {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static int reportedErrorNum = 0;

   public static PlayerListEntry getClientPlayerListEntry() {
      return MinecraftClient.getInstance().getNetworkHandler().getPlayerListEntry(MinecraftClient.getInstance().player.getGameProfile().id());
   }

   public static World getClientWorld(RegistryKey<World> dimension) {
      return ClientWorldLoader.getWorld(dimension);
   }

   @Nullable
   public static List<Portal> getClientGlobalPortal(World world) {
      return world instanceof ClientWorld ? ((IEClientWorld)(Object)world).ip_getGlobalPortals() : null;
   }

   public static Stream<Portal> getClientNearbyPortals(double range) {
      return IPMcHelper.getNearbyPortals(MinecraftClient.getInstance().player, range);
   }

   public static void checkGlError() {
      if (IPGlobal.doCheckGlError) {
         if (reportedErrorNum <= 100) {
            doCheckGlError();
         }
      }
   }

   public static void doCheckGlError() {
      if (!RenderBackendCompat.isRawGlAvailable()) {
         return;
      }

      int errorCode = GL11.glGetError();
      if (errorCode != 0) {
         Helper.err("OpenGL Error" + errorCode);
         new Throwable().printStackTrace();
         reportedErrorNum++;
      }
   }

   public static void printChat(String str) {
      Helper.log(str);
      printChat(Text.literal(str));
   }

   public static void printChat(Text text) {
      MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(text);
   }

   public static void openLinkConfirmScreen(Screen parent, String link) {
      MinecraftClient client = MinecraftClient.getInstance();
      client.setScreen(new ConfirmLinkScreen(result -> {
         if (result) {
            try {
               Util.getOperatingSystem().open(new URI(link));
            } catch (URISyntaxException var5) {
               var5.printStackTrace();
            }
         }

         client.setScreen(parent);
      }, link, true));
   }

   public static Vec3d getCurrentCameraPos() {
      return MinecraftClient.getInstance().gameRenderer.getCamera().getCameraPos();
   }

   public static Iterable<Entity> getWorldEntityList(World world) {
      return !(world instanceof ClientWorld clientWorld) ? (Iterable)Collections.<Entity>emptyList().iterator() : clientWorld.getEntities();
   }

   public static double getSmoothCycles(long unitTicks) {
      return (double)((float)(StableClientTimer.getStableTickTime() % unitTicks) + StableClientTimer.getStablePartialTicks()) / unitTicks;
   }

   public static void disableDepthClamp() {
      if (IPGlobal.enableClippingMechanism) {
         GL11.glDisable(34383);
      }
   }

   public static void enableDepthClamp() {
      if (IPGlobal.enableClippingMechanism) {
         GL11.glEnable(34383);
      }
   }

   @Nullable
   public static Identifier getDimensionIconPath(RegistryKey<World> dimension) {
      Identifier dimensionId = dimension.getValue();
      Identifier dimIconPath = Identifier.of(dimensionId.getNamespace(), "textures/dimension/" + dimensionId.getPath() + ".png");
      Optional<Resource> resource = MinecraftClient.getInstance().getResourceManager().getResource(dimIconPath);
      if (resource.isEmpty()) {
         LOGGER.info("Cannot load texture {}", dimIconPath);
         Identifier modIconLocation = O_O.getModIconLocation(dimensionId.getNamespace());
         if (modIconLocation == null) {
            return null;
         } else {
            Identifier modIconPath = McHelper.newResourceLocation(modIconLocation.getNamespace(), modIconLocation.getPath());
            Optional<Resource> modIconResource = MinecraftClient.getInstance().getResourceManager().getResource(modIconPath);
            if (modIconResource.isEmpty()) {
               LOGGER.info("Cannot load texture {}", modIconPath);
               return null;
            } else {
               return modIconPath;
            }
         }
      } else {
         return dimIconPath;
      }
   }
}
