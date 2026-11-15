package dev.unbox.unpvp;
import net.minecraft.client.gui.GuiScreen;
final class RedeemScreen extends UiScreen {
    private int x,y;private String message="",code="";
    RedeemScreen(GuiScreen parent){super(parent);}
    @Override boolean entrance(){return false;}
    @Override boolean blurred(){return false;}
    @Override void layout(){x=width/2-150;y=height/2-65;field(x,y+40,300,24,code,"Redemption code",s->code=s);button(x,y+76,145,24,"Back",this::close);button(x+155,y+76,145,24,"Redeem",()->{if(code.trim().isEmpty())message="Enter a redemption code";else if(SocialBridge.command("redeem",code.trim())){code="";message="Redeeming…";}});}
    @Override void paint(float mx,float my,float partial){Painter.text("REDEEM CODE",x,y,Painter.TEXT);Painter.text("For "+mc.getSession().getUsername()+" · Each code can be used once",x,y+21,Painter.MUTED);Painter.text(SocialBridge.message.isEmpty()?message:SocialBridge.message,x,y+114,Painter.MUTED);}
}
