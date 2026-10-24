package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;
/** Confirm before downloading a changed environment or closing the current game. */
final class JoinDecisionScreen extends Screen {
 private final Screen parent;private final List<String> lines;private final Runnable yes,no;private boolean answered;private CompletableFuture<Boolean> response;
 private static volatile CompletableFuture<Boolean> pending;
 JoinDecisionScreen(Screen parent,String title,List<String> lines,Runnable yes,Runnable no){super(Component.literal(title));this.parent=parent;this.lines=lines;this.yes=yes;this.no=no;}
 static CompletableFuture<Boolean> confirmFiles(String summary){var result=new CompletableFuture<Boolean>();pending=result;Minecraft mc=Minecraft.getInstance();mc.execute(()->{if(result.isDone())return;Screen previous=mc.screen;SocialBridge.message="Shared files differ. Waiting for your confirmation…";var screen=new JoinDecisionScreen(previous,"Shared files are different",List.of(summary,"Synchronize, restart, and join automatically?","Your personal profile and key bindings are kept."),()->{pending=null;result.complete(true);mc.setScreen(previous);},()->{pending=null;result.complete(false);SocialBridge.cancelJoin();mc.setScreen(new MultiplayerHubScreen(new UnboxHomeScreen(false)));});screen.response=result;mc.setScreen(screen);});return result;}
 static void cancelPending(){var p=pending;pending=null;if(p!=null)p.complete(false);}
 private void answer(boolean value){if(answered)return;answered=true;if(value)yes.run();else no.run();}
 @Override protected void init(){clearWidgets();int y=height/2+40;addRenderableWidget(new SocialScreenButton(width/2-130,y,160,"Restart and join",()->answer(true)));addRenderableWidget(new SocialScreenButton(width/2+36,y,94,"Cancel",()->answer(false)));}
 @Override public void onClose(){answer(false);}
 @Override public boolean isPauseScreen(){return false;}
 @Override public void tick(){var p=response;if(p!=null&&p.isDone()&&!answered){answered=true;minecraft.setScreen(parent);}}
 @Override public void extractBackground(GuiGraphicsExtractor g,int x,int y,float d){if(minecraft.level==null)extractPanorama(g,d);extractBlurredBackground(g);}
 @Override public void extractRenderState(GuiGraphicsExtractor g,int x,int y,float d){PanelStyle.center(g,getTitle().getString(),width/2,height/2-58,PanelStyle.TEXT);int yy=height/2-32;for(String line:lines){for(var part:font.split(Component.literal(line),Math.min(510,width-32))){g.centeredText(font,part,width/2,yy,PanelStyle.MUTED);yy+=12;}}super.extractRenderState(g,x,y,d);}
}
