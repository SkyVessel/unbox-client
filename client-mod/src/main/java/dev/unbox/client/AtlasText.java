package dev.unbox.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import java.util.*;

/** Submit a complete label as one GUI element, not one element/matrix copy per glyph. */
final class AtlasText {
    private static final Identifier TEXTURE=Identifier.fromNamespaceAndPath("unbox","textures/ui/glyphs.png");
    private record Glyph(float x,float u,float v) {}
    private record Layout(List<Glyph> glyphs,int width) {}
    private static final Map<String,Layout> CACHE=new LinkedHashMap<>(256,.75f,true){protected boolean removeEldestEntry(Map.Entry<String,Layout> e){return size()>256;}};
    private static Layout layout(String text){return CACHE.computeIfAbsent(text,s->{List<Glyph> glyphs=new ArrayList<>();float x=0;for(int i=0;i<s.length();i++){char c=s.charAt(i);int index=PanelStyle.glyph(c);if(c!=' ')glyphs.add(new Glyph(x-1,(index%16)/16f,(index/16)/8f));x+=PanelStyle.advance(c);}return new Layout(List.copyOf(glyphs),(int)Math.ceil(x));});}
    static void draw(GuiGraphicsExtractor g,String text,int x,int y,int color,boolean shadow){
        if(text.isEmpty())return;
        for(int i=0;i<text.length();i++)if(PanelStyle.glyph(text.charAt(i))<0){g.text(Minecraft.getInstance().font,PanelStyle.text(text),x,y,color,shadow);return;}
        Layout layout=layout(text);Matrix3x2f pose=new Matrix3x2f(g.pose()).translate(x,y);GraphicsAccess access=(GraphicsAccess)g;
        ScreenRectangle clip=access.unboxClip();ScreenRectangle bounds=new ScreenRectangle(-1,-2,layout.width+17,17).transformMaxBounds(pose);if(clip!=null){bounds=bounds.intersection(clip);if(bounds==null)return;}
        var texture=Minecraft.getInstance().getTextureManager().getTexture(TEXTURE);
        access.unboxState().addGuiElement(new Label(pose,layout,color,shadow,TextureSetup.singleTexture(texture.getTextureView(),texture.getSampler()),clip,bounds));
    }
    private record Label(Matrix3x2f pose,Layout layout,int color,boolean shadow,TextureSetup textureSetup,ScreenRectangle scissorArea,ScreenRectangle bounds) implements GuiElementRenderState {
        public RenderPipeline pipeline(){return RenderPipelines.GUI_TEXTURED;}
        public void buildVertices(VertexConsumer v){if(shadow)emit(v,1,0x88000000);emit(v,0,color);}
        private void emit(VertexConsumer v,float offset,int tint){for(Glyph g:layout.glyphs){float x=g.x+offset,y=-2+offset,u=g.u,uv=g.v;
            v.addVertexWith2DPose(pose,x,y).setUv(u,uv).setColor(tint);
            v.addVertexWith2DPose(pose,x,y+16).setUv(u,uv+.125f).setColor(tint);
            v.addVertexWith2DPose(pose,x+16,y+16).setUv(u+.0625f,uv+.125f).setColor(tint);
            v.addVertexWith2DPose(pose,x+16,y).setUv(u+.0625f,uv).setColor(tint);
        }}
    }
}
