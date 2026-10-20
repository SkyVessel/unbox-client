package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
final class NetworkDiagnosticsScreen extends Screen {
 private final Screen parent;
 NetworkDiagnosticsScreen(Screen parent){super(Component.literal("Connection diagnostics"));this.parent=parent;}
 @Override protected void init(){addRenderableWidget(new SocialScreenButton(width/2-110,height-30,100,"Save report",JoinDiagnostics::save));addRenderableWidget(new SocialScreenButton(width/2+10,height-30,100,"Back",this::onClose));}
 @Override public void onClose(){minecraft.setScreen(parent);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(minecraft.level==null)extractPanorama(g,d);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){PanelStyle.center(g,"CONNECTION DIAGNOSTICS",width/2,16,PanelStyle.TEXT);int yy=48;for(String line:JoinDiagnostics.lines()){for(var part:font.split(Component.literal(line),Math.min(540,width-28))){g.text(font,part,Math.max(14,(width-540)/2),yy,PanelStyle.TEXT);yy+=12;}yy+=8;}g.centeredText(font,JoinDiagnostics.exportStatus,width/2,height-46,PanelStyle.MUTED);super.extractRenderState(g,x,y,d);}
}
