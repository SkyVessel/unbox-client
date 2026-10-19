package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Cancellable preparation keeps the current world running until a route is ready. */
final class JoinProgressScreen extends Screen {
 private final Screen parent;
 JoinProgressScreen(Screen parent){super(Component.literal("Joining friend"));this.parent=parent;}
 @Override protected void init(){addRenderableWidget(Button.builder(Component.literal("Cancel"),button->onClose()).bounds(width/2-60,height/2+35,120,20).build());}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void onClose(){SocialBridge.cancelJoin();minecraft.setScreen(parent);}
 @Override public void tick(){if(!SocialBridge.joining())minecraft.setScreen(parent);}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float delta){if(minecraft.level==null)extractPanorama(g,delta);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float delta){
  PanelStyle.center(g,"JOINING FRIEND",width/2,height/2-35,PanelStyle.TEXT);
  g.centeredText(font,Component.literal(SocialBridge.message),width/2,height/2-8,0xffdddddd);
  super.extractRenderState(g,x,y,delta);
 }
}
