package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses the player's actual bindings, including remapped movement keys. */
final class KeystrokesHud {
    private static final long[] released=new long[7];
    private static final boolean[] down=new boolean[7];
    static int[] size(){int s=UnboxClient.number("keystrokes.size",24,18,36),gap=UnboxClient.number("keystrokes.gap",3,0,8),h=0;
        if(ClientHud.flag("keystrokes.movement",true))h+=2*(s+gap);
        if(ClientHud.flag("keystrokes.mouse",true))h+=s+gap;
        if(ClientHud.flag("keystrokes.space",true))h+=s+gap;
        return new int[]{s*3+gap*2,Math.max(0,h-gap)};
    }
    static float light(int index,boolean pressed){long now=System.nanoTime();if(down[index]&&!pressed)released[index]=now;down[index]=pressed;if(pressed)return 1;int duration=UnboxClient.number("keystrokes.fade",180,0,500);return duration==0?0:Math.max(0,1-(now-released[index])/(duration*1_000_000f));}
    static void draw(GuiGraphicsExtractor g,boolean preview){var m=Minecraft.getInstance();int s=UnboxClient.number("keystrokes.size",24,18,36),gap=UnboxClient.number("keystrokes.gap",3,0,8),w=s*3+gap*2,y=0;
        if(ClientHud.flag("keystrokes.movement",true)){KeyMapping[] keys={m.options.keyUp,m.options.keyLeft,m.options.keyDown,m.options.keyRight};String[] arrows={"^","<","v",">"};for(int i=0;i<4;i++){int x=i==0?s+gap:(i-1)*(s+gap),ky=i==0?0:s+gap;String label=ClientHud.flag("keystrokes.arrows",false)?arrows[i]:keys[i].getTranslatedKeyMessage().getString();key(g,x,ky,s,s,label,light(i,m.screen==null&&keys[i].isDown()));}y+=2*(s+gap);}
        if(ClientHud.flag("keystrokes.mouse",true)){for(int i=0;i<2;i++){int bw=(w-gap)/2;String label=ClientHud.flag("keystrokes.cps",true)?ClientHud.clicks(i)+" CPS":i==0?"LMB":"RMB";key(g,i*(bw+gap),y,i==1?w-bw-gap:bw,s,label,UnboxClient.clickLight(i));}y+=s+gap;}
        if(ClientHud.flag("keystrokes.space",true))key(g,0,y,w,s,m.options.keyJump.getTranslatedKeyMessage().getString(),light(4,m.screen==null&&m.options.keyJump.isDown()));
    }
    private static void key(GuiGraphicsExtractor g,int x,int y,int w,int h,String text,float press){int idle=(ClientHud.color("keystrokes.idleColor","#191E24")&0xffffff)|(UnboxClient.number("keystrokes.opacity",40,0,100)*255/100<<24);
        g.fill(x,y,x+w,y+h,PanelStyle.mix(idle,ClientHud.color("keystrokes.pressColor","#FFFFFF"),press));if(ClientHud.flag("keystrokes.border",true))g.outline(x,y,w,h,ClientHud.color("keystrokes.borderColor","#FFFFFF"));
        int color=PanelStyle.mix(ClientHud.color("keystrokes.color","#F4FFF6"),ClientHud.color("keystrokes.pressText","#171D23"),press);float fit=Math.min(1,(w-4f)/Math.max(1,PanelStyle.hudWidth(text)));
        g.pose().pushMatrix();g.pose().translate(x+w/2f,y+(h-10*fit)/2f);g.pose().scale(fit);PanelStyle.hudCenter(g,text,0,0,color,ClientHud.flag("keystrokes.shadow",true));g.pose().popMatrix();
    }
}
