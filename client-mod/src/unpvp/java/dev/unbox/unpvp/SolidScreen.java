package dev.unbox.unpvp;

import net.minecraft.client.gui.*;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;
import java.util.*;
import java.awt.geom.Point2D;

/** Native 1.8.9 screen backend for the current shared vector layout. */
abstract class SolidScreen extends UiScreen {
    String page;SolidLayout viewport;
    final List<SolidSurface.Op> paint=new ArrayList<>(),atlasPaint=new ArrayList<>();
    int atlasWidth=958,atlasHeight,uploads;private int revision,pw,ph,layoutWidth,layoutHeight;
    private double density,shownDensity;private String key="";private long entered;
    private SolidSurface surface;private SolidTexture backing,ink;
    Atlas atlas,previousAtlas;private long atlasEntered;float releaseX,releaseY;
    final Map<String,Painter.Motion> motions=new HashMap<>();
    static final class Atlas {final SolidTexture texture;final int logicalWidth,logicalHeight;Atlas(SolidTexture t,int w,int h){texture=t;logicalWidth=w;logicalHeight=h;}}
    SolidScreen(String page,GuiScreen parent){super(parent);this.page=page;}
    String scene(){return SolidTheme.scene(page);}
    private double density(){ScaledResolution r=new ScaledResolution(mc);int selected=r.getScaleFactor()/RetinaDisplay.scale(),automatic=1;int w=mc.displayWidth/RetinaDisplay.scale(),h=mc.displayHeight/RetinaDisplay.scale();while(w/(automatic+1)>=320&&h/(automatic+1)>=240)automatic++;return Math.sqrt(Math.min(1,selected/(double)automatic));}
    @Override final void layout(){if(surface==null)surface=new SolidSurface();layoutWidth=mc.displayWidth;layoutHeight=mc.displayHeight;density=density();viewport=new SolidLayout(page,layoutWidth,layoutHeight,density);scale=(float)(viewport.pixels*width/layoutWidth);logicalWidth=(int)Math.round(viewport.width);logicalHeight=(int)Math.round(viewport.height);offset=0;rebuild();}
    final void rebuild(){controls.clear();paint.clear();atlasPaint.clear();atlasHeight=0;build();revision++;key=scene()+":"+layoutWidth+":"+layoutHeight+":"+density+":"+revision;}
    boolean warm(){SolidSurface.Result ready=surface.poll(key,scene(),layoutWidth,layoutHeight,density,paint,atlasPaint,atlasHeight,atlasWidth);if(ready!=null&&mc.theWorld==null){for(SolidTexture.Pixels pixels:new SolidTexture.Pixels[]{ready.backing,ready.ink,ready.atlas})if(pixels!=null)new SolidTexture(pixels).close();}return surfaceReady();}
    void stopWarm(){if(surface!=null)surface.close();surface=null;}
    abstract void build();
    final void visit(String next){page=next;viewport=new SolidLayout(page,layoutWidth,layoutHeight,density);rebuild();}
    @Override final void paint(float x,float y,float partial){}
    @Override boolean blurred(){return false;}
    @Override boolean entrance(){return false;}
    void content(float mx,float my,float partial){}
    void live(float mx,float my,float partial){}
    void atlasChanged(){}
    boolean surfaceReady(){return surface!=null&&surface.ready(key);}
    boolean coversWorld(){return backing!=null&&pw==mc.displayWidth&&ph==mc.displayHeight&&shownDensity==density&&surfaceReady()&&SolidTheme.opacity()>=1;}
    @Override public void drawScreen(int mx,int my,float partial){
        if(layoutWidth!=mc.displayWidth||layoutHeight!=mc.displayHeight||density!=density())layout();
        boolean panorama=mc.theWorld==null;if(panorama)Panorama.draw(width,height);
        SolidSurface.Result next=surface.poll(key,scene(),layoutWidth,layoutHeight,density,paint,atlasPaint,atlasHeight,atlasWidth);
        if(next!=null){uploads++;if(backing!=null)backing.close();if(ink!=null)ink.close();backing=next.backing==null?null:new SolidTexture(next.backing);ink=new SolidTexture(next.ink);pw=layoutWidth;ph=layoutHeight;shownDensity=density;if(entered==0)entered=System.nanoTime();
            if(next.atlas!=null){releaseAtlas(previousAtlas);previousAtlas=atlas;atlas=new Atlas(new SolidTexture(next.atlas),atlasWidth,atlasHeight);atlasEntered=System.nanoTime();atlasChanged();}}
        if(ink==null)return;if(this instanceof HomeScreen)UiScreen.reportStartup();
        Painter.begin(1,0);try{float unit=width/(float)layoutWidth;float x=(layoutWidth-pw)/2f*unit,y=(layoutHeight-ph)/2f*unit;
            float fade=page.equals("title")&&!SolidTheme.reduced()?1-(float)Math.pow(1-UiState.clamp((System.nanoTime()-entered)/120000000f,0,1),3):1;Painter.opacity=fade;
            if(backing!=null)backing.draw(x,y,pw*unit,ph*unit,(Math.round(SolidTheme.opacity()*255)<<24)|0xffffff);ink.draw(x,y,pw*unit,ph*unit,0xffffffff);
        }finally{Painter.end();}
        if(pw==layoutWidth&&ph==layoutHeight&&shownDensity==density){Painter.begin(scale,0);try{float x=mx/scale,y=my/scale;content(x,y,partial);if(surfaceReady()){live(x,y,partial);for(Control c:controls){if(!c.visible)continue;if(c.clip)beginClip();c.draw(x,y);if(c.clip)Painter.unclip();}}}finally{Painter.end();}}
        if(Boolean.getBoolean("unpvp.test.solid"))SolidProbe.afterFrame(this);
    }
    void text(String value,int x,int y,int max,int size,int color){paint.add(new SolidSurface.Op("text",value,x,y,max,0,color,size));}
    void center(String value,int x,int y,int w,int size,int color){paint.add(new SolidSurface.Op("center",value,x,y,w,0,color,size));}
    void round(int x,int y,int w,int h,int radius,int color){paint.add(new SolidSurface.Op("round","",x,y,w,h,color,radius));}
    void icon(String id,int x,int y,int size,int color){paint.add(new SolidSurface.Op("icon",id,x,y,size,size,color,0));}
    int ux(double x,double y){return viewport.x(x,y);}int uy(double x,double y){return viewport.y(x,y);}
    SolidButton absoluteHit(int x,int y,int w,int h,String label,Runnable run){SolidButton b=new SolidButton(x,y,w,h,label,run);controls.add(b);return b;}
    SolidButton hit(int x,int y,int w,int h,String label,Runnable run){return absoluteHit(ux(x,y),uy(x,y),w,h,label,run);}
    SolidButton solidButton(int x,int y,int w,int h,String label,int color,Runnable run){round(x,y,w,h,Math.min(22,h/2),color);center(label,x,y+(h-22)/2-3,w,22,SolidTheme.text());return hit(x,y,w,h,label,run).shape(Math.min(22,h/2),15);}
    Field solidField(int x,int y,int w,String label,String value,java.util.function.Consumer<String> change){round(x-12,y-12,w+24,50,18,SolidTheme.panel());Field f=field(ux(x,y),uy(x,y),w,26,value,label,change);return f;}
    @Override void beginClip(){Painter.clip(280,258,viewport.gridWidth(),viewport.gridHeight());}
    @Override boolean insideClip(float x,float y){return x>=280&&x<280+viewport.gridWidth()&&y>=258&&y<viewport.gridBottom();}
    float atlasProgress(){float t=SolidTheme.reduced()?1:UiState.clamp((System.nanoTime()-atlasEntered)/120000000f,0,1);if(t>=1){releaseAtlas(previousAtlas);previousAtlas=null;}return t*t*(3-2*t);}
    void drawAtlas(Atlas a,double scroll,float alpha){if(a==null||alpha<=0)return;float offset=(float)SolidGeometry.clamp(scroll,0,Math.max(0,a.logicalHeight-viewport.gridHeight()));beginClip();try{a.texture.draw(280,258-offset,a.logicalWidth,a.logicalHeight,(Math.round(alpha*255)<<24)|0xffffff);}finally{Painter.unclip();}}
    private void releaseAtlas(Atlas a){if(a!=null)a.texture.close();}
    @Override protected void mouseClicked(int x,int y,int button){if(surfaceReady())super.mouseClicked(x,y,button);}
    @Override protected void mouseClickMove(int x,int y,int button,long held){if(surfaceReady())super.mouseClickMove(x,y,button,held);}
    @Override protected void mouseReleased(int x,int y,int button){releaseX=x/scale;releaseY=y/scale;super.mouseReleased(x,y,button);}
    @Override public void onGuiClosed(){super.onGuiClosed();if(surface!=null)surface.close();surface=null;if(backing!=null)backing.close();if(ink!=null)ink.close();backing=ink=null;releaseAtlas(atlas);releaseAtlas(previousAtlas);atlas=previousAtlas=null;entered=0;}
    class SolidButton extends Control {
        final Runnable run;boolean down,feedback=true;int radius=20,corners=15;String geometry="";java.util.function.BooleanSupplier toggle;
        SolidButton(float x,float y,float w,float h,String label,Runnable run){super(x,y,w,h,label);this.run=run;}
        SolidButton shape(int radius,int corners){this.radius=radius;this.corners=corners;return this;}
        SolidButton geometry(String name){geometry=name;return this;}
        @Override boolean hit(float mx,float my){if(!visible||!super.hit(mx,my))return false;double xx=mx-x,yy=my-y;if(!geometry.isEmpty())return SolidGeometry.contains(geometry,xx,yy,w,h);int r=Math.min(radius,(int)Math.min(w,h)/2);if(r==0||toggle!=null)return true;int a=xx<r?0:xx>w-r?1:-1,b=yy<r?0:yy>h-r?1:-1;if(a<0||b<0||(corners&(1<<(a+2*b)))==0)return true;return Math.pow(xx-(a==0?r:w-r),2)+Math.pow(yy-(b==0?r:h-r),2)<=r*r;}
        @Override void draw(float mx,float my){float amount=motions.computeIfAbsent("hover:"+label,k->new Painter.Motion()).to(active&&(hit(mx,my)||focus||down)?1:0);
            if(toggle!=null){float t=motions.computeIfAbsent("toggle:"+label,k->new Painter.Motion()).to(toggle.getAsBoolean()?1:0);SolidGeometry.draw("toggle-track",x+8,y+9,46,26,Painter.mix(0xff999999,SolidTheme.accent(),t));SolidGeometry.draw("toggle-knob",x+12+20*t,y+13,18,18,SolidTheme.index()==2?0xff323437:0xfff5f3ed);return;}
            if(feedback&&amount>.001f){int color=(Math.round(amount*(down?54:28))<<24)|(SolidTheme.text()&0xffffff);if(geometry.isEmpty())Painter.round(x,y,w,h,radius,color);else SolidGeometry.draw(geometry,x,y,w,h,color);}
            if(focus&&!feedback)Painter.round(x+4,y+h-2,w-8,2,1,SolidTheme.accent());
        }
        @Override void click(float mx,float my,int button){if(button==0&&active)down=true;}
        @Override void release(){boolean activate=down&&active&&hit(releaseX,releaseY)&&surfaceReady();down=false;if(activate)run.run();UiState.save();}
        @Override void key(char c,int k){if(active&&(k==Keyboard.KEY_RETURN||k==Keyboard.KEY_SPACE))run.run();}
    }
    class ThemeControl extends Control {
        final SolidGeometry.Curve curve;double position=SolidTheme.index()==2?1:0;boolean dragging;
        ThemeControl(SolidGeometry.Curve c){super(0,0,1,1,"UI color");curve=c;}
        double dx(){return curve==SolidGeometry.THEME?viewport.extraWidth:viewport.dx(650,432);}double dy(){return curve==SolidGeometry.THEME?0:viewport.dy(650,432);}
        @Override boolean hit(float x,float y){return curve.contains(x-dx(),y-dy());}
        void update(float x,float y){position=curve.nearest(x-dx(),y-dy());}
        @Override void click(float x,float y,int b){if(b==0){dragging=true;update(x,y);}}
        @Override void drag(float x,float y,int b){update(x,y);}
        @Override void release(){if(dragging){dragging=false;SolidTheme.select(position<.5?1:2);rebuild();}}
        @Override void key(char c,int k){if(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_UP||k==Keyboard.KEY_RIGHT||k==Keyboard.KEY_DOWN){SolidTheme.select(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_UP?1:2);rebuild();}}
        @Override void draw(float x,float y){GL11.glPushMatrix();try{GL11.glTranslated(dx(),dy(),0);curve.draw(SolidTheme.accent());if(!dragging)position=SolidTheme.index()==2?1:0;Point2D.Double p=curve.point(position);Painter.round((float)p.x-6,(float)p.y-6,12,12,6,SolidTheme.accent());}finally{GL11.glPopMatrix();}}
    }
    class OpacityControl extends Control {
        final SolidGeometry.Curve curve;
        OpacityControl(SolidGeometry.Curve curve){super(180,238,60,(float)viewport.height-412,"Background opacity");this.curve=curve;}
        @Override boolean hit(float x,float y){return curve==null?super.hit(x,y):curve.contains(x-viewport.dx(650,432),y-viewport.dy(650,432));}
        void update(float x,float y){double t=curve==null?1-SolidGeometry.clamp((y-278)/(viewport.height-496),0,1):curve.nearest(x-viewport.dx(650,432),y-viewport.dy(650,432));UiState.set("ui.opacity",Math.round(t*100));}
        @Override void click(float x,float y,int b){if(b==0)update(x,y);}@Override void drag(float x,float y,int b){update(x,y);}
        @Override void key(char c,int k){if(k==Keyboard.KEY_LEFT||k==Keyboard.KEY_DOWN||k==Keyboard.KEY_RIGHT||k==Keyboard.KEY_UP){UiState.set("ui.opacity",UiState.clamp(Math.round(SolidTheme.opacity()*100)+(k==Keyboard.KEY_RIGHT||k==Keyboard.KEY_UP?5:-5),0,100));UiState.save();}}
        @Override void draw(float x,float y){double t=SolidTheme.opacity();if(curve==null)SolidGeometry.verticalTab(210,278+(int)Math.round((1-t)*(viewport.height-496)),SolidTheme.accent());else{GL11.glPushMatrix();try{GL11.glTranslated(viewport.dx(650,432),viewport.dy(650,432),0);curve.draw(SolidTheme.accent());Point2D.Double p=curve.point(t);Painter.round((float)p.x-6,(float)p.y-6,12,12,6,SolidTheme.accent());}finally{GL11.glPopMatrix();}}if(hit(x,y)||focus)SolidText.draw(Math.round(t*100)+"%",curve==null?210:ux(650,432),curve==null?(int)viewport.height-125:uy(650,432)+206,18,SolidTheme.text(),true);}
    }
}
