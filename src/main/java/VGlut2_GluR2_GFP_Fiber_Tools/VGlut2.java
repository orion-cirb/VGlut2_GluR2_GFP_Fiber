package VGlut2_GluR2_GFP_Fiber_Tools;

import java.util.HashMap;
import mcib3d.geom2.Object3DInt;

/**
 * @author hm
 */
public class VGlut2 {
    
    public Object3DInt VGlut2;
    public HashMap<String, Double> params;
    
    public VGlut2(Object3DInt VGlut2) {
        this.VGlut2 = VGlut2;
        this.params = new HashMap<>();
    }
    
    public void setParams(double gluR2, double gluR2Vol) {
        params.put("gluR2", gluR2);
        params.put("gluR2Vol", gluR2Vol);
    }
}
