package dev.unbox.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.Identifier;

/** Previous frame held only while the target prepares, then a short GPU-only crossfade. */
public final class UiTransition {
    private static final Identifier ID=Identifier.fromNamespaceAndPath("unbox","page_transition");
    private static Snapshot snapshot;private static int width,height;private static long started,requested;
    private static Screen target;
    public static boolean active(){return snapshot!=null;}
    private static boolean own(Screen s){return s instanceof SolidScreen||s instanceof UnboxScreen;}
    public static void open(Screen next){
        var mc=Minecraft.getInstance();if(next==mc.gui.screen()||(!own(next)&&!own(mc.gui.screen())))return;
        release();if(SolidTheme.reduced()||!own(mc.gui.screen())&&mc.level==null)return;
        var source=mc.gameRenderer.mainRenderTarget().getColorTexture();if(source==null)return;
        width=mc.getWindow().getWidth();height=mc.getWindow().getHeight();target=next;requested=System.nanoTime();
        snapshot=new Snapshot(width,height);mc.getTextureManager().register(ID,snapshot);
        RenderSystem.getDevice().createCommandEncoder().copyTextureToTexture(source,snapshot.getTexture(),0,0,0,0,0,width,height);
    }
    public static void draw(GuiGraphicsExtractor g){
        if(snapshot==null)return;var mc=Minecraft.getInstance();
        if(mc.gui.screen()!=target||width!=mc.getWindow().getWidth()||height!=mc.getWindow().getHeight()||SolidTheme.reduced()){release();return;}
        if(started==0&&(!(target instanceof SolidScreen s)||s.surfaceReady())){started=System.nanoTime();if(Boolean.getBoolean("unbox.portUiTest")||Boolean.getBoolean("unbox.solidUiTest"))System.out.println("[unbox-transition] ready-ms="+(started-requested)/1_000_000.0+" target="+(target==null?"world":target.getClass().getSimpleName()));}
        float t=started==0?0:Math.clamp((System.nanoTime()-started)/120_000_000f,0,1);
        if(t>=1){release();return;}float alpha=1-t*t*(3-2*t);g.nextStratum();
        // Framebuffer textures have their origin at the lower left.
        g.pose().pushMatrix();g.pose().translate(0,mc.getWindow().getGuiScaledHeight());g.pose().scale(1,-1);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,ID,0,0,0f,0f,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight(),width,height,width,height,(Math.round(alpha*255)<<24)|0xffffff);
        g.pose().popMatrix();
    }
    private static void release(){if(snapshot!=null)Minecraft.getInstance().getTextureManager().release(ID);snapshot=null;started=0;target=null;}
    private static final class Snapshot extends AbstractTexture {
        Snapshot(int w,int h){texture=RenderSystem.getDevice().createTexture("Unbox transition",GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_COPY_DST,com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM,w,h,1,1);textureView=RenderSystem.getDevice().createTextureView(texture);}
    }
}
