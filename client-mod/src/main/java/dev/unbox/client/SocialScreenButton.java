package dev.unbox.client;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
final class SocialScreenButton extends Button {
 SocialScreenButton(int x,int y,int w,String label,Runnable action){super(x,y,w,22,PanelStyle.text(label),b->action.run(),DEFAULT_NARRATION);}
 @Override protected void extractContents(GuiGraphicsExtractor g,int x,int y,float delta){if(isHoveredOrFocused())PanelStyle.round(g,getX(),getY(),width,height,4,0x8059667c);PanelStyle.center(g,getMessage().getString(),getX()+width/2,getY()+6,active?PanelStyle.TEXT:PanelStyle.MUTED);}
}
