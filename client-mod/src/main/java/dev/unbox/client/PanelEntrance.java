package dev.unbox.client;

import net.minecraft.client.Minecraft;

/** One short entrance per screen visit; never restarts on a settings update or resize. */
final class PanelEntrance {
    private long start;
    private boolean settled;
    private float offset;
    void reset(){start=0;settled=false;offset=0;}
    static float ease(float elapsedMs){float t=Math.clamp(elapsedMs/200f,0,1);return 1-(1-t)*(1-t)*(1-t);}
    float frame(){
        if(settled)return 1;
        if(Minecraft.getInstance().options.screenEffectScale().get()==0){settled=true;offset=0;return 1;}
        if(Minecraft.getInstance().getOverlay()!=null){offset=12;return 0;}
        long now=System.nanoTime();if(start==0)start=now;
        float progress=ease((now-start)/1_000_000f);offset=12*(1-progress);settled=progress==1;return progress;
    }
    float offset(){return offset;}
}
