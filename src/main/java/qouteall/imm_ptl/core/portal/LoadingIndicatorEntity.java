package qouteall.imm_ptl.core.portal;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.Entity.RemovalReason;
import net.minecraft.entity.EntityType.Builder;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import qouteall.imm_ptl.core.McHelper;
import qouteall.q_misc_util.my_util.IntBox;
import qouteall.q_misc_util.platform.Platform;

public class LoadingIndicatorEntity extends Entity {
   public static final EntityType<LoadingIndicatorEntity> entityType = Platform.get()
      .forceTrackedVelocityUpdates(
         Builder.create(LoadingIndicatorEntity::new, SpawnGroup.MISC).dimensions(1.0F, 1.0F).makeFireImmune().maxTrackingRange(6).trackingTickInterval(20),
         true
      )
      .build(RegistryKey.of(RegistryKeys.ENTITY_TYPE, McHelper.newResourceLocation("immersive_portals", "loading_indicator")));
   private static final TrackedData<Text> TEXT = DataTracker.registerData(LoadingIndicatorEntity.class, TrackedDataHandlerRegistry.TEXT_COMPONENT);
   private static final TrackedData<BlockPos> BOX_LOW_POS = DataTracker.registerData(LoadingIndicatorEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
   private static final TrackedData<BlockPos> BOX_HIGH_POS = DataTracker.registerData(LoadingIndicatorEntity.class, TrackedDataHandlerRegistry.BLOCK_POS);
   public boolean isValid = false;

   public LoadingIndicatorEntity(EntityType type, World world) {
      super(type, world);
   }

   public void tick() {
      super.tick();
      if (this.getEntityWorld().isClient()) {
         this.tickClient();
      } else if (!this.isValid) {
         this.remove(RemovalReason.KILLED);
      }
   }

   @Environment(EnvType.CLIENT)
   private void tickClient() {
      this.addParticles();
      if (this.age > 40) {
         ClientPlayerEntity player = MinecraftClient.getInstance().player;
         if (player != null && player.getEntityWorld() == this.getEntityWorld() && player.getEntityPos().squaredDistanceTo(this.getEntityPos()) < 256.0) {
            this.showMessageClient();
         }
      }
   }

   @Environment(EnvType.CLIENT)
   private void addParticles() {
      int num = this.age < 100 ? 50 : 20;
      IntBox box = this.getBox();
      BlockPos size = box.getSize();
      Random random = this.getEntityWorld().getRandom();

      for (int i = 0; i < num; i++) {
         Vec3d p = new Vec3d(random.nextDouble(), random.nextDouble(), random.nextDouble()).multiply(Vec3d.of(size)).add(Vec3d.of(box.l));
         double speedMultiplier = 20.0;
         double vx = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         double vy = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         double vz = speedMultiplier * (random.nextFloat() - 0.5) * 0.5;
         this.getEntityWorld().addParticleClient(ParticleTypes.PORTAL, p.x, p.y, p.z, vx, vy, vz);
      }
   }

   protected void initDataTracker(net.minecraft.entity.data.DataTracker.Builder builder) {
      builder.add(TEXT, Text.literal("Loading..."));
      builder.add(BOX_LOW_POS, BlockPos.ORIGIN);
      builder.add(BOX_HIGH_POS, BlockPos.ORIGIN);
   }

   public boolean damage(ServerWorld world, DamageSource source, float amount) {
      return false;
   }

   protected void readCustomData(ReadView view) {
   }

   protected void writeCustomData(WriteView view) {
   }

   public void inform(Text str) {
      this.setText(str);
   }

   public void setText(Text str) {
      this.getDataTracker().set(TEXT, str);
   }

   public Text getText() {
      return (Text)this.getDataTracker().get(TEXT);
   }

   public void setBox(IntBox box) {
      this.getDataTracker().set(BOX_LOW_POS, box.l);
      this.getDataTracker().set(BOX_HIGH_POS, box.h);
   }

   public IntBox getBox() {
      return new IntBox((BlockPos)this.getDataTracker().get(BOX_LOW_POS), (BlockPos)this.getDataTracker().get(BOX_HIGH_POS));
   }

   @Environment(EnvType.CLIENT)
   private void showMessageClient() {
      InGameHud inGameHud = MinecraftClient.getInstance().inGameHud;
      inGameHud.setOverlayMessage(this.getText(), false);
   }
}
