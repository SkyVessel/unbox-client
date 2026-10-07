package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;

final class PanelStyle {
    private static float opacity=1;
    static void opacity(float value){opacity=value;}
    static int tint(int color){return (color&0xffffff)|(Math.round((color>>>24)*opacity)<<24);}
    static void fill(GuiGraphicsExtractor g,int x,int y,int right,int bottom,int color){g.fill(x,y,right,bottom,tint(color));}
    static void outline(GuiGraphicsExtractor g,int x,int y,int w,int h,int color){g.outline(x,y,w,h,tint(color));}
    static final int TEXT=0xffeef2f4,MUTED=0xff9da8b1,ACCENT=0xff86aaff;
    static final FontDescription FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath("unbox","ui"));
    static Component text(String s){return Component.literal(s).withStyle(Style.EMPTY.withFont(FONT));}
    private static final net.minecraft.resources.Identifier GLYPHS=Identifier.fromNamespaceAndPath("unbox","textures/ui/glyphs.png"), ROUND=Identifier.fromNamespaceAndPath("unbox","textures/ui/rounded.png");
    private static String characters;private static int[] advances;
    private static void metrics(){if(advances!=null)return;try(var in=PanelStyle.class.getResourceAsStream("/assets/unbox/ui/metrics.json")){var j=com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();characters=j.get("chars").getAsString();advances=new com.google.gson.Gson().fromJson(j.get("advances"),int[].class);}catch(Exception e){throw new IllegalStateException("Unbox font atlas is missing",e);}}
    static int glyph(char c){metrics();return characters.indexOf(c);}
    static float advance(char c){int i=glyph(c);return i<0?6:advances[i]/8f;}
    static int width(String s){metrics();float w=0;for(char c:s.toCharArray()){int i=characters.indexOf(c);w+=i<0?Minecraft.getInstance().font.width(""+c):advances[i]/8f;}return Math.round(w);}
    static void label(GuiGraphicsExtractor g,String s,int x,int y,int color){
        AtlasText.draw(g,s,x,y,tint(color),false);
    }
    static void center(GuiGraphicsExtractor g,String s,int x,int y,int color){label(g,s,x-width(s)/2,y,color);}
    // Four filtered, anti-aliased texture corners + three solid spans. Constant work at any radius.
    static void round(GuiGraphicsExtractor g,int x,int y,int w,int h,int r,int color){
        color=tint(color);if(w<=0||h<=0)return;r=Math.min(r,Math.min(w,h)/2);if(r<1){g.fill(x,y,x+w,y+h,color);return;}
        g.fill(x+r,y,x+w-r,y+h,color);g.fill(x,y+r,x+r,y+h-r,color);g.fill(x+w-r,y+r,x+w,y+h-r,color);
        for(int a=0;a<2;a++)for(int b=0;b<2;b++)g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,ROUND,x+a*(w-r),y+b*(h-r),a*128f,b*128f,r,r,128,128,256,256,color);
    }
    // HUD submits one cached atlas layout per label, with fractional geometry and filtered glyphs.
    static void hudLabel(GuiGraphicsExtractor g,String s,int x,int y,int color,boolean shadow){AtlasText.draw(g,s,x,y,color,shadow);}
    static void hudCenter(GuiGraphicsExtractor g,String s,int x,int y,int color,boolean shadow){hudLabel(g,s,x-width(s)/2,y,color,shadow);}
    static int hudWidth(String s){return width(s);}
    static int mix(int a,int b,float t){int c=0;for(int s=0;s<32;s+=8)c|=(Math.round(((a>>>s)&255)*(1-t)+((b>>>s)&255)*t)&255)<<s;return c;}
    static final class Motion{private float value;private long time;float to(float target){long now=System.nanoTime();float dt=time==0?1:Math.min(.1f,(now-time)/1e9f);time=now;value+=(target-value)*(1-(float)Math.exp(-dt*18));return value;}}
    static void icon(GuiGraphicsExtractor g,String id,int x,int y,int color){icon(g,id,x,y,32,color);}
    static void icon(GuiGraphicsExtractor g,String id,int x,int y,int size,int color){
        int resolution=id.equals("logo")?768:96;
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,Identifier.fromNamespaceAndPath("unbox","textures/ui/"+id+".png"),x,y,0f,0f,size,size,resolution,resolution,resolution,resolution,tint(color));
    }
}
