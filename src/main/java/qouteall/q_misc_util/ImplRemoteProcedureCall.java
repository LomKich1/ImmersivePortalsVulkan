package qouteall.q_misc_util;

import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.listener.ClientCommonPacketListener;
import net.minecraft.network.listener.ServerCommonPacketListener;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.CustomPayload.Id;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.Validate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.CountDownInt;
import qouteall.q_misc_util.my_util.DQuaternion;
import qouteall.q_misc_util.platform.PlatformNetworking;

public class ImplRemoteProcedureCall {
   private static final Logger LOGGER = LogUtils.getLogger();
   private static final CountDownInt LOGGING_LIMIT = new CountDownInt(100);
   private static final CountDownInt ERROR_MESSAGE_LIMIT = new CountDownInt(10);
   public static final Gson gson = MiscHelper.gson;
   private static final ConcurrentHashMap<String, Method> methodCache = new ConcurrentHashMap<>();
   private static final ImmutableMap<Class, BiConsumer<RegistryByteBuf, Object>> serializerMap = ImmutableMap.<Class, BiConsumer<RegistryByteBuf, Object>>builder()
      .put(Identifier.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeIdentifier((Identifier)o))
      .put(RegistryKey.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeIdentifier(((RegistryKey)o).getValue()))
      .put(BlockPos.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeBlockPos((BlockPos)o))
      .put(Vec3d.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> {
         Vec3d vec = (Vec3d)o;
         buf.writeDouble(vec.x);
         buf.writeDouble(vec.y);
         buf.writeDouble(vec.z);
      })
      .put(UUID.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeUuid((UUID)o))
      .put(Block.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> serializeByCodec(buf, Registries.BLOCK.getCodec(), o))
      .put(Item.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> serializeByCodec(buf, Registries.ITEM.getCodec(), o))
      .put(BlockState.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> serializeByCodec(buf, BlockState.CODEC, o))
      .put(ItemStack.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> serializeByCodec(buf, ItemStack.CODEC, o))
      .put(NbtCompound.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeNbt((NbtCompound)o))
      .put(Text.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC.encode(buf, (Text)o))
      .put(DQuaternion.class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> {
         DQuaternion dQuaternion = (DQuaternion)o;
         buf.writeDouble(dQuaternion.x);
         buf.writeDouble(dQuaternion.y);
         buf.writeDouble(dQuaternion.z);
         buf.writeDouble(dQuaternion.w);
      })
      .put(byte[].class, (BiConsumer<RegistryByteBuf, Object>)(buf, o) -> buf.writeByteArray((byte[])o))
      .build();
   private static final ImmutableMap<java.lang.reflect.Type, Function<RegistryByteBuf, Object>> deserializerMap = ImmutableMap.<Class, BiConsumer<RegistryByteBuf, Object>>builder()
      .put(Identifier.class, (Function<RegistryByteBuf, Object>)buf -> buf.readIdentifier())
      .put((new TypeToken<RegistryKey<World>>() {}).getType(), (Function<RegistryByteBuf, Object>)buf -> RegistryKey.of(RegistryKeys.WORLD, buf.readIdentifier()))
      .put((new TypeToken<RegistryKey<Biome>>() {}).getType(), (Function<RegistryByteBuf, Object>)buf -> RegistryKey.of(RegistryKeys.BIOME, buf.readIdentifier()))
      .put(BlockPos.class, (Function<RegistryByteBuf, Object>)buf -> buf.readBlockPos())
      .put(Vec3d.class, (Function<RegistryByteBuf, Object>)buf -> new Vec3d(buf.readDouble(), buf.readDouble(), buf.readDouble()))
      .put(UUID.class, (Function<RegistryByteBuf, Object>)buf -> buf.readUuid())
      .put(Block.class, (Function<RegistryByteBuf, Object>)buf -> deserializeByCodec(buf, Registries.BLOCK.getCodec()))
      .put(Item.class, (Function<RegistryByteBuf, Object>)buf -> deserializeByCodec(buf, Registries.ITEM.getCodec()))
      .put(BlockState.class, (Function<RegistryByteBuf, Object>)buf -> deserializeByCodec(buf, BlockState.CODEC))
      .put(ItemStack.class, (Function<RegistryByteBuf, Object>)buf -> deserializeByCodec(buf, ItemStack.CODEC))
      .put(NbtCompound.class, (Function<RegistryByteBuf, Object>)buf -> buf.readNbt())
      .put(Text.class, TextCodecs.UNLIMITED_REGISTRY_PACKET_CODEC::decode)
      .put(DQuaternion.class, (Function<RegistryByteBuf, Object>)buf -> new DQuaternion(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble()))
      .put(byte[].class, (Function<RegistryByteBuf, Object>)buf -> buf.readByteArray())
      .build();

   public static void init() {
      PlatformNetworking.get().registerPlayC2S(ImplRemoteProcedureCall.C2SRPCPayload.TYPE, ImplRemoteProcedureCall.C2SRPCPayload.CODEC);
      PlatformNetworking.get().registerPlayS2C(ImplRemoteProcedureCall.S2CRPCPayload.TYPE, ImplRemoteProcedureCall.S2CRPCPayload.CODEC);
      PlatformNetworking.get().registerServerReceiver(ImplRemoteProcedureCall.C2SRPCPayload.TYPE, ImplRemoteProcedureCall.C2SRPCPayload::handle);
   }

   @Environment(EnvType.CLIENT)
   public static void initClient() {
      PlatformNetworking.get().registerClientReceiver(ImplRemoteProcedureCall.S2CRPCPayload.TYPE, ImplRemoteProcedureCall.S2CRPCPayload::handle);
   }

   private static Object deserializeByCodec(RegistryByteBuf buf, Codec codec) {
      String jsonString = buf.readString();
      JsonElement jsonElement = JsonParser.parseString(jsonString);
      return codec.parse(JsonOps.INSTANCE, jsonElement).getOrThrow();
   }

   private static Object deserializeArgument(RegistryByteBuf buf, java.lang.reflect.Type type) {
      Function<RegistryByteBuf, Object> deserializer = (Function<RegistryByteBuf, Object>)deserializerMap.get(type);
      if (deserializer == null) {
         String json = buf.readString();
         return gson.fromJson(json, type);
      } else {
         return deserializer.apply(buf);
      }
   }

   private static void serializeArgument(RegistryByteBuf buf, Object object) {
      BiConsumer<RegistryByteBuf, Object> serializer = (BiConsumer<RegistryByteBuf, Object>)serializerMap.get(object.getClass());
      if (serializer == null) {
         serializer = serializerMap.entrySet()
            .stream()
            .filter(e -> ((Class)e.getKey()).isAssignableFrom(object.getClass()))
            .findFirst()
            .map(Entry::getValue)
            .orElse(null);
      }

      if (serializer == null) {
         String json = gson.toJson(object);
         buf.writeString(json);
      } else {
         serializer.accept(buf, object);
      }
   }

   private static void serializeByCodec(PacketByteBuf buf, Codec codec, Object object) {
      JsonElement result = (JsonElement)codec.encodeStart(JsonOps.INSTANCE, object).getOrThrow();
      String jsonString = gson.toJson(result);
      buf.writeString(jsonString);
   }

   @Environment(EnvType.CLIENT)
   public static Packet<ServerCommonPacketListener> createC2SPacket(String methodPath, Object... arguments) {
      return PlatformNetworking.get().createC2SPacket(new ImplRemoteProcedureCall.C2SRPCPayload(true, methodPath, null, List.of(arguments)));
   }

   public static Packet<ClientCommonPacketListener> createS2CPacket(String methodPath, Object... arguments) {
      return PlatformNetworking.get().createS2CPacket(new ImplRemoteProcedureCall.S2CRPCPayload(true, methodPath, null, List.of(arguments)));
   }

   @Environment(EnvType.CLIENT)
   private static void clientTellFailure() {
      MinecraftClient.getInstance()
         .inGameHud
         .getChatHud()
         .addMessage(Text.literal("The client failed to process a packet from server. See the log for details.").formatted(Formatting.RED));
   }

   private static void serverTellFailure(ServerPlayerEntity player) {
      player.sendMessage(Text.literal("The server failed to process a packet sent from client.").formatted(Formatting.RED));
   }

   private static Method getMethodByPath(String methodPath) {
      Method result = methodCache.get(methodPath);
      if (result != null) {
         return result;
      } else {
         Method method = findMethodByPath(methodPath);
         Validate.notNull(method);
         methodCache.put(methodPath, method);
         return method;
      }
   }

   private static Method findMethodByPath(String methodPath) {
      int lastDotIndex = methodPath.lastIndexOf(46);
      Validate.isTrue(lastDotIndex != -1);
      String classPath = methodPath.substring(0, lastDotIndex);
      String methodName = methodPath.substring(lastDotIndex + 1);
      if (!classPath.contains("RemoteCallable")) {
         throw new RuntimeException("The class path must contain \"RemoteCallable\"");
      } else {
         Class<?> aClass;
         try {
            aClass = Class.forName(classPath);
         } catch (ClassNotFoundException var10) {
            int dotIndex = classPath.lastIndexOf(46);
            if (dotIndex == -1) {
               throw new RuntimeException("Cannot find class " + classPath, var10);
            }

            String newClassPath = classPath.substring(0, dotIndex) + "$" + classPath.substring(dotIndex + 1);

            try {
               aClass = Class.forName(newClassPath);
            } catch (ClassNotFoundException var9) {
               throw new RuntimeException("Cannot find class " + classPath, var10);
            }
         }

         return Arrays.stream(aClass.getMethods())
            .filter(m -> m.getName().equals(methodName))
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Cannot find method " + methodPath + " . If it's a private method, make it public."));
      }
   }

   public record C2SRPCPayload(boolean deserializeSuccess, @Nullable String methodPath, @Nullable Method method, @Nullable List<Object> args)
      implements CustomPayload {
      public static final Id<ImplRemoteProcedureCall.C2SRPCPayload> TYPE = new Id(McHelper.newResourceLocation("iportal:remote_c2s"));
      public static final PacketCodec<RegistryByteBuf, ImplRemoteProcedureCall.C2SRPCPayload> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImplRemoteProcedureCall.C2SRPCPayload::read
      );

      public static ImplRemoteProcedureCall.C2SRPCPayload read(RegistryByteBuf buf) {
         String methodPath = null;

         try {
            methodPath = buf.readString();
            Method method = ImplRemoteProcedureCall.getMethodByPath(methodPath);
            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            List<Object> args = new ArrayList<>();

            for (int i = 1; i < genericParameterTypes.length; i++) {
               java.lang.reflect.Type parameterType = genericParameterTypes[i];
               Object obj = ImplRemoteProcedureCall.deserializeArgument(buf, parameterType);
               args.add(obj);
            }

            return new ImplRemoteProcedureCall.C2SRPCPayload(true, methodPath, method, args);
         } catch (Exception var8) {
            if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.LOGGER.error("Failed to parse remote procedure call {}", methodPath, var8);
            }

            return new ImplRemoteProcedureCall.C2SRPCPayload(false, methodPath, null, null);
         }
      }

      public void write(RegistryByteBuf buf) {
         Validate.notNull(this.args, "args must not be null", new Object[0]);
         Validate.notNull(this.methodPath, "methodPath must not be null", new Object[0]);
         buf.writeString(this.methodPath);

         for (Object arg : this.args) {
            ImplRemoteProcedureCall.serializeArgument(buf, arg);
         }
      }

      public void handle(ServerPlayerEntity player) {
         if (!this.deserializeSuccess) {
            if (ImplRemoteProcedureCall.ERROR_MESSAGE_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.serverTellFailure(player);
            }
         } else {
            Validate.notNull(this.args, "args must not be null", new Object[0]);
            Validate.notNull(this.method, "method must not be null", new Object[0]);

            try {
               Object[] argArray = new Object[this.args.size() + 1];
               argArray[0] = player;

               for (int i = 0; i < this.args.size(); i++) {
                  argArray[i + 1] = this.args.get(i);
               }

               this.method.invoke(null, argArray);
            } catch (Exception var4) {
               if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
                  ImplRemoteProcedureCall.LOGGER.error("Failed to invoke remote procedure call {} {}", new Object[]{this.methodPath, player, var4});
                  ImplRemoteProcedureCall.serverTellFailure(player);
               }
            }
         }
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }

   public record S2CRPCPayload(boolean deserializeSuccess, @Nullable String methodPath, @Nullable Method method, @Nullable List<Object> args)
      implements CustomPayload {
      public static final Id<ImplRemoteProcedureCall.S2CRPCPayload> TYPE = new Id(McHelper.newResourceLocation("iportal:remote_s2c"));
      public static final PacketCodec<RegistryByteBuf, ImplRemoteProcedureCall.S2CRPCPayload> CODEC = PacketCodec.ofStatic(
         (b, p) -> p.write(b), ImplRemoteProcedureCall.S2CRPCPayload::read
      );

      public static ImplRemoteProcedureCall.S2CRPCPayload read(RegistryByteBuf buf) {
         String methodPath = null;

         try {
            methodPath = buf.readString();
            Method method = ImplRemoteProcedureCall.getMethodByPath(methodPath);
            java.lang.reflect.Type[] genericParameterTypes = method.getGenericParameterTypes();
            List<Object> args = new ArrayList<>();

            for (java.lang.reflect.Type parameterType : genericParameterTypes) {
               Object obj = ImplRemoteProcedureCall.deserializeArgument(buf, parameterType);
               args.add(obj);
            }

            return new ImplRemoteProcedureCall.S2CRPCPayload(true, methodPath, method, args);
         } catch (Exception var10) {
            if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.LOGGER.error("Failed to parse remote procedure call {}", methodPath, var10);
            }

            return new ImplRemoteProcedureCall.S2CRPCPayload(false, methodPath, null, null);
         }
      }

      public void write(RegistryByteBuf buf) {
         Validate.notNull(this.args, "args must not be null", new Object[0]);
         Validate.notNull(this.methodPath, "methodPath must not be null", new Object[0]);
         buf.writeString(this.methodPath);

         for (Object arg : this.args) {
            ImplRemoteProcedureCall.serializeArgument(buf, arg);
         }
      }

      @Environment(EnvType.CLIENT)
      public void handle(MinecraftClient client) {
         if (!this.deserializeSuccess) {
            if (ImplRemoteProcedureCall.ERROR_MESSAGE_LIMIT.tryDecrement()) {
               ImplRemoteProcedureCall.clientTellFailure();
            }
         } else {
            Validate.notNull(this.args, "args must not be null", new Object[0]);
            Validate.notNull(this.method, "method must not be null", new Object[0]);

            try {
               Object[] argArray = this.args.toArray(new Object[0]);
               this.method.invoke(null, argArray);
            } catch (Exception var3) {
               if (ImplRemoteProcedureCall.LOGGING_LIMIT.tryDecrement()) {
                  ImplRemoteProcedureCall.LOGGER.error("Failed to invoke remote procedure call {}", this.methodPath, var3);
                  ImplRemoteProcedureCall.clientTellFailure();
               }
            }
         }
      }

      @NotNull
      public Id<? extends CustomPayload> getId() {
         return TYPE;
      }
   }
}
