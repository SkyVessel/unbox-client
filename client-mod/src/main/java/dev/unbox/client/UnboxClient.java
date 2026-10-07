package dev.unbox.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;
import java.nio.file.*;
import java.util.*;
import java.io.*;

public class UnboxClient implements ClientModInitializer {
    public static final Properties CONFIG = new Properties();
    private static final Path FILE=FabricLoader.getInstance().getConfigDir().resolve("unbox.properties");
    public static KeyMapping zoomKey;
    private static KeyMapping menuKey;
    private static int ticks;
    private static long modified=-1;
    private static final ArrayDeque<Long> LEFT=new ArrayDeque<>(),RIGHT=new ArrayDeque<>();
    private static Double priorChatScale,priorChatWidth,priorChatFocused,priorChatUnfocused,priorChatSpacing,priorChatText;
    private static boolean zoomHeld,zoomToggled;
    private static float zoomAmount;
    private static long zoomFrame;
    public static boolean enabled(String key){return Boolean.parseBoolean(CONFIG.getProperty(key,switch(key){case "fps","coordinates","armor","zoom"->"true";default->"false";}));}
    public static int number(String key,int fallback,int min,int max){try{return Math.clamp(Integer.parseInt(CONFIG.getProperty(key,""+fallback)),min,max);}catch(NumberFormatException e){return fallback;}}
    public static void set(String key,Object value){CONFIG.setProperty(key,String.valueOf(value));}
    public static void save(){try{Files.createDirectories(FILE.getParent());Path temp=FILE.resolveSibling("unbox.properties.tmp");try(var out=Files.newOutputStream(temp)){CONFIG.store(out,"Unbox Client");}Files.move(temp,FILE,StandardCopyOption.REPLACE_EXISTING);modified=Files.getLastModifiedTime(FILE).toMillis();}catch(IOException e){System.err.println("Unbox settings could not be saved: "+e.getMessage());}}
    public static void reload(){try{if(!Files.exists(FILE))return;long time=Files.getLastModifiedTime(FILE).toMillis();if(time==modified)return;Properties next=new Properties();try(var in=Files.newInputStream(FILE)){next.load(in);}CONFIG.clear();CONFIG.putAll(next);modified=time;}catch(IOException e){System.err.println("Unbox settings could not be loaded: "+e.getMessage());}}
    private static final long[] clickTime=new long[2];private static final boolean[] buttonDown=new boolean[2];
    public static void mouse(int button,int action){if(button<0||button>1)return;buttonDown[button]=action==1&&Minecraft.getInstance().screen==null;if(action==1)click(button);}
    public static float clickLight(int button){if(buttonDown[button]&&Minecraft.getInstance().screen==null)return 1;return Math.max(0,1-(System.nanoTime()-clickTime[button])/(UnboxClient.number("cps.fade",180,50,500)*1_000_000f));}
    public static void click(int button){if(Minecraft.getInstance().screen!=null||Minecraft.getInstance().player==null)return;long now=System.nanoTime();if(button>=0&&button<2)clickTime[button]=now;if(button==0)LEFT.addLast(now);if(button==1)RIGHT.addLast(now);}
    @Override public void onInitializeClient(){
        reload();
        menuKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.unbox.menu",GLFW.GLFW_KEY_RIGHT_SHIFT,KeyMapping.Category.MISC));
        zoomKey=KeyMappingHelper.registerKeyMapping(new KeyMapping("key.unbox.zoom",GLFW.GLFW_KEY_C,KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(++ticks%40==0)reload();
            UiSmokeTest.tick(client);HomeSmokeTest.tick(client);
            while(menuKey.consumeClick())if(client.screen==null)client.setScreen(new UnboxHomeScreen(true));
            long cutoff=System.nanoTime()-1_000_000_000L;while(!LEFT.isEmpty()&&LEFT.peekFirst()<cutoff)LEFT.removeFirst();while(!RIGHT.isEmpty()&&RIGHT.peekFirst()<cutoff)RIGHT.removeFirst();
            ClientHud.update(LEFT.size(),RIGHT.size());
            boolean held=zoomKey.isDown();if(ClientHud.value("zoom.mode","Hold").equals("Toggle")){if(held&&!zoomHeld&&client.screen==null)zoomToggled=!zoomToggled;}else zoomToggled=false;zoomHeld=held;
            if(!enabled("zoom")||client.player==null)zoomToggled=false;
            if(enabled("chat")){
                if(priorChatScale==null){priorChatScale=client.options.chatScale().get();priorChatWidth=client.options.chatWidth().get();priorChatFocused=client.options.chatHeightFocused().get();priorChatUnfocused=client.options.chatHeightUnfocused().get();priorChatSpacing=client.options.chatLineSpacing().get();priorChatText=client.options.chatOpacity().get();}
                client.options.chatHeightFocused().set(number("chat.focused",100,20,100)/100.0);client.options.chatHeightUnfocused().set(number("chat.unfocused",44,10,100)/100.0);client.options.chatLineSpacing().set(number("chat.spacing",0,0,100)/100.0);client.options.chatOpacity().set(number("chat.textOpacity",100,10,100)/100.0);
                double scale=number("chat.scale",100,50,100)/100.0,width=number("chat.width",100,20,100)/100.0;
                if(client.options.chatScale().get()!=scale||client.options.chatWidth().get()!=width){client.options.chatScale().set(scale);client.options.chatWidth().set(width);client.gui.getChat().rescaleChat();}
            }else if(priorChatScale!=null){client.options.chatScale().set(priorChatScale);client.options.chatWidth().set(priorChatWidth);client.options.chatHeightFocused().set(priorChatFocused);client.options.chatHeightUnfocused().set(priorChatUnfocused);client.options.chatLineSpacing().set(priorChatSpacing);client.options.chatOpacity().set(priorChatText);priorChatScale=null;client.gui.getChat().rescaleChat();}
        });
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("unbox","hud"),(g,t)->drawHud(g));
        System.out.println("[Unbox] 26.1 client modules initialized");
    }
    public static void drawHud(GuiGraphicsExtractor g){ClientHud.render(g);}
    public static boolean zooming(){var m=Minecraft.getInstance();return enabled("zoom")&&zoomKey!=null&&(ClientHud.value("zoom.mode","Hold").equals("Toggle")?zoomToggled:zoomKey.isDown())&&m.screen==null&&m.player!=null;}
    public static boolean adjustZoom(double scroll){if(!zooming()||!ClientHud.flag("zoom.scroll",true)||scroll==0)return false;set("zoom.factor",Math.clamp(number("zoom.factor",3,2,12)+(scroll>0?1:-1),2,12));return true;}
    public static float zoomFactor(){
        long now=System.nanoTime();float seconds=zoomFrame==0?0:Math.min(.1f,(now-zoomFrame)/1_000_000_000f);zoomFrame=now;
        float target=zooming()?1:0;if(!ClientHud.flag("zoom.smooth",true))zoomAmount=target;else zoomAmount+=(target-zoomAmount)*(1-(float)Math.exp(-seconds*3000/number("zoom.duration",170,50,500)));
        if(Math.abs(zoomAmount-target)<.001f)zoomAmount=target;return 1+zoomAmount*(number("zoom.factor",3,2,12)-1);
    }
    public static boolean showCrosshair(){var m=Minecraft.getInstance();return m.player!=null&&(m.options.getCameraType().isFirstPerson()||ClientHud.flag("crosshair.thirdPerson",false))&&(!m.player.isSpectator()||ClientHud.flag("crosshair.spectator",false))&&(!m.getDebugOverlay().showDebugScreen()||ClientHud.flag("crosshair.inDebug",false));}
    public static void crosshair(GuiGraphicsExtractor g){
        drawCrosshair(g,g.guiWidth()/2,g.guiHeight()/2);
        var m=Minecraft.getInstance();if(ClientHud.flag("crosshair.attack",true)&&m.player!=null){float strength=m.player.getAttackStrengthScale(0);if(strength<1){int x=g.guiWidth()/2-8,y=g.guiHeight()/2+18;g.fill(x,y,x+16,y+2,0x88000000);g.fill(x,y,x+(int)(16*strength),y+2,0xffeeeeee);}}
    }
    public static void drawCrosshair(GuiGraphicsExtractor g,int x,int y){
        int size=number("crosshair.size",5,2,20),gap=number("crosshair.gap",2,0,10),thickness=number("crosshair.thickness",1,1,5);
        int color=(ClientHud.color("crosshair.color","#FFFFFF")&0xffffff)|(number("crosshair.opacity",100,10,100)*255/100<<24);
        var m=Minecraft.getInstance();if(ClientHud.flag("crosshair.target",false)&&m.crosshairPickEntity!=null)color=(color&0xff000000)|(ClientHud.color("crosshair.targetColor","#FF7979")&0xffffff);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().rotate((float)Math.toRadians(number("crosshair.rotation",0,-180,180)));g.pose().scale(number("crosshair.scale",100,50,200)/100f);x=0;y=0;
        String shape=ClientHud.value("crosshair.shape","Cross");int half=thickness/2;int dot=number("crosshair.dotSize",1,1,7),outline=number("crosshair.outlineWidth",1,1,3),outlineColor=(color&0xff000000)|(ClientHud.color("crosshair.outlineColor","#000000")&0xffffff);
        if(shape.equals("Custom")){String pixels=CrosshairPattern.pixels();if(ClientHud.flag("crosshair.outline",true))for(int py=0;py<15;py++)for(int px=0;px<15;px++)if(pixels.charAt(py*15+px)=='1')g.fill(px-7-outline,py-7-outline,px-6+outline,py-6+outline,outlineColor);for(int py=0;py<15;py++)for(int px=0;px<15;px++)if(pixels.charAt(py*15+px)=='1')g.fill(px-7,py-7,px-6,py-6,color);g.pose().popMatrix();return;}
        if(!shape.equals("Dot")){
            stroke(g,x-gap-size,y-half,x-gap,y-half+thickness,color);stroke(g,x+gap+1,y-half,x+gap+size+1,y-half+thickness,color);
            if(!shape.equals("T"))stroke(g,x-half,y-gap-size,x-half+thickness,y-gap,color);
            stroke(g,x-half,y+gap+1,x-half+thickness,y+gap+size+1,color);
        }
        if(shape.equals("Dot")||ClientHud.flag("crosshair.dot",false))stroke(g,x-dot/2,y-dot/2,x-dot/2+dot,y-dot/2+dot,color);
        g.pose().popMatrix();
    }
    private static void stroke(GuiGraphicsExtractor g,int x,int y,int x2,int y2,int color){if(ClientHud.flag("crosshair.outline",true)){int w=number("crosshair.outlineWidth",1,1,3);g.fill(x-w,y-w,x2+w,y2+w,(color&0xff000000)|(ClientHud.color("crosshair.outlineColor","#000000")&0xffffff));}g.fill(x,y,x2,y2,color);}
}
