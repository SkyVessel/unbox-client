package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiChat;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/** Native Unbox controls. Auto sprint is applied in vanilla player input by SprintMixin. */
final class PvpControls {
    final KeyBinding zoom=new KeyBinding("Unbox zoom",Keyboard.KEY_V,"Unbox"),look=new KeyBinding("Unbox look back",Keyboard.KEY_R,"Unbox");
    static boolean sneaking;private boolean lastSneak,lastZoom,lastLook,zooming,looking;
    private int previousView;private Object world;private float factor=3,currentFactor=1;private long frame;
    static boolean down(KeyBinding k){int n=k.getKeyCode();return n<0?n>=-100&&Mouse.isButtonDown(n+100):n>0&&n<Keyboard.KEYBOARD_SIZE&&Keyboard.isKeyDown(n);}
    void tick(){Minecraft mc=Minecraft.getMinecraft();boolean playing=mc.thePlayer!=null&&mc.theWorld!=null&&mc.currentScreen==null&&mc.inGameHasFocus;
        boolean z=playing&&down(zoom),l=playing&&down(look),n=playing&&down(mc.gameSettings.keyBindSneak);
        if(!playing||world!=mc.theWorld){sneaking=zooming=false;restoreLook();}world=mc.theWorld;
        if(playing){
            if(UiState.on("sneak")){if(n&&!lastSneak)sneaking=!sneaking;}else sneaking=false;
            if(!UiState.on("zoom"))zooming=false;else if(UiState.get("zoom.mode","Hold").equals("Hold"))zooming=z;else if(z&&!lastZoom)zooming=!zooming;
            if(z&&!lastZoom)factor=UiState.number("zoom.factor",3,2,12);
            boolean want=UiState.on("lookback")&&(UiState.get("lookback.mode","Hold").equals("Hold")?l:(l&&!lastLook?!looking:looking));
            if(want&&!looking){previousView=mc.gameSettings.thirdPersonView;mc.gameSettings.thirdPersonView=2;looking=true;mc.renderGlobal.setDisplayListEntitiesDirty();}else if(!want)restoreLook();
        }
        lastSneak=n;lastZoom=z;lastLook=l;
    }
    private void restoreLook(){if(looking){Minecraft mc=Minecraft.getMinecraft();if(mc.gameSettings.thirdPersonView==2)mc.gameSettings.thirdPersonView=previousView;looking=false;mc.renderGlobal.setDisplayListEntitiesDirty();}}
    @SubscribeEvent public void wheel(MouseEvent e){if(zooming&&UiState.flag("zoom.scroll",true)&&e.dwheel!=0){factor=UiState.clamp(factor+(e.dwheel>0?.5f:-.5f),1.5f,16);e.setCanceled(true);}}
    @SubscribeEvent public void fov(EntityViewRenderEvent.FOVModifier e){if(!zooming&&currentFactor==1){frame=0;return;}long now=System.nanoTime();float dt=frame==0?0:Math.min(.1f,(now-frame)/1e9f);frame=now;float target=zooming?factor:1;currentFactor+=(target-currentFactor)*(UiState.flag("zoom.smooth",true)?1-(float)Math.exp(-dt*5/Math.max(.05,UiState.number("zoom.duration",170,50,500)/1000.)):1);if(Math.abs(target-currentFactor)<.001)currentFactor=target;e.setFOV(e.getFOV()/currentFactor);}
    @SubscribeEvent public void overlay(RenderGameOverlayEvent.Pre e){Minecraft mc=Minecraft.getMinecraft();if(e.type!=RenderGameOverlayEvent.ElementType.CROSSHAIRS||!UiState.on("crosshair")||mc.thePlayer==null)return;
        if(mc.gameSettings.showDebugInfo&&!UiState.flag("crosshair.inDebug",false)||mc.gameSettings.thirdPersonView!=0&&!UiState.flag("crosshair.thirdPerson",false)||mc.thePlayer.isSpectator()&&!UiState.flag("crosshair.spectator",false))return;
        e.setCanceled(true);Painter.begin(1,0);try{Preview.crosshair(e.resolution.getScaledWidth()/2f,e.resolution.getScaledHeight()/2f);}finally{Painter.end();}
    }
    @SubscribeEvent public void status(RenderGameOverlayEvent.Post e){Minecraft mc=Minecraft.getMinecraft();if(e.type==RenderGameOverlayEvent.ElementType.ALL&&mc.thePlayer!=null&&mc.currentScreen==null&&!mc.gameSettings.hideGUI){String s=sneaking?"[Sneaking toggled]":UiState.on("sprint")&&mc.thePlayer.isSprinting()&&UiState.flag("sprint.indicator",true)?"[Auto sprint]":"";if(!s.isEmpty())mc.fontRendererObj.drawStringWithShadow(s,4,e.resolution.getScaledHeight()-12,0xffffff);}}
}
