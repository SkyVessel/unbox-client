package dev.unbox.unpvp.bootstrap;
import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import java.util.Map;
@IFMLLoadingPlugin.SortingIndex(1001)
@IFMLLoadingPlugin.MCVersion("1.8.9")
@IFMLLoadingPlugin.TransformerExclusions({"dev.unbox.unpvp.bootstrap."})
public final class UiBootstrap implements IFMLLoadingPlugin {
    public UiBootstrap(){
        if(System.getProperty("os.name","").startsWith("Mac")&&System.getProperty("org.lwjgl.opengl.Display.enableHighDPI")==null)
            System.setProperty("org.lwjgl.opengl.Display.enableHighDPI","true");
    }
    public String[] getASMTransformerClass(){return new String[]{"dev.unbox.unpvp.bootstrap.UiFrameLimitTransformer","dev.unbox.unpvp.bootstrap.RetinaTransformer","dev.unbox.unpvp.bootstrap.LoadTransformer"};}
    public String getModContainerClass(){return null;}
    public String getSetupClass(){return null;}
    public void injectData(Map<String,Object> data){}
    public String getAccessTransformerClass(){return null;}
}
