package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
/** Keeps account entitlement checks and reports success only after a matching service acknowledgement. */
public final class RedeemScreen extends SolidScreen {
 private String value="",message="";private boolean waiting;private SolidButton redeem;
 public RedeemScreen(Screen parent){super("Redeem code","dialog",parent);}
 static boolean eligible(){var v=SocialBridge.view;return !System.getProperty("unbox.accountKind","local").equals("local")&&SocialBridge.flag(v,"connected")&&v.has("self")&&SocialBridge.text(v.getAsJsonObject("self"),"kind").equals("microsoft");}
 @Override protected void build(){themePicker();text("Redeem code",182,105,800,30,SolidTheme.text());button(125,100,45,45,"←",SolidTheme.card(1),this::onClose);text("For "+minecraft.getUser().getName(),420,260,600,24,SolidTheme.text());EditBox code=field(432,330,576,"Redemption code",value,s->value=s);code.setEditable(!waiting);code.setMaxLength(100);redeem=button(720,404,300,52,waiting?"Redeeming…":"Redeem",SolidTheme.accent(),()->{if(eligible()&&!value.isBlank()&&SocialBridge.command("redeem",value)){waiting=true;value="";message="Redeeming…";rebuild();}});button(420,404,280,52,"Back",SolidTheme.card(1),this::onClose);text(eligible()?message:"Sign in with a Microsoft account in the launcher",420,490,650,22,SolidTheme.muted());}
 @Override public void tick(){redeem.active=eligible()&&!waiting&&!value.isBlank();if(waiting&&!SocialBridge.commandPending()){waiting=false;message=SocialBridge.commandSucceeded()?"Redeemed. Assets and permissions updated.":SocialBridge.message;rebuild();}}
}
