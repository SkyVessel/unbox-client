package dev.unbox.client;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.options.SkinCustomizationScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** A small, branded entry layer. The world's content stays visible around it. */
public final class UnboxHomeScreen extends Screen {
    private final boolean inWorld;
    private final PanelEntrance entrance=new PanelEntrance();
    public float menuOffsetY(){return entrance.offset();}
    @Override public void added(){entrance.reset();}
    private float scale=1;
    private int logicalWidth,logicalHeight,centerX,logoY;
    private String draggingHud;
    private int dragX,dragY;
    public UnboxHomeScreen(boolean inWorld){super(Component.literal(inWorld?"Unbox quick menu":"Unbox Client"));this.inWorld=inWorld;}
    public float menuScale(){return scale;}
    public boolean inWorld(){return inWorld;}
    @Override protected void init(){
        clearWidgets();scale=.85f*Math.min(width/960f,height/540f);logicalWidth=Math.round(width/scale);logicalHeight=Math.round(height/scale);centerX=logicalWidth/2;logoY=logicalHeight/2-108;
        if(inWorld){
            add(centerX-80,logoY+132,160,38,"MODS","",()->minecraft.setScreen(new UnboxScreen(this)));
            add(centerX-128,logoY+132,40,38,"Edit HUD","move",()->{var screen=new UnboxScreen(this);minecraft.setScreen(screen);screen.editHud();});
            add(centerX+88,logoY+132,40,38,"Game settings","settings",()->minecraft.setScreen(new OptionsScreen(this,minecraft.options,false)));
            add(logicalWidth-45,15,30,28,"Return to game","close",this::onClose);
        }else{
            add(centerX-112,logoY+130,224,28,"SINGLEPLAYER","",()->minecraft.setScreen(new SelectWorldScreen(this)));
            GlassButton multiplayer=add(centerX-112,logoY+165,224,28,"MULTIPLAYER","",()->minecraft.setScreen(minecraft.options.skipMultiplayerWarning?new JoinMultiplayerScreen(this):new SafetyScreen(this)));
            multiplayer.active=minecraft.allowsMultiplayer();if(!multiplayer.active)multiplayer.setTooltip(Tooltip.create(Component.literal("Multiplayer is unavailable for this account.")));
            add(logicalWidth-45,15,30,28,"Quit game","close",()->minecraft.stop());
            add(centerX-62,logicalHeight-49,36,29,"Client Mods","mods",()->minecraft.setScreen(new UnboxScreen(this)));
            add(centerX-18,logicalHeight-49,36,29,"Skin settings","armor",()->minecraft.setScreen(new SkinCustomizationScreen(this,minecraft.options)));
            add(centerX+26,logicalHeight-49,36,29,"Game settings","settings",()->minecraft.setScreen(new OptionsScreen(this,minecraft.options,false)));
        }
    }
    private GlassButton add(int x,int y,int w,int h,String label,String icon,Runnable action){return addRenderableWidget(new GlassButton(x,y,w,h,label,icon,action));}
    @Override public void extractBackground(GuiGraphicsExtractor g,int mx,int my,float delta){if(inWorld)extractBlurredBackground(g);else {extractPanorama(g,delta);extractBlurredBackground(g);}g.fill(0,0,width,height,inWorld?0x30141416:0x60141416);}
    @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){
        if(inWorld)for(String id:ClientHud.IDS){if(!UnboxClient.enabled(id))continue;var b=ClientHud.bounds(id,width,height,true);ClientHud.draw(g,id,b.x(),b.y(),true);if(b.contains(mx,my)){g.outline(b.x()-2,b.y()-2,b.w()+4,b.h()+4,PanelStyle.ACCENT);PanelStyle.label(g,ClientHud.flag(id+".locked",false)?"Locked":"Drag to move",b.x(),b.y()+b.h()+3,PanelStyle.ACCENT);}}
        float opacity=entrance.frame();
        g.pose().pushMatrix();g.pose().scale(scale);g.pose().translate(0,menuOffsetY());PanelStyle.opacity(opacity);
        try {PanelStyle.icon(g,"logo",centerX-40,logoY,80,0xfff5f8fa);
        PanelStyle.center(g,"U N B O X   C L I E N T",centerX,logoY+93,PanelStyle.TEXT);
        if(!inWorld)PanelStyle.label(g,"Unbox Client  /  Minecraft 26.1",16,logicalHeight-24,0xffa5b2b9);
        for(var child:children())if(child instanceof AbstractWidget widget)widget.extractRenderState(g,(int)(mx/scale),(int)(my/scale-menuOffsetY()),delta);
        }finally{PanelStyle.opacity(1);g.pose().popMatrix();}
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){if(super.mouseClicked(new MouseButtonEvent(e.x()/scale,e.y()/scale-menuOffsetY(),e.buttonInfo()),doubled))return true;
        if(inWorld&&e.button()==0)for(String id:ClientHud.IDS.reversed()){if(!UnboxClient.enabled(id)||ClientHud.flag(id+".locked",false))continue;var b=ClientHud.bounds(id,width,height,true);if(b.contains(e.x(),e.y())){draggingHud=id;dragX=(int)e.x()-b.x();dragY=(int)e.y()-b.y();return true;}}return false;}
    @Override public boolean mouseDragged(MouseButtonEvent e,double dx,double dy){if(draggingHud!=null){var b=ClientHud.bounds(draggingHud,width,height,true);UnboxClient.set(draggingHud+".x",Math.round(Math.clamp((int)e.x()-dragX,0,Math.max(0,width-b.w()))/ClientHud.unit()));UnboxClient.set(draggingHud+".y",Math.round(Math.clamp((int)e.y()-dragY,0,Math.max(0,height-b.h()))/ClientHud.unit()));return true;}return super.mouseDragged(e,dx,dy);}
    @Override public boolean mouseReleased(MouseButtonEvent e){if(draggingHud!=null){draggingHud=null;UnboxClient.save();return true;}return super.mouseReleased(new MouseButtonEvent(e.x()/scale,e.y()/scale-menuOffsetY(),e.buttonInfo()));}
    @Override public boolean keyPressed(KeyEvent e){if(inWorld&&e.key()==GLFW.GLFW_KEY_RIGHT_SHIFT){onClose();return true;}return super.keyPressed(e);}
    @Override public void onClose(){if(inWorld){UnboxClient.save();minecraft.setScreen(null);}}
    @Override public boolean shouldCloseOnEsc(){return inWorld;}
    @Override public boolean isPauseScreen(){return false;}
    private static final class GlassButton extends AbstractWidget {
        private final String icon;private final Runnable action;private final PanelStyle.Motion hover=new PanelStyle.Motion();
        GlassButton(int x,int y,int w,int h,String text,String icon,Runnable action){super(x,y,w,h,PanelStyle.text(text));this.icon=icon;this.action=action;setTooltip(Tooltip.create(Component.literal(text)));}
        @Override protected void extractWidgetRenderState(GuiGraphicsExtractor g,int mx,int my,float delta){float amount=hover.to(isHoveredOrFocused()?1:0);PanelStyle.round(g,getX()-1,getY()-1,width+2,height+2,5,PanelStyle.mix(0x407e7e82,icon.equals("close")?0xffff9098:0xff9abaff,amount));PanelStyle.round(g,getX(),getY(),width,height,4,PanelStyle.mix(0x702a2a2e,icon.equals("close")?0xc5743942:0xc5465270,amount));int color=active?PanelStyle.TEXT:0xff6d777e;if(icon.isEmpty())PanelStyle.center(g,getMessage().getString(),getX()+width/2,getY()+(height-10)/2,color);else PanelStyle.icon(g,icon,getX()+width/2-9,getY()+height/2-9,18,color);}
        @Override public void onClick(MouseButtonEvent e,boolean doubled){action.run();}
        @Override public boolean keyPressed(KeyEvent e){if(e.isConfirmation()&&active){action.run();return true;}return super.keyPressed(e);}
        @Override protected void updateWidgetNarration(NarrationElementOutput out){defaultButtonNarrationText(out);}
    }
}
