package qouteall.q_misc_util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;
import java.nio.file.Path;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.World;
import org.apache.commons.lang3.Validate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import qouteall.q_misc_util.ducks.IEMinecraftServer_Misc;
import qouteall.q_misc_util.mixin.IELevelStorageAccess_Misc;
import qouteall.q_misc_util.platform.Platform;

public class MiscHelper {
   private static final Logger LOGGER = LogManager.getLogger();
   public static final Gson gson;

   @Environment(EnvType.CLIENT)
   public static void executeOnRenderThread(Runnable runnable) {
      MinecraftClient client = MinecraftClient.getInstance();
      if (client.isOnThread()) {
         try {
            runnable.run();
         } catch (Exception var3) {
            LOGGER.error("Processing task on render thread", var3);
         }
      } else {
         client.execute(runnable);
      }
   }

   @Deprecated
   public static MinecraftServer getServer() {
      return MiscGlobals.refMinecraftServer.get();
   }

   public static void executeOnServerThread(MinecraftServer server, Runnable runnable) {
      if (server.isOnThread()) {
         try {
            runnable.run();
         } catch (Exception var3) {
            LOGGER.error("Processing task on server thread", var3);
         }
      } else {
         server.execute(runnable);
      }
   }

   public static boolean isDedicatedServer() {
      return Platform.get().isDedicatedServer();
   }

   public static Path getWorldSavingDirectory(MinecraftServer server) {
      Validate.notNull(server);
      return ((IELevelStorageAccess_Misc)((IEMinecraftServer_Misc)server).ip_getStorageSource()).ip_getLevelPath().path();
   }

   static {
      GsonBuilder gsonBuilder = new GsonBuilder();
      gsonBuilder.setPrettyPrinting();
      gsonBuilder.registerTypeAdapter((new TypeToken<RegistryKey<World>>() {}).getType(), new MiscHelper.DimensionIDJsonAdapter());
      gson = gsonBuilder.create();
   }

   private static class DimensionIDJsonAdapter implements JsonSerializer<RegistryKey<World>>, JsonDeserializer<RegistryKey<World>> {
      public RegistryKey<World> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
         String str = json.getAsString();
         return Helper.dimIdToKey(str);
      }

      public JsonElement serialize(RegistryKey<World> src, Type typeOfSrc, JsonSerializationContext context) {
         return new JsonPrimitive(src.getValue().toString());
      }
   }
}
