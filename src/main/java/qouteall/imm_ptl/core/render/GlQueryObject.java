package qouteall.imm_ptl.core.render;

import java.util.ArrayList;
import net.minecraft.util.Util;
import net.minecraft.util.Util.OperatingSystem;
import org.apache.commons.lang3.Validate;
import org.lwjgl.opengl.GL15;

public class GlQueryObject {
   private int idQueryObject = -1;
   private boolean isQuerying = false;
   private boolean hasResult = false;
   private static final ArrayList<GlQueryObject> queryObjects = new ArrayList<>();

   public GlQueryObject(int handle) {
      this.idQueryObject = handle;
   }

   public void performQueryAnySamplePassed(Runnable renderingFunc) {
      if (Util.getOperatingSystem() == OperatingSystem.OSX) {
         this.performQuerySampleNumPassed(renderingFunc);
      } else {
         this.performQuery(renderingFunc, 35887);
      }
   }

   public void performQuerySampleNumPassed(Runnable renderingFunc) {
      this.performQuery(renderingFunc, 35092);
   }

   private void performQuery(Runnable renderingFunc, int glQueryType) {
      Validate.isTrue(this.isValid());
      Validate.isTrue(!this.isQuerying);
      GL15.glBeginQuery(glQueryType, this.idQueryObject);
      this.isQuerying = true;
      renderingFunc.run();
      GL15.glEndQuery(glQueryType);
      this.isQuerying = false;
      this.hasResult = true;
   }

   public boolean fetchQueryResult() {
      Validate.isTrue(this.isValid());
      Validate.isTrue(this.hasResult);
      int result = GL15.glGetQueryObjecti(this.idQueryObject, 34918);
      return result != 0;
   }

   private void dispose() {
      if (this.idQueryObject != -1) {
         GL15.glDeleteQueries(this.idQueryObject);
         this.idQueryObject = -1;
      }
   }

   public boolean isValid() {
      return this.idQueryObject != -1;
   }

   private void reset() {
      this.hasResult = false;
   }

   private static void prepareQueryObjects() {
      int[] buf = new int[500];
      GL15.glGenQueries(buf);

      for (int id : buf) {
         queryObjects.add(new GlQueryObject(id));
      }
   }

   public static GlQueryObject acquireQueryObject() {
      if (queryObjects.isEmpty()) {
         prepareQueryObjects();
      }

      return queryObjects.remove(queryObjects.size() - 1);
   }

   public static void returnQueryObject(GlQueryObject obj) {
      obj.reset();
      if (queryObjects.size() > 1500) {
         obj.dispose();
      } else {
         queryObjects.add(obj);
      }
   }
}
