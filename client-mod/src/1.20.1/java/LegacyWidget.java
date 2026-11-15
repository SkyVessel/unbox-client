package dev.unbox.client;
import dev.unbox.client.input.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public abstract class LegacyWidget extends AbstractWidget {
 private int mouseButton;
 protected LegacyWidget(int x,int y,int w,int h,Component label){super(x,y,w,h,label);}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){extractWidgetRenderState(g,x,y,delta);}
 protected abstract void extractWidgetRenderState(GuiGraphics g,int x,int y,float delta);
 @Override public boolean keyPressed(int key,int scan,int mods){return keyPressed(new KeyEvent(key,scan,mods));}
 public boolean keyPressed(KeyEvent e){return super.keyPressed(e.key(),e.scancode(),e.modifiers());}
 @Override public boolean mouseClicked(double x,double y,int button){mouseButton=button;return super.mouseClicked(x,y,button);}
 @Override public void onClick(double x,double y){onClick(new MouseButtonEvent(x,y,new MouseButtonInfo(mouseButton,0)),false);}
 public void onClick(MouseButtonEvent e,boolean doubled){super.onClick(e.x(),e.y());}
 @Override protected void onDrag(double x,double y,double dx,double dy){onDrag(new MouseButtonEvent(x,y,new MouseButtonInfo(mouseButton,0)),dx,dy);}
 protected void onDrag(MouseButtonEvent e,double dx,double dy){super.onDrag(e.x(),e.y(),dx,dy);}
 @Override public void onRelease(double x,double y){onRelease(new MouseButtonEvent(x,y,new MouseButtonInfo(mouseButton,0)));}
 public void onRelease(MouseButtonEvent e){super.onRelease(e.x(),e.y());}
 @Override protected boolean isValidClickButton(int button){return isValidClickButton(new MouseButtonInfo(button,0));}
 protected boolean isValidClickButton(MouseButtonInfo button){return super.isValidClickButton(button.button());}
}
