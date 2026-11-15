package dev.unbox.client;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
public class LegacyEditBox extends net.minecraft.client.gui.components.EditBox {private boolean textShadow=true;public LegacyEditBox(Font font,int x,int y,int w,int h,Component label){super(font,x,y,w,h,label);}public void setTextShadow(boolean value){textShadow=value;}public boolean textShadow(){return textShadow;}}
