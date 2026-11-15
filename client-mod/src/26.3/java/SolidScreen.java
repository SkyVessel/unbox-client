package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.*;
import net.minecraft.network.chat.*;
import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.*;
import java.util.*;

/** Solid controls over a separately composited background, at the selected GUI scale. */
public abstract class SolidScreen extends Screen {
    protected final Screen parent;
    protected String page;
    protected final List<SolidSurface.Op> paint=new ArrayList<>(),atlasPaint=new ArrayList<>();
    protected int atlasHeight,atlasWidth=958;
    protected SolidLayout viewport;
    private SolidSurface surface;
    private Identifier backing,ink;
    private int pw,ph,revision;
    private double shownZoom;
    int uploads;
    private long entered;
    private boolean opacityDirty,keyboardNavigation;
    private final String textureId="solid/"+UUID.randomUUID();
    protected float scale=1,scaleY=1,ox,oy;
    private double zoom=1;
    private int layoutWidth,layoutHeight,layoutGuiWidth,layoutGuiHeight;
    private String layoutPage="",cachedSurfaceKey="";
    private SolidButton pressed;
    protected final Map<String,PanelStyle.Motion> motions=new HashMap<>();
    protected record Atlas(Identifier texture,int width,int height,int logicalHeight,int logicalWidth) {}
    protected Atlas atlas,previousAtlas;
    private long atlasEntered;
    private int textureSerial;
    protected void atlasChanged(){}
    final boolean prewarm(){
        var next=surface.poll(surfaceKey(),scene(),layoutWidth,layoutHeight,zoom,paint,atlasPaint,atlasHeight,atlasWidth);
        if(next!=null){if(next.backing()!=null)release(upload(next.backing(),"back"));release(upload(next.ink(),"ink"));if(next.atlas()!=null)release(upload(next.atlas(),"cards-warm"));}
        return surface.ready(surfaceKey());
    }
    protected String scene(){return SolidTheme.scene(page);}
    public boolean coversWorld(){return backing!=null&&pw==minecraft.getWindow().getWidth()&&ph==minecraft.getWindow().getHeight()&&shownZoom==guiZoom()&&SolidTheme.opacity()>=1&&!UiTransition.active();}
    SolidScreen(String title,String page,Screen parent){super(Component.literal(title));this.page=page;this.parent=parent;}
    @Override protected void init(){if(surface==null)surface=new SolidSurface();layout();rebuild();}
    private double guiZoom(){var window=minecraft.getWindow();return Math.sqrt(Math.min(1,window.getGuiScale()/(double)Math.max(1,window.calculateScale(0,false))));}
    private void layout(){
        var window=minecraft.getWindow();double nextZoom=guiZoom();
        if(viewport!=null&&layoutWidth==window.getWidth()&&layoutHeight==window.getHeight()&&layoutGuiWidth==width&&layoutGuiHeight==height&&zoom==nextZoom&&layoutPage.equals(page))return;
        layoutWidth=window.getWidth();layoutHeight=window.getHeight();layoutGuiWidth=width;layoutGuiHeight=height;layoutPage=page;zoom=nextZoom;cachedSurfaceKey="";viewport=new SolidLayout(page,window.getWidth(),window.getHeight(),zoom);double pixels=viewport.pixels;
        scale=(float)(pixels*width/window.getWidth());scaleY=(float)(pixels*height/window.getHeight());
        ox=oy=0;
    }
    protected final void rebuild(){pressed=null;setDragging(false);String focus=getFocused() instanceof AbstractWidget w?w.getMessage().getString():"";clearWidgets();paint.clear();atlasPaint.clear();atlasHeight=0;build();revision++;cachedSurfaceKey="";for(var child:children())if(child instanceof AbstractWidget w&&w.getMessage().getString().equals(focus)){setFocused(w);break;}}
    protected abstract void build();
    protected void visit(String next){page=next;layout();clearFocus();rebuild();}
    protected void text(String value,int x,int y,int max,int size,int color){paint.add(new SolidSurface.Op("text",value,x,y,max,0,color,size));}
    protected void center(String value,int x,int y,int w,int size,int color){paint.add(new SolidSurface.Op("center",value,x,y,w,0,color,size));}
    protected void round(int x,int y,int w,int h,int radius,int color){paint.add(new SolidSurface.Op("round","",x,y,w,h,color,radius));}
    protected void icon(String id,int x,int y,int size,int color){paint.add(new SolidSurface.Op("icon",id,x,y,size,size,color,0));}
    protected int ux(double x,double y){return viewport.x(x,y);}
    protected int uy(double x,double y){return viewport.y(x,y);}
    protected int listBottom(){return viewport.gridBottom();}
    protected int listRight(){return 280+viewport.gridWidth();}
    protected SolidButton absoluteHit(int x,int y,int w,int h,String label,Runnable action){return addRenderableWidget(new SolidButton(x,y,w,h,label,action));}
    protected SolidButton hit(int x,int y,int w,int h,String label,Runnable action){return absoluteHit(ux(x,y),uy(x,y),w,h,label,action);}
    protected SolidButton button(int x,int y,int w,int h,String label,int color,Runnable action){round(x,y,w,h,Math.min(22,h/2),color);center(label,x,y+(h-22)/2-3,w,22,color==SolidTheme.accent()?(SolidTheme.index()==1?0xfff5f3ed:0xff292b2e):SolidTheme.text());return hit(x,y,w,h,label,action).shape(Math.min(22,h/2),15);}
    protected EditBox field(int x,int y,int w,String label,String value,java.util.function.Consumer<String> changed){
        round(x-12,y-12,w+24,50,18,SolidTheme.panel());
        EditBox box=new EditBox(font,ux(x,y),uy(x,y),w,26,Component.literal(label));box.setBordered(false);box.setTextShadow(false);box.setTextColor(SolidTheme.text());box.setMaxLength(128);box.setValue(value);
        box.addFormatter((s,i)->styled(s).getVisualOrderText());box.setHint(styled(label));box.setResponder(changed);return addRenderableWidget(box);
    }
    private Component styled(String text){return Component.literal(text).withStyle(Style.EMPTY.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("unbox","solid"))));}
    protected void label(GuiGraphicsExtractor g,String text,float x,float y,int size,int color,boolean centered){g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(size/20f);var c=styled(text);g.text(font,c,centered?-font.width(c)/2:0,0,color,false);g.pose().popMatrix();}
    protected void themePicker(){} // Respect the saved motion preference without a footer control.
    protected void content(GuiGraphicsExtractor g,int mx,int my,float delta){}
    protected void live(GuiGraphicsExtractor g,int mx,int my,float delta){}
    final String surfaceKey(){if(cachedSurfaceKey.isEmpty())cachedSurfaceKey=scene()+":"+layoutWidth+":"+layoutHeight+":"+zoom+":"+paint.toString()+":"+atlasPaint.toString()+":"+atlasWidth+":"+atlasHeight;return cachedSurfaceKey;}
    final boolean surfaceReady(){return surface!=null&&surface.ready(surfaceKey());}
    @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        // The dense mod grid sits above the HUD and its card atlas.
        boolean layered=this instanceof VectorModsScreen;if(layered)g.nextStratum();
        layout();int w=minecraft.getWindow().getWidth(),h=minecraft.getWindow().getHeight();
        var next=surface.poll(surfaceKey(),scene(),w,h,zoom,paint,atlasPaint,atlasHeight,atlasWidth);
        if(next!=null){
            uploads++;release(backing);release(ink);backing=next.backing()==null?null:upload(next.backing(),"back");ink=upload(next.ink(),"ink");pw=w;ph=h;shownZoom=zoom;if(entered==0)entered=System.nanoTime();
            if(next.atlas()!=null){releaseAtlas(previousAtlas);previousAtlas=atlas;atlas=new Atlas(upload(next.atlas(),"cards-"+(textureSerial++)),next.atlas().getWidth(),next.atlas().getHeight(),atlasHeight,atlasWidth);atlasEntered=System.nanoTime();atlasChanged();}
        }
        boolean panorama=page.equals("title")&&minecraft.level==null;
        if(panorama)extractPanorama(g,delta);
        float opacity=minecraft.level==null?1:SolidTheme.opacity();
        if(ink==null){if(panorama)return;g.fill(0,0,width,height,page.equals("title")?0xff000000:(SolidTheme.background()&0xffffff)|(Math.round(opacity*255)<<24));return;}
        g.pose().pushMatrix();g.pose().scale(width/(float)w,height/(float)h);
        if(!panorama&&(pw!=w||ph!=h))g.fill(0,0,w,h,(SolidTheme.background()&0xffffff)|(Math.round(opacity*255)<<24));
        int x=(w-pw)/2,y=(h-ph)/2;if(!panorama&&backing!=null)blit(g,backing,x,y,pw,ph,opacity);blit(g,ink,x,y,pw,ph,1);g.pose().popMatrix();
        if(pw==w&&ph==h&&shownZoom==zoom){
            g.pose().pushMatrix();g.pose().translate(ox,oy);g.pose().scale(scale,scaleY);
            try{
                int ux=(int)((mx-ox)/scale),uy=(int)((my-oy)/scaleY);content(g,ux,uy,delta);
                if(surfaceReady()){if(layered)g.nextStratum();live(g,ux,uy,delta);for(var child:children())if(child instanceof AbstractWidget widget&&widget.visible){
                    if(widget instanceof SolidButton b&&b.inList){g.enableScissor(280,258,listRight(),listBottom());g.pose().pushMatrix();g.pose().translate(0,b.fractionY);PanelStyle.opacity(b.listAlpha);widget.extractRenderState(g,ux,uy,delta);PanelStyle.opacity(1);g.pose().popMatrix();g.disableScissor();}
                    else widget.extractRenderState(g,ux,uy,delta);
                }}
            }finally{g.pose().popMatrix();}
        }
        if(page.equals("title")&&!SolidTheme.reduced()){
            float t=Math.clamp((System.nanoTime()-entered)/120_000_000f,0,1);float fade=1-(1-t)*(1-t)*(1-t);
            if(fade<1){g.nextStratum();g.fill(0,0,width,height,Math.round(255*(1-fade))<<24);}
        }
    }
    protected float atlasProgress(){float t=SolidTheme.reduced()?1:Math.clamp((System.nanoTime()-atlasEntered)/120_000_000f,0,1);if(t>=1&&previousAtlas!=null){releaseAtlas(previousAtlas);previousAtlas=null;}return t*t*(3-2*t);}
    protected void drawAtlas(GuiGraphicsExtractor g,Atlas a,double scroll,float alpha){
        if(a==null||alpha<=0)return;double offset=Math.clamp(scroll,0,Math.max(0,a.logicalHeight()-viewport.gridHeight()));
        g.enableScissor(280,258,listRight(),listBottom());g.pose().pushMatrix();g.pose().translate(0,(float)-offset);
        g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,a.texture(),280,258,0f,0f,a.logicalWidth(),a.logicalHeight(),a.width(),a.height(),a.width(),a.height(),(Math.round(alpha*255)<<24)|0xffffff);
        g.pose().popMatrix();g.disableScissor();
    }
    private void blit(GuiGraphicsExtractor g,Identifier id,int x,int y,int w,int h,float alpha){g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,id,x,y,0f,0f,w,h,w,h,w,h,(Math.round(alpha*255)<<24)|0xffffff);}
    private static final long TEXTURE_LIMIT=128L*1024*1024;
    private static long textureBytes,serial;
    private static final LinkedHashMap<java.awt.image.BufferedImage,Resident> TEXTURES=new LinkedHashMap<>(8,.75f,true);
    private static final class Resident {final Identifier id;final long bytes;int users;Resident(Identifier id,long bytes){this.id=id;this.bytes=bytes;users=1;}}
    private Identifier upload(java.awt.image.BufferedImage b,String layer){
        Resident hit=TEXTURES.get(b);if(hit!=null){hit.users++;return hit.id;}
        NativeImage image=new NativeImage(b.getWidth(),b.getHeight(),false);int[] pixels=((java.awt.image.DataBufferInt)b.getRaster().getDataBuffer()).getData();
        for(int y=0;y<b.getHeight();y++)for(int x=0;x<b.getWidth();x++)image.setPixel(x,y,pixels[y*b.getWidth()+x]);
        Identifier id=Identifier.fromNamespaceAndPath("unbox","solid_cache/"+(serial++));minecraft.getTextureManager().register(id,new SurfaceTexture(image,layer.startsWith("cards-")));
        Resident r=new Resident(id,4L*b.getWidth()*b.getHeight());TEXTURES.put(b,r);textureBytes+=r.bytes;trimTextures();return id;
    }
    private static void trimTextures(){var it=TEXTURES.values().iterator();while(textureBytes>TEXTURE_LIMIT&&it.hasNext()){var r=it.next();if(r.users==0){net.minecraft.client.Minecraft.getInstance().getTextureManager().release(r.id);textureBytes-=r.bytes;it.remove();}}}
    private static final class SurfaceTexture extends DynamicTexture{SurfaceTexture(NativeImage image,boolean smooth){super(()->"Unbox solid UI",image);sampler=RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,AddressMode.CLAMP_TO_EDGE,smooth?FilterMode.LINEAR:FilterMode.NEAREST,smooth?FilterMode.LINEAR:FilterMode.NEAREST,false);}}
    private MouseButtonEvent point(MouseButtonEvent e){return new MouseButtonEvent((e.x()-ox)/scale,(e.y()-oy)/scaleY,e.buttonInfo());}
    @Override public java.util.Optional<net.minecraft.client.gui.components.events.GuiEventListener> getChildAt(double x,double y){for(var child:children().reversed())if(child.isMouseOver(x,y))return java.util.Optional.of(child);return java.util.Optional.empty();}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){if(!surfaceReady())return false;keyboardNavigation=false;return super.mouseClicked(point(e),doubled);}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(!surfaceReady())return false;return super.mouseDragged(point(e),dx/scale,dy/scaleY);}
    @Override public boolean mouseReleased(MouseButtonEvent e){MouseButtonEvent p=point(e);SolidButton button=pressed;pressed=null;if(button!=null){setDragging(false);button.down=false;if(button.active&&button.isMouseOver(p.x(),p.y()))button.run.run();return true;}return super.mouseReleased(p);}
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical){return super.mouseScrolled((mx-ox)/scale,(my-oy)/scaleY,horizontal,vertical);}
    @Override public boolean keyPressed(KeyEvent e){keyboardNavigation=true;if(e.key()==InputConstants.KEY_RSHIFT){minecraft.gui.setScreen(minecraft.level!=null?null:new UnboxHomeScreen(false));return true;}return super.keyPressed(e);}
    @Override public void onClose(){minecraft.gui.setScreen(parent);}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){if(opacityDirty){UnboxClient.save();opacityDirty=false;}release(backing);release(ink);backing=ink=null;releaseAtlas(atlas);releaseAtlas(previousAtlas);atlas=previousAtlas=null;if(surface!=null){surface.close();surface=null;}pressed=null;}
    private void release(Identifier id){if(id==null)return;for(var r:TEXTURES.values())if(r.id.equals(id)){r.users--;break;}trimTextures();}
    private void releaseAtlas(Atlas a){if(a!=null)release(a.texture());}
    protected final class OpacityControl extends AbstractWidget {
        private final SolidGeometry.Curve curve;
        OpacityControl(SolidGeometry.Curve curve){super(curve==null?180:526,curve==null?238:410,curve==null?60:210,curve==null?(int)Math.round(viewport.height-412):230,Component.literal("Background opacity"));this.curve=curve;if(curve!=null){setX(ux(650,432)-124);setY(uy(650,432)-22);}}
        @Override public boolean isMouseOver(double x,double y){return curve==null?super.isMouseOver(x,y):curve.contains(x-viewport.dx(650,432),y-viewport.dy(650,432));}
        private void update(double x,double y){double t=curve==null?1-Math.clamp((y-278)/(viewport.height-496),0,1):curve.nearest(x-viewport.dx(650,432),y-viewport.dy(650,432));UnboxClient.set("ui.opacity",Math.round(t*100));opacityDirty=true;}
        @Override public void onClick(MouseButtonEvent e,boolean doubled){update(e.x(),e.y());}
        @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){update(e.x(),e.y());}
        @Override public void onRelease(MouseButtonEvent e){UnboxClient.save();opacityDirty=false;}
        @Override public boolean keyPressed(KeyEvent e){int key=e.key();if(key==InputConstants.KEY_LEFT||key==InputConstants.KEY_RIGHT||key==InputConstants.KEY_UP||key==InputConstants.KEY_DOWN){UnboxClient.set("ui.opacity",Math.clamp(Math.round(SolidTheme.opacity()*100)+(key==InputConstants.KEY_RIGHT||key==InputConstants.KEY_UP?5:-5),0,100));UnboxClient.save();return true;}return super.keyPressed(e);}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            double t=SolidTheme.opacity();if(curve==null){SolidGeometry.verticalTab(g,210,278+(int)Math.round((1-t)*(viewport.height-496)),SolidTheme.accent());}
            else{g.pose().pushMatrix();g.pose().translate((float)viewport.dx(650,432),(float)viewport.dy(650,432));curve.draw(g,SolidTheme.accent());var p=curve.point(t);PanelStyle.round(g,(int)p.x-6,(int)p.y-6,12,12,6,SolidTheme.accent());g.pose().popMatrix();}
            if(isHoveredOrFocused())label(g,Math.round(t*100)+"%",curve==null?210:ux(650,432),curve==null?(int)viewport.height-125:uy(650,432)+206,18,SolidTheme.text(),true);
        }
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    protected final class ThemeControl extends AbstractWidget {
        private double position=SolidTheme.index()==2?1:0;private boolean dragging;
        private final SolidGeometry.Curve curve;
        ThemeControl(){this(SolidGeometry.THEME);}
        ThemeControl(SolidGeometry.Curve curve){super(0,0,210,230,Component.literal("UI color"));this.curve=curve;setX((int)(curve==SolidGeometry.THEME?ux(1167,116):ux(526,410)));setY(curve==SolidGeometry.THEME?116:uy(526,410));}
        private double dx(){return curve==SolidGeometry.THEME?viewport.extraWidth:viewport.dx(650,432);}
        private double dy(){return curve==SolidGeometry.THEME?0:viewport.dy(650,432);}
        @Override public boolean isMouseOver(double x,double y){return curve.contains(x-dx(),y-dy());}
        private void update(MouseButtonEvent e){position=curve.nearest(e.x()-dx(),e.y()-dy());}
        @Override public void onClick(MouseButtonEvent e,boolean doubled){dragging=true;update(e);}
        @Override protected void onDrag(MouseButtonEvent e,double dx,double dy){update(e);}
        @Override public void onRelease(MouseButtonEvent e){update(e);dragging=false;SolidTheme.select(position<.5?1:2);rebuild();}
        @Override public boolean keyPressed(KeyEvent e){int key=e.key();if(key==InputConstants.KEY_LEFT||key==InputConstants.KEY_UP||key==InputConstants.KEY_RIGHT||key==InputConstants.KEY_DOWN){SolidTheme.select(key==InputConstants.KEY_LEFT||key==InputConstants.KEY_UP?1:2);rebuild();return true;}return super.keyPressed(e);}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){g.pose().pushMatrix();g.pose().translate((float)dx(),(float)dy());curve.draw(g,SolidTheme.accent());if(!dragging)position=SolidTheme.index()==2?1:0;var p=curve.point(position);PanelStyle.round(g,(int)Math.round(p.x)-6,(int)Math.round(p.y)-6,12,12,6,SolidTheme.accent());g.pose().popMatrix();}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
    protected class SolidButton extends AbstractWidget {
        final Runnable run;boolean down,feedback=true,inList;int corners=15,radius=20;float fractionY,listAlpha=1;String geometry="";java.util.function.BooleanSupplier toggle;
        SolidButton(int x,int y,int w,int h,String label,Runnable run){super(x,y,w,h,Component.literal(label));this.run=run;}
        SolidButton shape(int radius,int corners){this.radius=radius;this.corners=corners;return this;}
        SolidButton geometry(String name){geometry=name;return this;}
        @Override public boolean isMouseOver(double x,double y){if(!visible||(inList&&listAlpha<.5f)||(inList&&(y<258||y>=listBottom()||x<280||x>=listRight())))return false;double localX=x-getX(),localY=y-getY()-fractionY;if(!geometry.isEmpty())return SolidGeometry.contains(geometry,localX,localY,width,height);if(!super.isMouseOver(x,y-fractionY))return false;int r=Math.min(radius,Math.min(width,height)/2);if(toggle!=null||r==0)return true;int a=localX<r?0:localX>width-r?1:-1,b=localY<r?0:localY>height-r?1:-1;if(a<0||b<0||(corners&(1<<(a+2*b)))==0)return true;double dx=localX-(a==0?r:width-r),dy=localY-(b==0?r:height-r);return dx*dx+dy*dy<=r*r;}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
            if(!visible)return;float target=active&&(isHovered()||(keyboardNavigation&&isFocused())||down)?1:0;float hover=motions.computeIfAbsent("hover:"+getMessage().getString()+":"+getX(),k->new PanelStyle.Motion()).to(target);
            if(toggle!=null){float amount=motions.computeIfAbsent("toggle:"+getMessage().getString(),k->new PanelStyle.Motion()).to(toggle.getAsBoolean()?1:0);int track=PanelStyle.mix(0xff999999,SolidTheme.accent(),amount);track=PanelStyle.mix(track,SolidTheme.text(),hover*.15f);SolidGeometry.draw(g,"toggle-track",getX()+8,getY()+9,46,26,PanelStyle.tint(track));SolidGeometry.draw(g,"toggle-knob",getX()+12+Math.round(20*amount),getY()+13,18,18,PanelStyle.tint(SolidTheme.index()==2?0xff323437:0xfff5f3ed));return;}
            int tint=(Math.round(hover*(down?54:28))<<24)|(SolidTheme.text()&0xffffff);
            if(feedback&&hover>.001f){if(geometry.isEmpty())PanelStyle.roundCorners(g,getX(),getY(),width,height,radius,tint,corners);else SolidGeometry.draw(g,geometry,getX(),getY(),width,height,tint);}
            if(!feedback&&keyboardNavigation&&isFocused())PanelStyle.round(g,getX()+4,getBottom()-2,width-8,2,1,SolidTheme.accent());
            if(!active&&feedback){if(geometry.isEmpty())PanelStyle.roundCorners(g,getX(),getY(),width,height,radius,0x30666666,corners);else SolidGeometry.draw(g,geometry,getX(),getY(),width,height,0x30666666);}
        }
        @Override public void onClick(MouseButtonEvent e,boolean doubled){pressed=this;down=true;}
        @Override public boolean keyPressed(KeyEvent e){if(active&&e.isConfirmation()){run.run();return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
}
