package dev.unbox.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Codes are redeemed by the launcher's authenticated cloud identity, never by a supplied UUID. */
public final class RedeemScreen extends Screen {
 private final Screen parent;private EditBox code;private Button redeem;private boolean waiting;private String message="";
 public RedeemScreen(Screen parent){super(Component.literal("Redeem code"));this.parent=parent;}
 static boolean eligible(){var v=SocialBridge.view;return !System.getProperty("unbox.accountKind","local").equals("local")&&SocialBridge.flag(v,"connected")&&v.has("self")&&SocialBridge.text(v.getAsJsonObject("self"),"kind").equals("microsoft");}
 @Override protected void init(){int x=width/2-150,y=height/2-65;String previous=code==null?"":code.getValue();code=addRenderableWidget(new EditBox(font,x,y+40,300,24,Component.literal("Redemption code")));code.setMaxLength(100);code.setHint(Component.literal("Redemption code"));code.setValue(previous);redeem=addRenderableWidget(Button.builder(Component.literal("Redeem"),b->{if(eligible()&&!code.getValue().isBlank()&&SocialBridge.command("redeem",code.getValue())){waiting=true;code.setValue("");message="Redeeming…";}}).bounds(x+155,y+76,145,22).build());addRenderableWidget(Button.builder(Component.literal("Back"),b->onClose()).bounds(x,y+76,145,22).build());setInitialFocus(code);}
 @Override public void tick(){redeem.active=eligible()&&!waiting&&!code.getValue().isBlank();code.setEditable(!waiting);if(waiting&&!SocialBridge.commandPending()){waiting=false;message=SocialBridge.commandSucceeded()?"Redeemed. Assets and permissions updated.":SocialBridge.message;}}
 @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){int x=width/2-150,y=height/2-65;PanelStyle.label(g,"REDEEM CODE",x,y,PanelStyle.TEXT);PanelStyle.label(g,"For "+minecraft.getUser().getName()+" · Each code can be used once",x,y+21,PanelStyle.MUTED);super.extractRenderState(g,mx,my,delta);String status=eligible()?message:"Sign in with a verified Minecraft account";for(int start=0,line=0;start<status.length();start+=48,line++)PanelStyle.label(g,status.substring(start,Math.min(start+48,status.length())),x,y+114+line*12,PanelStyle.MUTED);}
 @Override public void onClose(){code.setValue("");minecraft.setScreen(parent);}@Override public boolean isPauseScreen(){return false;}
}
