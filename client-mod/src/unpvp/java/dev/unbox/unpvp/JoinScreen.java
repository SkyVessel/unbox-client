package dev.unbox.unpvp;
import net.minecraft.client.gui.*;
final class JoinScreen extends UiScreen {
 JoinScreen(GuiScreen parent){super(parent);}
 @Override void layout(){logicalWidth=480;logicalHeight=220;scale=Math.min(width/520f,height/260f);offset=0;logicalWidth=(int)(width/scale);logicalHeight=(int)(height/scale);controls.clear();button((logicalWidth-150)/2,(logicalHeight-220)/2+150,150,30,"Cancel",()->{SocialBridge.cancelJoin();navigate(parent);});}
 @Override void paint(float x,float y,float partial){int xx=(logicalWidth-480)/2,yy=(logicalHeight-220)/2;Painter.round(xx,yy,480,220,16,SolidTheme.panel());Painter.text("JOIN WORLD",xx+24,yy+24,Painter.TEXT);Painter.text(SocialBridge.message,xx+24,yy+80,Painter.MUTED);}
 @Override void close(){SocialBridge.cancelJoin();super.close();}
}
