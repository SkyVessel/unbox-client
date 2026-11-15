package dev.unbox.unpvp;
import net.minecraft.client.gui.GuiScreen;
/** Existing Unbox controls, explicit session-only consent. */
final class FeedbackScreen extends UiScreen {
 private int x,y,w;
 FeedbackScreen(GuiScreen parent){super(parent);}
 @Override void layout(){w=Math.min(450,width-32);x=(width-w)/2;y=Math.max(16,(height-230)/2);button(x+16,y+174,w-32,24,Feedback.active()?"Stop & discard":"Start recording",()->{if(Feedback.active())Feedback.discard();else Feedback.start();initGui();}).framed=true;button(x+16,y+204,w-32,22,"Back",this::close);}
 @Override void paint(float mx,float my,float partial){Painter.round(x,y,w,234,12,SolidTheme.panel());Painter.text("FEEDBACK",x+16,y+15,Painter.TEXT);String[] lines={"Record connection diagnostics for this game session.","Includes your account ID, server address, connection route,","packet timing, frame timing and Java GC measurements.","No video, chat, passwords or gameplay packet contents.","Uploads once on exit through the launcher; keep it open.","Private reports expire after 7 days. Default: off each launch."};for(int i=0;i<lines.length;i++)Painter.text(lines[i],x+16,y+43+i*15,Painter.MUTED);Painter.text(Feedback.status,x+16,y+146,Feedback.active()?(SolidTheme.index()==2?0xff88adff:0xff365fc4):Painter.MUTED);}
}
