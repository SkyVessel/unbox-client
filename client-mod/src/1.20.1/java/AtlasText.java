package dev.unbox.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
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
    static void draw(GuiGraphics g,String text,int x,int y,int color,boolean shadow){
        if(text.isEmpty())return;
        for(int i=0;i<text.length();i++)if(PanelStyle.glyph(text.charAt(i))<0){g.text(Minecraft.getInstance().font,PanelStyle.text(text),x,y,color,shadow);return;}
        Layout layout=layout(text);if(shadow)emit(g,layout,x+1,y+1,0x88000000);emit(g,layout,x,y,color);
    }
    private static void emit(GuiGraphics g,Layout layout,int x,int y,int color){for(Glyph glyph:layout.glyphs){LegacyGui.quad(g,TEXTURE,x+glyph.x,y-2,16,16,glyph.u,glyph.u+.0625f,glyph.v,glyph.v+.125f,color);}}
}
