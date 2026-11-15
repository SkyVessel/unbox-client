package dev.unbox.client;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
public abstract class LegacyButton extends Button {
 protected LegacyButton(int x,int y,int w,int h,Component label,OnPress action,CreateNarration narration){super(x,y,w,h,label,action,narration);}
 @Override protected void renderWidget(GuiGraphics g,int x,int y,float delta){extractContents(g,x,y,delta);}
 protected abstract void extractContents(GuiGraphics g,int x,int y,float delta);
}
