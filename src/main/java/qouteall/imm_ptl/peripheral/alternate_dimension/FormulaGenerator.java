package qouteall.imm_ptl.peripheral.alternate_dimension;

import it.unimi.dsi.fastutil.doubles.DoubleList;
import java.util.Random;
import java.util.function.Function;

public class FormulaGenerator {
   private static RandomSelector<Function<Random, FormulaGenerator.UniNumFunction>> uniFuncSelector;
   private static RandomSelector<FormulaGenerator.TriNumFunction> triFuncSelector;
   private static final RandomSelector<FormulaGenerator.TriToTriFuncCPS> shuffleFunSelector = new RandomSelector.Builder<FormulaGenerator.TriToTriFuncCPS>()
      .add(1, (x, y, z, c) -> c.eval(x, y, z))
      .add(1, (x, y, z, c) -> c.eval(x, z, y))
      .add(1, (x, y, z, c) -> c.eval(y, z, x))
      .add(1, (x, y, z, c) -> c.eval(y, x, z))
      .add(1, (x, y, z, c) -> c.eval(z, x, y))
      .add(1, (x, y, z, c) -> c.eval(z, y, x))
      .build();

   public static void init() {
      initUniFuncSelector();
      initTriFuncSelector();
   }

   private static void initUniFuncSelector() {
      RandomSelector.Builder<Function<Random, FormulaGenerator.UniNumFunction>> builder = new RandomSelector.Builder<>();
      Function<Function<Double, FormulaGenerator.UniNumFunction>, Function<Random, FormulaGenerator.UniNumFunction>> applyRandomArg = arged -> random -> arged.apply(
         random.nextDouble()
      );
      Function<FormulaGenerator.UniNumFunction, Function<Random, FormulaGenerator.UniNumFunction>> noRandomArg = func -> random -> func;
      builder.add(30, noRandomArg.apply(x -> x));
      builder.add(30, noRandomArg.apply(x -> -x));
      builder.add(30, noRandomArg.apply(x -> x * x));
      builder.add(30, noRandomArg.apply(x -> x * x * x));
      builder.add(10, noRandomArg.apply(x -> Math.max(x, 0.0)));
      builder.add(7, noRandomArg.apply(x -> Math.sin(x * 3.0)));
      builder.add(7, noRandomArg.apply(x -> Math.cos(x * 7.0)));
      builder.add(10, noRandomArg.apply(x -> Math.exp(x * 3.0)));
      builder.add(10, noRandomArg.apply(x -> Math.log(Math.abs(x) + 1.0)));
      builder.add(5, noRandomArg.apply(x -> Math.cosh(x)));
      builder.add(10, noRandomArg.apply(x -> Math.abs(x)));
      builder.add(5, noRandomArg.apply(x -> Math.round(x)));
      builder.add(10, noRandomArg.apply(x -> Math.sqrt(Math.abs(x))));
      builder.add(10, noRandomArg.apply(x -> weirdSwap(x)));
      builder.add(30, applyRandomArg.apply(arg -> x -> x * arg));
      builder.add(30, applyRandomArg.apply(arg -> x -> x / Math.max(arg, 0.1)));
      builder.add(10, applyRandomArg.apply(arg -> x -> Math.max(x, arg)));
      builder.add(10, applyRandomArg.apply(arg -> x -> x + arg));
      builder.add(7, applyRandomArg.apply(arg -> x -> Math.floor(x * arg * 23.0)));
      builder.add(7, applyRandomArg.apply(arg -> x -> Math.floor(x * arg)));
      builder.add(10, applyRandomArg.apply(arg -> x -> weirdAnd(arg, x)));
      builder.add(10, applyRandomArg.apply(arg -> x -> weirdXor(arg, x)));
      uniFuncSelector = builder.build();
   }

   private static int toInt(double d) {
      return (int)(d * 256.0) + 256;
   }

   private static double fromInt(int d) {
      return (d - 256.0) / 256.0;
   }

   private static double weirdAnd(double a, double b) {
      return fromInt(toInt(a) & toInt(b));
   }

   private static double weirdXor(double a, double b) {
      return fromInt(toInt(a) ^ toInt(b));
   }

   private static double weirdSwap(double a) {
      int i = toInt(a);
      int r = (i & 15) << 4 | (i & 240) >> 4;
      return fromInt(r);
   }

   private static void initTriFuncSelector() {
      RandomSelector.Builder<FormulaGenerator.TriNumFunction> builder = new RandomSelector.Builder<>();
      builder.add(50, (x, y, z) -> x + y + z);
      builder.add(50, (x, y, z) -> x * y * z);
      builder.add(10, (x, y, z) -> x * y + z);
      builder.add(10, (x, y, z) -> x + y * z);
      builder.add(10, (x, y, z) -> x * z + y);
      builder.add(10, (x, y, z) -> x + y + z);
      builder.add(10, (x, y, z) -> x * x + y * y + z * z);
      builder.add(10, (x, y, z) -> x + y * y + z * z);
      builder.add(10, (x, y, z) -> x * x + y + z * z);
      builder.add(10, (x, y, z) -> x * x + y * y + z);
      builder.add(10, (x, y, z) -> -x * x + y * y + z * z);
      builder.add(10, (x, y, z) -> x * x - y * y + z * z);
      builder.add(10, (x, y, z) -> x * x + y * y - z * z);
      builder.add(10, (x, y, z) -> (x + y + z) * (x + y + z));
      builder.add(10, (x, y, z) -> (x + y + z) * (x + y - z));
      builder.add(10, (x, y, z) -> x * x * x * y * z);
      builder.add(5, (x, y, z) -> Math.pow(y, x + z));
      builder.add(5, (x, y, z) -> Math.pow(y, x - z));
      builder.add(5, (x, y, z) -> Math.pow(x * z, y));
      builder.add(10, (x, y, z) -> Math.log(Math.abs(x + y + z) + 0.5));
      builder.add(10, (x, y, z) -> Math.log(Math.abs(x - y * z) + 0.5));
      triFuncSelector = builder.build();
   }

   public static FormulaGenerator.TriNumFunction newGetRandomTriCompositeExpression(Random random, int nestLayer) {
      if (nestLayer == 0) {
         return shuffleFunSelector.select(random).finish(triFuncSelector.select(random));
      } else {
         FormulaGenerator.TriToTriFuncCPS curr = newGetComplexTriNumFunc2(random);
         curr = curr.then(newGetComplexTriNumFunc1(random));
         return curr.finish(newGetRandomTriCompositeExpression(random, nestLayer - 1));
      }
   }

   public static FormulaGenerator.TriToTriFuncCPS newGetComplexTriNumFunc1(Random random) {
      FormulaGenerator.TriNumFunction f1 = triFuncSelector.select(random);
      FormulaGenerator.TriNumFunction f2 = triFuncSelector.select(random);
      FormulaGenerator.TriNumFunction f3 = triFuncSelector.select(random);
      return (x, y, z, c) -> c.eval(f1.eval(x, y, z), f2.eval(x, y, z), f3.eval(x, y, z));
   }

   public static FormulaGenerator.TriToTriFuncCPS newGetComplexTriNumFunc2(Random random) {
      FormulaGenerator.UniNumFunction f1 = uniFuncSelector.select(random).apply(random);
      FormulaGenerator.UniNumFunction f2 = uniFuncSelector.select(random).apply(random);
      FormulaGenerator.UniNumFunction f3 = uniFuncSelector.select(random).apply(random);
      return (x, y, z, c) -> c.eval(f1.eval(x), f2.eval(y), f3.eval(z));
   }

   public interface TriNumFunction {
      double eval(double var1, double var3, double var5);

      default FormulaGenerator.TriNumFunction normalize() {
         DoubleList doubleList = DoubleList.of(
            new double[]{
               this.eval(0.0, 0.0, 0.0),
               this.eval(0.0, 0.0, 1.0),
               this.eval(0.0, 1.0, 0.0),
               this.eval(0.0, 1.0, 1.0),
               this.eval(1.0, 0.0, 0.0),
               this.eval(1.0, 0.0, 1.0),
               this.eval(1.0, 1.0, 0.0),
               this.eval(1.0, 1.0, 1.0)
            }
         );
         double mean = doubleList.doubleStream().sum() / 8.0;
         double S = doubleList.doubleStream().map(v -> (v - mean) * (v - mean)).sum() / 8.0;
         double deviation = Math.sqrt(S);
         return (x, y, z) -> (this.eval(x, y, z) - mean) / deviation;
      }
   }

   public interface TriToTriFuncCPS {
      double eval(double var1, double var3, double var5, FormulaGenerator.TriNumFunction var7);

      default FormulaGenerator.TriToTriFuncCPS then(FormulaGenerator.TriToTriFuncCPS other) {
         return (x, y, z, c) -> this.eval(x, y, z, (nx, ny, nz) -> other.eval(nx, ny, nz, c));
      }

      default FormulaGenerator.TriNumFunction finish(FormulaGenerator.TriNumFunction finalCont) {
         return (x, y, z) -> this.eval(x, y, z, finalCont);
      }
   }

   public interface UniNumFunction {
      double eval(double var1);
   }
}
