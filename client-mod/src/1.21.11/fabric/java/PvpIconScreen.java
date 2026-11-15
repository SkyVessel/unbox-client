package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import java.util.List;
/** Unbox pixel-frame editor over the author's live animation object. */
final class PvpIconScreen extends SolidScreen {
 private final String key;private final Object animation;private int frame,color=0xffffffff;private boolean drawing,erase;
 PvpIconScreen(String key,Screen parent){super("Edit icon","dialog",parent);this.key=key;animation=PvpSettings.icon(key);}
 private Object grid(){return PvpSettings.call(animation,"getFrame",frame);}
 private int count(){return ((List<?>)PvpSettings.call(animation,"getFrames")).size();}
 protected void build(){
  center("Edit icon · Frame "+(frame+1)+" / "+count(),350,150,740,28,SolidTheme.text());
  button(390,210,160,38,"Previous frame",SolidTheme.card(0),()->{frame=Math.floorMod(frame-1,count());rebuild();});
  button(570,210,160,38,"Next frame",SolidTheme.card(0),()->{frame=(frame+1)%count();rebuild();});
  button(750,210,160,38,"Duplicate frame",SolidTheme.card(0),()->{PvpSettings.call(animation,"addFrame",PvpSettings.call(grid(),"copy"));frame=count()-1;changed();rebuild();});
  button(930,210,140,38,"Delete frame",SolidTheme.card(0),()->{if(count()>1){PvpSettings.call(animation,"removeFrame",frame);frame=Math.min(frame,count()-1);changed();rebuild();}}).active=count()>1;
  text("Paint color",860,310,230,22,SolidTheme.text());field(880,360,180,"Hex RGB",String.format("#%06X",color&0xffffff),s->{if(s.matches("#[0-9a-fA-F]{6}"))color=(color&0xff000000)|Integer.parseInt(s.substring(1),16);});
  text("Opacity (0–255)",860,415,250,22,SolidTheme.text());field(880,465,180,"Opacity",""+(color>>>24),s->{try{color=(Math.clamp(Integer.parseInt(s),0,255)<<24)|(color&0xffffff);}catch(NumberFormatException ignored){}});
  button(860,530,230,40,"Clear frame",SolidTheme.card(0),()->{Object g=grid();int w=(int)PvpSettings.call(g,"getWidth"),h=(int)PvpSettings.call(g,"getHeight");for(int y=0;y<h;y++)for(int x=0;x<w;x++)PvpSettings.call(g,"setColor",x,y,0);changed();});
  button(860,590,230,40,"Grid resolution",SolidTheme.card(0),()->{Object g=grid(),resolution=PvpSettings.call(g,"getResolution");Object[] values=resolution.getClass().getEnumConstants();Object next=values[(((Enum<?>)resolution).ordinal()+1)%values.length];PvpSettings.call(animation,"setFrame",frame,PvpSettings.call(g,"resizedTo",next));changed();});
  center("Left drag to paint · Right drag to erase",340,675,760,20,SolidTheme.muted());button(600,720,240,40,"Back",SolidTheme.accent(),this::onClose);
 }
 protected void live(GuiGraphics g,int mx,int my,float delta){Object grid=grid();int w=(int)PvpSettings.call(grid,"getWidth"),h=(int)PvpSettings.call(grid,"getHeight");for(int y=0;y<h;y++)for(int x=0;x<w;x++){int px=ux(420,290)+x*360/w,py=uy(420,290)+y*360/h;g.fill(px,py,px+360/w,py+360/h,(x+y)%2==0?0xff44474a:0xff5b5e61);int c=(int)PvpSettings.call(grid,"getColor",x,y);if((c>>>24)>0)g.fill(px+1,py+1,px+360/w-1,py+360/h-1,c);}}
 private boolean paint(MouseButtonEvent e){double x=(e.x()-ox)/scale-ux(420,290),y=(e.y()-oy)/scaleY-uy(420,290);if(x<0||y<0||x>=360||y>=360)return false;Object g=grid();PvpSettings.call(g,"setColor",(int)(x*(int)PvpSettings.call(g,"getWidth")/360),(int)(y*(int)PvpSettings.call(g,"getHeight")/360),erase?0:color);changed();return true;}
 private void changed(){PvpSettings.iconChanged(key);}
 @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){if(e.button()==0||e.button()==1){erase=e.button()==1;if(surfaceReady()&&paint(e)){drawing=true;return true;}}return super.mouseClicked(e,doubleClick);}
 @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(drawing){paint(e);return true;}return super.mouseDragged(e,dx,dy);}
 @Override public boolean mouseReleased(MouseButtonEvent e){if(drawing){drawing=false;PvpSettings.flush();return true;}return super.mouseReleased(e);}
 @Override public void onClose(){PvpSettings.flush();super.onClose();}
 @Override public void removed(){PvpSettings.flush();super.removed();}
}
