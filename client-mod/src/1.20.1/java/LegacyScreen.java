package dev.unbox.client;
import dev.unbox.client.input.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
/** Bridge Minecraft 1.20's primitive events to the shared screen implementations. */
public abstract class LegacyScreen extends Screen {
 protected LegacyScreen(Component title){super(title);}
 @Override public void render(GuiGraphics g,int x,int y,float delta){extractBackground(g,x,y,delta);extractRenderState(g,x,y,delta);}
 public void extractRenderState(GuiGraphics g,int x,int y,float delta){super.render(g,x,y,delta);}
 public void extractBackground(GuiGraphics g,int x,int y,float delta){super.renderBackground(g);}
 protected void extractPanorama(GuiGraphics g,float delta){LegacyGui.panorama(delta);}
 protected void extractBlurredBackground(GuiGraphics g){LegacyGui.blur();}
 @Override public boolean keyPressed(int key,int scan,int mods){return keyPressed(new KeyEvent(key,scan,mods));}
 public boolean keyPressed(KeyEvent e){return super.keyPressed(e.key(),e.scancode(),e.modifiers());}
 @Override public boolean mouseClicked(double x,double y,int button){return mouseClicked(new MouseButtonEvent(x,y,new MouseButtonInfo(button,0)),false);}
 public boolean mouseClicked(MouseButtonEvent e,boolean doubled){return super.mouseClicked(e.x(),e.y(),e.button());}
 @Override public boolean mouseReleased(double x,double y,int button){return mouseReleased(new MouseButtonEvent(x,y,new MouseButtonInfo(button,0)));}
 public boolean mouseReleased(MouseButtonEvent e){return super.mouseReleased(e.x(),e.y(),e.button());}
 @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return mouseDragged(new MouseButtonEvent(x,y,new MouseButtonInfo(button,0)),dx,dy);}
 public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){return super.mouseDragged(e.x(),e.y(),e.button(),dx,dy);}
 @Override public boolean mouseScrolled(double x,double y,double amount){return mouseScrolled(x,y,0,amount);}
 public boolean mouseScrolled(double x,double y,double horizontal,double vertical){return super.mouseScrolled(x,y,vertical);}
}
