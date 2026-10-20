package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
/** A continuous, cancellable join status, including the post-restart title phase. */
final class JoinProgressScreen extends Screen {
 private final Screen parent;private boolean lastBusy;
 JoinProgressScreen(Screen parent){super(Component.literal("Joining friend"));this.parent=parent;}
 @Override protected void init(){clearWidgets();lastBusy=SocialBridge.busy();int y=height/2+48;if(!lastBusy)addRenderableWidget(new SocialScreenButton(width/2-110,y,100,"Retry",SocialBridge::retryJoin));addRenderableWidget(new SocialScreenButton(lastBusy?width/2-60:width/2+10,y,lastBusy?120:100,lastBusy?"Cancel":"Back",this::onClose));}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){SocialBridge.cancelJoin();minecraft.setScreen(parent instanceof JoinProgressScreen?new MultiplayerHubScreen(new UnboxHomeScreen(false)):parent);}
 @Override public void tick(){if(lastBusy!=SocialBridge.busy())init();}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(minecraft.level==null)extractPanorama(g,d);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){PanelStyle.center(g,SocialBridge.busy()?"JOINING FRIEND":"COULD NOT JOIN",width/2,height/2-65,PanelStyle.TEXT);g.centeredText(font,font.plainSubstrByWidth(SocialBridge.worldName(),width-32),width/2,height/2-43,PanelStyle.MUTED);int yy=height/2-20;for(var line:font.split(Component.literal(SocialBridge.message),Math.min(480,width-32))){g.centeredText(font,line,width/2,yy,PanelStyle.TEXT);yy+=12;}if(SocialBridge.busy()){int w=Math.min(240,width-48),xx=(width-w)/2;PanelStyle.round(g,xx,height/2+24,w,3,1,0x70545460);int moving=(int)(System.currentTimeMillis()/18%Math.max(1,w-32));PanelStyle.round(g,xx+moving,height/2+24,32,3,1,0xff8eaded);}super.extractRenderState(g,x,y,d);}
}
