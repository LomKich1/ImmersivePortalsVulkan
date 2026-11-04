package qouteall.imm_ptl.core.portal.custom_portal_gen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.function.Function;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.SimpleRegistry;
import qouteall.imm_ptl.core.McHelper;

public abstract class PortalGenTrigger {
   public static final Registry<MapCodec<? extends PortalGenTrigger>> codecRegistry = new SimpleRegistry(
      RegistryKey.ofRegistry(McHelper.newResourceLocation("imm_ptl:custom_portal_gen_trigger")), Lifecycle.stable()
   );
   public static final Codec<PortalGenTrigger> triggerCodec = PortalGenTrigger.codecRegistry
      .getCodec()
      .dispatchStable(PortalGenTrigger::getCodec, Function.identity());
   public static final MapCodec<PortalGenTrigger.UseItemTrigger> useItemTriggerCodec = RecordCodecBuilder.mapCodec(
      instance -> instance.group(
            Registries.ITEM.getCodec().fieldOf("item").forGetter(o -> o.item), Codec.BOOL.optionalFieldOf("consume", false).forGetter(o -> o.consume)
         )
         .apply(instance, instance.stable(PortalGenTrigger.UseItemTrigger::new))
   );
   public static final MapCodec<PortalGenTrigger.ThrowItemTrigger> throwItemTriggerCodec = RecordCodecBuilder.mapCodec(
      instance -> instance.group(Registries.ITEM.getCodec().fieldOf("item").forGetter(o -> o.item))
         .apply(instance, instance.stable(PortalGenTrigger.ThrowItemTrigger::new))
   );
   public static final MapCodec<PortalGenTrigger.ConventionalDimensionChangeTrigger> conventionalDimensionChangeCodec = MapCodec.unit(
      PortalGenTrigger.ConventionalDimensionChangeTrigger::new
   );

   public abstract MapCodec<? extends PortalGenTrigger> getCodec();

   static {
      Registry.register(codecRegistry, McHelper.newResourceLocation("imm_ptl:use_item"), useItemTriggerCodec);
      Registry.register(codecRegistry, McHelper.newResourceLocation("imm_ptl:throw_item"), throwItemTriggerCodec);
      Registry.register(
         codecRegistry,
         McHelper.newResourceLocation("imm_ptl:conventional_dimension_change"),
         PortalGenTrigger.ConventionalDimensionChangeTrigger.conventionalDimensionChangeCodec
      );
   }

   public static class ConventionalDimensionChangeTrigger extends PortalGenTrigger {
      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return conventionalDimensionChangeCodec;
      }
   }

   public static class ThrowItemTrigger extends PortalGenTrigger {
      public final Item item;

      public ThrowItemTrigger(Item item) {
         this.item = item;
      }

      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return throwItemTriggerCodec;
      }
   }

   public static class UseItemTrigger extends PortalGenTrigger {
      public final Item item;
      public final boolean consume;

      public UseItemTrigger(Item item, boolean consume) {
         this.item = item;
         this.consume = consume;
      }

      public boolean shouldConsume(ItemUsageContext context) {
         if (!this.consume) {
            return false;
         } else {
            PlayerEntity player = context.getPlayer();
            return player == null || !player.isCreative();
         }
      }

      @Override
      public MapCodec<? extends PortalGenTrigger> getCodec() {
         return useItemTriggerCodec;
      }
   }
}
