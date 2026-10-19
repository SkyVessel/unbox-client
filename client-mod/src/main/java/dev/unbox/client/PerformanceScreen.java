package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Coarse diagnostics, kept separate from the everyday mod settings. */
final class PerformanceScreen extends Screen {
    private final Screen parent;
    PerformanceScreen(Screen parent){super(Component.literal("Unbox performance tester"));this.parent=parent;}
    @Override protected void init(){
        clearWidgets();
        int gap=6,w=Math.min(115,(width-36)/5),x=(width-(w*5+gap*4))/2,y=height-28;
        addRenderableWidget(new Control(x,y,w,"Record",PerformanceMonitor::start));
        addRenderableWidget(new Control(x+w+gap,y,w,"Stop",PerformanceMonitor::stop));
        addRenderableWidget(new Control(x+(w+gap)*2,y,w,"Export",PerformanceMonitor::export));
        addRenderableWidget(new Control(x+(w+gap)*3,y,w,PerformanceMonitor.overlay?"HUD: on":"HUD: off",()->{PerformanceMonitor.overlay=!PerformanceMonitor.overlay;init();}));
        addRenderableWidget(new Control(x+(w+gap)*4,y,w,"Back",this::onClose));
    }
    private static final class Control extends Button {
        Control(int x,int y,int w,String text,Runnable action){super(x,y,w,20,PanelStyle.text(text),b->action.run(),DEFAULT_NARRATION);}
        @Override protected void extractContents(GuiGraphicsExtractor g,int mx,int my,float delta){
            if(isHoveredOrFocused())PanelStyle.round(g,getX(),getY(),getWidth(),getHeight(),4,0x99515b72);
            PanelStyle.center(g,getMessage().getString(),getX()+getWidth()/2,getY()+5,PanelStyle.TEXT);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        float scale=Math.min(1f,Math.min(width/650f,(height-40)/370f));int x=(int)(width/scale-620)/2,y=14;
        g.pose().pushMatrix();g.pose().scale(scale);
        PanelStyle.label(g,"PERFORMANCE TESTER",x,y,PanelStyle.TEXT);y+=25;
        for(String line:PerformanceMonitor.lines()){PanelStyle.hudLabel(g,line,x,y,0xffeeeeee,true);y+=14;}
        y+=8;PanelStyle.hudLabel(g,"For A/B: same world, view, distance and FPS cap; warm up, record 60s.",x,y,0xffa9c8ff,true);
        PanelStyle.hudLabel(g,"Back / Esc keeps recording. Stop preserves the sample; Record resets it.",x,y+14,0xffa9c8ff,true);
        if(!PerformanceMonitor.exportStatus.isEmpty())PanelStyle.hudLabel(g,PerformanceMonitor.exportStatus,x,y+30,0xffa9c8ff,true);
        g.pose().popMatrix();super.extractRenderState(g,mx,my,delta);
    }
}
