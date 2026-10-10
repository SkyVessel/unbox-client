package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Runs only with an explicit development flag, without loading or modifying a world. */
final class HomeSmokeTest {
    private static long last;
    private static int step;private static boolean sawEntrance;
    private static final List<String> checks=new ArrayList<>();
    private static void check(boolean value,String label){if(!value)throw new IllegalStateException(label);checks.add(label);}
    private static AbstractWidget widget(Minecraft m,String label){return m.screen.children().stream().filter(c->c instanceof AbstractWidget w&&w.getMessage().getString().equals(label)).map(c->(AbstractWidget)c).findFirst().orElseThrow();}
    private static void click(Minecraft m,String label){var w=widget(m,label);float scale=m.screen instanceof UnboxHomeScreen h?h.menuScale():1;float offset=m.screen instanceof UnboxHomeScreen h?h.menuOffsetY():0;var event=new MouseButtonEvent((w.getX()+w.getWidth()/2)*scale,(w.getY()+w.getHeight()/2+offset)*scale,new MouseButtonInfo(0,0));check(m.screen.mouseClicked(event,false),"Click "+label);}
    private static void escape(Minecraft m){m.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0));}
    private static void shot(Minecraft m,String name){Screenshot.grab(m.gameDirectory,"unbox-"+name+".png",m.getMainRenderTarget(),1,c->{});}
    static void tick(Minecraft m){
        if(!Boolean.getBoolean("unbox.homeSmoke")||m.getOverlay()!=null||m.screen==null)return;
        if(step==0&&m.screen instanceof UnboxHomeScreen h&&h.menuOffsetY()>0&&!sawEntrance){sawEntrance=true;shot(m,"title-entrance");}
        long now=System.currentTimeMillis();if(last==0){last=now;return;}if(now-last<1500)return;last=now;
        try{switch(step++){
            case 0->{check(sawEntrance,"Title visibly passes through an entrance phase");check(((UnboxHomeScreen)m.screen).menuOffsetY()==0,"Entrance settles at the final position");check(m.screen instanceof UnboxHomeScreen h&&!h.inWorld(),"Vanilla title is replaced at actual startup");check(m.player==null,"Title test has not loaded a world");shot(m,"title-home");var w=widget(m,"SINGLEPLAYER");float ratio=w.getWidth()*((UnboxHomeScreen)m.screen).menuScale()/m.screen.width;check(ratio>.18&&ratio<.21,"Title action occupies about one fifth of the screen");click(m,"SINGLEPLAYER");check(m.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen,"Singleplayer opens actual world selection");}
            case 1->{escape(m);check(m.screen instanceof UnboxHomeScreen,"World list returns to Unbox home");if(m.allowsMultiplayer()){click(m,"MULTIPLAYER");check(m.screen instanceof net.minecraft.client.gui.screens.multiplayer.SafetyScreen||m.screen instanceof net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen,"Multiplayer preserves vanilla warning / server flow");}else check(!widget(m,"MULTIPLAYER").active,"Account multiplayer restriction is preserved");}
            case 2->{if(!(m.screen instanceof UnboxHomeScreen))escape(m);click(m,"Client Mods");check(m.screen instanceof UnboxScreen,"Title opens client settings without a world");}
            case 3->{shot(m,"title-client-mods");check(!widget(m,"Edit HUD").active,"HUD editing requires a loaded world");check(ClientHud.bounds("armor",m.screen.width,m.screen.height,true).w()==0,"Armor preview is empty before item components are bound");m.screen.keyPressed(new net.minecraft.client.input.KeyEvent(org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT,0,0));check(m.screen instanceof UnboxHomeScreen,"Right Shift returns settings to Unbox title");click(m,"Game settings");check(m.screen instanceof net.minecraft.client.gui.screens.options.OptionsScreen,"Title settings button is connected");}
            case 4->{escape(m);click(m,"Skin settings");check(m.screen instanceof net.minecraft.client.gui.screens.options.SkinCustomizationScreen,"Skin button opens actual customization");}
            case 5->{escape(m);double effects=m.options.screenEffectScale().get();m.options.screenEffectScale().set(0.0);var entrance=new PanelEntrance();check(entrance.frame()==1&&entrance.offset()==0,"Zero screen effects disables entrance motion");m.options.screenEffectScale().set(effects);m.options.guiScale().set(6);m.resizeGui();}
            case 6->{shot(m,"title-gui6");click(m,"SINGLEPLAYER");check(m.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen,"Scaled title button maps input correctly");escape(m);m.options.guiScale().set(2);m.getWindow().setWindowed(1000,650);m.resizeGui();}
            case 7->{shot(m,"title-small-window");var w=widget(m,"SINGLEPLAYER");check(w.getX()>0,"Small-window actions stay on screen");m.getWindow().setWindowed(1600,900);}
            case 8->{shot(m,"title-large-window");m.setScreen(new net.minecraft.client.gui.screens.Screen(net.minecraft.network.chat.Component.literal("Return path regression")){});escape(m);check(m.screen instanceof UnboxHomeScreen,"Esc from a screen without a parent returns to Unbox home");m.setScreen(new net.minecraft.client.gui.screens.TitleScreen());check(m.screen instanceof UnboxHomeScreen,"Explicit vanilla title requests remain branded");Files.writeString(m.gameDirectory.toPath().resolve("logs/home-smoke.json"),"{\"passed\":"+checks.size()+",\"checks\":["+checks.stream().map(s->"\""+s+"\"").reduce((a,b)->a+","+b).orElse("")+"]}");m.stop();}
            default->{}
        }}catch(Exception e){throw new RuntimeException("Unbox title regression failed at step "+(step-1),e);}
    }
}
