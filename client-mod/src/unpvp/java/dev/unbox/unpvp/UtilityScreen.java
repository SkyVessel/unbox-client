package dev.unbox.unpvp;
import net.minecraft.client.gui.GuiScreen;
/** The modern utility page layout; no synthetic recordings or connection results. */
final class UtilityScreen extends UiScreen {
    private final String page;private int x;private String notice="UI preview: measurements are not connected.";
    UtilityScreen(GuiScreen parent,String page){super(parent);this.page=page;}
    @Override boolean entrance(){return false;}
    @Override void layout(){
        if(page.equals("Performance")){int w=Math.min(115,(width-36)/5),start=(width-(5*w+24))/2;String[] names={"Record","Stop","Export","HUD: OFF","Back"};for(int i=0;i<5;i++){final int n=i;button(start+i*(w+6),height-28,w,20,names[i],()->{if(n==4)close();else notice="UI preview: measurements are not connected.";});}x=width/2-310;}
        else{button(width/2-110,height-30,100,24,"Save report",()->notice="UI preview: network diagnostics are not connected.");button(width/2+10,height-30,100,24,"Back",this::close);}
    }
    @Override void paint(float mx,float my,float partial){if(page.equals("Performance")){Painter.text("PERFORMANCE TESTER",Math.max(16,x),14,Painter.TEXT);Painter.text(notice,Math.max(16,x),48,Painter.MUTED);}else{Painter.center("CONNECTION DIAGNOSTICS",width/2,16,Painter.TEXT);Painter.text("UI preview: network diagnostics are not connected.",24,48,Painter.MUTED);}}
}
