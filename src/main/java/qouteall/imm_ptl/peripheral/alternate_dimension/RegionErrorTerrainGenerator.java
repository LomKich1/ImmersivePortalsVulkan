package qouteall.imm_ptl.peripheral.alternate_dimension;

import java.util.Arrays;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.world.biome.source.SeedMixer;

public class RegionErrorTerrainGenerator {
   private int regionX;
   private int regionZ;
   private FormulaGenerator.TriNumFunction expression;
   private double middle;
   private double upMiddle;
   private double downMiddle;
   RegionErrorTerrainGenerator.Composition composition;

   public RegionErrorTerrainGenerator(int regionX_, int regionZ_, long seed) {
      this.regionX = regionX_;
      this.regionZ = regionZ_;
      this.initExpression(seed);
      this.middle = this.calcMiddle(0.4, 0.5);
      this.upMiddle = this.calcMiddle(1.0, 1.0);
      this.downMiddle = this.calcMiddle(0.0, 0.0);
   }

   private void initExpression(long seed) {
      long realSeed = SeedMixer.mixSeed(seed, SeedMixer.mixSeed(this.regionX, this.regionZ));
      Random random = new Random(realSeed);
      this.expression = FormulaGenerator.newGetRandomTriCompositeExpression(random, 3);
      this.composition = ErrorTerrainComposition.selector.select(random);
   }

   private double calcMiddle(double lowerHeight, double upperHeight) {
      double[] arr = new double[8];
      double zero = 0.2;
      double one = 0.8;
      arr[0] = this.expression.eval(zero, lowerHeight, zero);
      arr[1] = this.expression.eval(zero, lowerHeight, one);
      arr[2] = this.expression.eval(zero, upperHeight, zero);
      arr[3] = this.expression.eval(zero, upperHeight, one);
      arr[4] = this.expression.eval(one, lowerHeight, zero);
      arr[5] = this.expression.eval(one, lowerHeight, one);
      arr[6] = this.expression.eval(one, upperHeight, zero);
      arr[7] = this.expression.eval(one, upperHeight, one);
      Arrays.sort(arr);
      return arr[4];
   }

   private double calc(int worldX, int worldY, int worldZ) {
      int a = 64;
      int regionStartX = this.regionX * a;
      int regionStartZ = this.regionZ * a;
      return this.expression.eval((double)(worldX - regionStartX) / a, worldY / 128.0, (double)(worldZ - regionStartZ) / a);
   }

   public BlockState getBlockComposition(int worldX, int worldY, int worldZ) {
      if (worldY >= 128) {
         return ErrorTerrainComposition.air;
      } else {
         double currValue = this.calc(worldX, worldY, worldZ);
         return this.composition.generate(worldY, currValue, this.middle, this.upMiddle, this.downMiddle, worldX, worldZ);
      }
   }

   public interface Composition {
      BlockState generate(int var1, double var2, double var4, double var6, double var8, int var10, int var11);
   }
}
