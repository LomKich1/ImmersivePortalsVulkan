package qouteall.imm_ptl.core.render;

import net.minecraft.util.Util;
import net.minecraft.util.Util.class_158;
import org.lwjgl.opengl.GL15;
import qouteall.imm_ptl.core.CHelper;

public class QueryManager {
   public static int queryStallCounter = 0;
   public static boolean isQuerying = false;
   private static int idQueryObject = -1;

   public static boolean renderAndGetDoesAnySamplePass(Runnable renderingFunc) {
      if (idQueryObject == -1) {
         idQueryObject = GL15.glGenQueries();
         CHelper.checkGlError();
      }

      if (Util.getOperatingSystem() == class_158.field_1137) {
         return renderAndGetSampleCountPassed(renderingFunc) > 0;
      } else {
         assert !isQuerying;

         GL15.glBeginQuery(35887, idQueryObject);
         isQuerying = true;
         renderingFunc.run();
         GL15.glEndQuery(35887);
         isQuerying = false;
         int result = GL15.glGetQueryObjecti(idQueryObject, 34918);
         queryStallCounter++;
         return result != 0;
      }
   }

   public static int renderAndGetSampleCountPassed(Runnable renderingFunc) {
      assert !isQuerying;

      if (idQueryObject == -1) {
         idQueryObject = GL15.glGenQueries();
         CHelper.checkGlError();
      }

      GL15.glBeginQuery(35092, idQueryObject);
      isQuerying = true;
      renderingFunc.run();
      GL15.glEndQuery(35092);
      isQuerying = false;
      int result = GL15.glGetQueryObjecti(idQueryObject, 34918);
      queryStallCounter++;
      return result;
   }
}
