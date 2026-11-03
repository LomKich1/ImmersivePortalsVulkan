package qouteall.imm_ptl.peripheral.alternate_dimension;

import net.minecraft.block.Blocks;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry.class_6883;

public class ErrorTerrainComposition {
   public static final BlockState air = Blocks.field_10124.getDefaultState();
   public static final BlockState stone = Blocks.field_10340.getDefaultState();
   public static final BlockState water = Blocks.field_10382.getDefaultState();
   public static final RegionErrorTerrainGenerator.Composition mountain = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      if (funcValue > middle) {
         return funcValue > middle * middle ? air : stone;
      } else {
         return air;
      }
   };
   public static final RegionErrorTerrainGenerator.Composition classicalSolid = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint = getSplitPointClassical(worldY, middle, 32.0);
      return funcValue > splitPoint ? stone : air;
   };
   public static final RegionErrorTerrainGenerator.Composition classicalHollow = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint = getSplitPointClassical(worldY, middle, 32.0);
      if (funcValue > splitPoint) {
         return funcValue > splitPoint + 2.0 ? air : stone;
      } else {
         return air;
      }
   };
   public static final RegionErrorTerrainGenerator.Composition classicalWatery = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint = getSplitPointClassical(worldY, middle, 16.0);
      if (funcValue > splitPoint) {
         return (int)funcValue % 23 == 0 ? water : stone;
      } else {
         return air;
      }
   };
   public static final RegionErrorTerrainGenerator.Composition newSolid = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint1 = Math.abs(middle);
      splitPoint1 *= Math.exp(Math.abs(worldY - 64) / 32.0 - 1.0);
      splitPoint1 *= Math.max(1.0, 100.0 / Math.max(1, Math.min(worldY, 128 - worldY)));
      return funcValue > splitPoint1 ? stone : air;
   };
   public static final RegionErrorTerrainGenerator.Composition floatingSea = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint = getSplitPointClassical(worldY, middle, 16.0);
      if (funcValue > splitPoint) {
         return worldY > 63 ? water : stone;
      } else {
         return air;
      }
   };
   public static final RegionErrorTerrainGenerator.Composition treasured = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      if (worldY > 32) {
         return air;
      } else {
         double upPoint = Math.max(middle, Math.max(upMiddle, downMiddle));
         double splitPoint = MathHelper.lerp(Math.abs(worldY - 32) / 32.0, middle, worldY > 32 ? upMiddle : downMiddle);
         if (funcValue > splitPoint && (int)funcValue % 37 == 0) {
            Block randomBlock = (Block)((class_6883)Registries.BLOCK.getRandom(Random.create()).get()).comp_349();
            if (randomBlock == Blocks.field_10327) {
               randomBlock = Blocks.field_10124;
            }

            return randomBlock.getDefaultState();
         } else {
            return air;
         }
      }
   };
   public static final RegionErrorTerrainGenerator.Composition layeredHollow = (worldY, funcValue, middle, upMiddle, downMiddle, worldX, worldZ) -> {
      double splitPoint = getSplitPointClassical(worldY, middle, 32.0);
      if (funcValue > splitPoint) {
         double v = (funcValue - splitPoint) / 4.0;
         return v > Math.floor(v) + 0.5 ? air : stone;
      } else {
         return air;
      }
   };
   public static final RandomSelector<RegionErrorTerrainGenerator.Composition> selector = new RandomSelector.Builder<RegionErrorTerrainGenerator.Composition>()
      .add(25, mountain)
      .add(40, classicalSolid)
      .add(50, classicalHollow)
      .add(10, classicalWatery)
      .add(10, newSolid)
      .add(30, floatingSea)
      .add(1, treasured)
      .add(15, layeredHollow)
      .build();

   private static double getSplitPointClassical(int worldY, double middle, double unitFactor) {
      double splitPoint = middle * Math.exp(Math.abs(worldY - 64) / unitFactor);
      return splitPoint * Math.max(0.7, 30.0 / Math.max(1, Math.min(worldY, 128 - worldY)));
   }
}
