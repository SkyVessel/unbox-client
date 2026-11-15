package dev.unbox.unpvp;
import net.minecraft.client.gui.*;
final class ConnectionScreen extends GuiScreen {
 private final GuiScreen parent;ConnectionScreen(GuiScreen p){parent=p;}
 @Override public void initGui(){buttonList.add(new GuiButton(0,width/2-105,height-30,100,20,"Save report"));buttonList.add(new GuiButton(1,width/2+5,height-30,100,20,"Back"));}
 @Override protected void actionPerformed(GuiButton b){if(b.id==0)JoinDiagnostics.save();else mc.displayGuiScreen(parent);}
 @Override public void drawScreen(int x,int y,float partial){drawDefaultBackground();drawCenteredString(fontRendererObj,"CONNECTION",width/2,15,0xffffff);int yy=42;for(String line:JoinDiagnostics.lines())for(String part:fontRendererObj.listFormattedStringToWidth(line,width-30)){fontRendererObj.drawString(part,15,yy,0xdddddd);yy+=12;}drawCenteredString(fontRendererObj,JoinDiagnostics.exportStatus,width/2,height-45,0xaaaaaa);super.drawScreen(x,y,partial);}
 @Override protected void keyTyped(char c,int key){if(key==1)mc.displayGuiScreen(parent);else super.keyTyped(c,key);}
 @Override public boolean doesGuiPauseGame(){return false;}
}
