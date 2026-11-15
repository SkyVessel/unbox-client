package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.*;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import org.lwjgl.opengl.GL11;

/** Drawing samples only. Never registered as an in-game HUD or camera hook. */
final class Preview {
    static float unit(){return Math.min(1f,2f*RetinaDisplay.scale()/new ScaledResolution(Minecraft.getMinecraft()).getScaleFactor());}
    private static int textWidth(String s){return Minecraft.getMinecraft().fontRendererObj.getStringWidth(s);}
    static void pixel(String s,float x,float y,int c){pixel(s,x,y,c,UiState.flag("preview.shadow",true));}
    static void pixel(String s,float x,float y,int c,boolean shadow){
        GL11.glEnable(GL11.GL_TEXTURE_2D);GlStateManager.bindTexture(0);GlStateManager.resetColor();
        Minecraft.getMinecraft().fontRendererObj.drawString(s,x,y,c,shadow);
        GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
    }
    static int[] size(String id){if(ExtraHud.IDS.contains(id))return ExtraHud.size(id);if(LiveHud.IDS.contains(id))return LiveHud.size(id);int p=UiState.number(id+".padding",4,0,12);if(id.equals("minimap"))return new int[]{128,143};if(id.equals("keystrokes")){int key=UiState.number("keystrokes.size",24,18,36),gap=UiState.number("keystrokes.gap",3,0,8);return new int[]{key*3+gap*2,key*3+gap*2+17};}if(id.equals("cps")){int w=UiState.number("cps.width",48,36,80);return new int[]{UiState.get("cps.mode","Both").equals("Both")?w*2+UiState.number("cps.spacing",4,0,16):w,UiState.number("cps.height",32,28,48)};}if(id.equals("armor"))return UiState.get("armor.layout","Vertical").equals("Vertical")?new int[]{p*2+63,p*2+5*(22+UiState.number("armor.spacing",4,0,12))}:new int[]{p*2+5*48,p*2+46};if(id.equals("coordinates"))return new int[]{76+p*2,(UiState.get("coordinates.layout","Stacked").equals("Stacked")?4:2)*12+p*2};return new int[]{textWidth(id.equals("fps")?"240 FPS":"42 ms")+p*2,12+p*2};}
    static float factor(String id){return UiState.number(id+".scale",100,50,200)/100f*unit();}
    static float[] bounds(String id,int width,int height){int[] size=size(id);float f=factor(id),u=unit(),w=size[0]*f,h=size[1]*f;int maxX=Math.max(0,Math.round((width-w)/u)),maxY=Math.max(0,Math.round((height-h)/u));int defaultX=id.equals("scoreboard")?maxX-12:id.equals("potions")?maxX-12:id.equals("keystrokes")?150:12;int defaultY=id.equals("fps")?12:id.equals("cps")?48:id.equals("ping")?88:id.equals("coordinates")?120:id.equals("armor")?190:id.equals("potions")?12:id.equals("scoreboard")?Math.max(70,maxY/3):id.equals("keystrokes")?maxY-42:164;return new float[]{UiState.number(id+".x",Math.max(0,defaultX),0,maxX)*u,UiState.number(id+".y",Math.max(0,defaultY),0,maxY)*u,w,h};}

    static void inPanel(String id,int x,int y,int w,int h){int[] s=size(id);float f=factor(id),fit=Math.min(1.8f,Math.min((w-28f)/Math.max(1,s[0]*f),(h-90f)/Math.max(1,s[1]*f)));Painter.clip(x+8,y+36,w-16,h-74);GL11.glPushMatrix();GL11.glTranslatef(x+(w-s[0]*f*fit)/2,y+60,0);GL11.glScalef(fit,fit,1);hud(id,0,0);GL11.glPopMatrix();Painter.unclip();}
    static void hud(String id,float x,float y){
        if(ExtraHud.IDS.contains(id)){ExtraHud.draw(id,x,y,true);return;}
        if(LiveHud.IDS.contains(id)){LiveHud.draw(id,x,y,true);return;}
        int[] s=size(id);int p=UiState.number(id+".padding",4,0,12),color=UiState.color(id+".color","#F4F6F8");GL11.glPushMatrix();GL11.glTranslatef(x,y,0);GL11.glScalef(factor(id),factor(id),1);
        if(!id.equals("fps")&&!id.equals("cps")&&!id.equals("keystrokes")&&UiState.flag(id+".background",false))Painter.round(0,0,s[0],s[1],UiState.flag(id+".rounded",true)?4:0,(UiState.number(id+".opacity",60,0,100)*255/100<<24)|(UiState.color(id+".backgroundColor","#191E24")&0xffffff));
        if(id.equals("fps"))pixel(UiState.flag("fps.label",true)?"240 FPS":"240",p,p,color);
        else if(id.equals("ping"))pixel(UiState.flag("ping.label",true)?"42 ms":"42",p,p,color);
        else if(id.equals("coordinates")){String[] lines=UiState.get("coordinates.layout","Stacked").equals("Stacked")?new String[]{"X  9","Y  121","Z  3","NORTH"}:new String[]{"9 / 121 / 3","NORTH"};for(int i=0;i<lines.length;i++)pixel(lines[i],p,p+i*12,color);}
        else if(id.equals("cps")){int bw=UiState.number("cps.width",48,36,80),bh=s[1],gap=UiState.number("cps.spacing",4,0,16),n=0;for(int b=0;b<2;b++){String mode=UiState.get("cps.mode","Both");if(mode.equals("Left")&&b==1||mode.equals("Right")&&b==0)continue;int xx=n++*(bw+gap);Painter.outline(xx,0,bw,bh,0xffffffff);String label=b==0?"LCPS":"RCPS";pixel(label,xx+(bw-textWidth(label))/2,4,color);pixel("0",xx+(bw-textWidth("0"))/2,bh-14,color);}}
        else if(id.equals("keystrokes")){int k=UiState.number("keystrokes.size",24,18,36),gap=UiState.number("keystrokes.gap",3,0,8);String[] labels={"W","A","S","D","LMB","RMB","SPACE"};for(int i=0;i<7;i++){int xx=i==0?k+gap:i<4?(i-1)*(k+gap):i<6?(i-4)*(s[0]+gap)/2:0,yy=i==0?0:i<4?k+gap:i<6?2*(k+gap):3*(k+gap),ww=i<4?k:i<6?(s[0]-gap)/2:s[0],hh=i==6?12:k;Painter.round(xx,yy,ww,hh,3,(UiState.number("keystrokes.opacity",40,0,100)*255/100<<24)|(UiState.color("keystrokes.idleColor","#191E24")&0xffffff));if(UiState.flag("keystrokes.border",true))Painter.outline(xx,yy,ww,hh,UiState.color("keystrokes.borderColor","#FFFFFF"));pixel(labels[i],xx+(ww-textWidth(labels[i]))/2,yy+(hh-8)/2,color);}}
        else if(id.equals("armor")){
            ItemStack[] items={new ItemStack(Items.diamond_helmet),new ItemStack(Items.diamond_chestplate),new ItemStack(Items.diamond_leggings),new ItemStack(Items.diamond_boots),new ItemStack(Items.diamond_sword)};boolean horizontal=UiState.get("armor.layout","Vertical").equals("Horizontal");String[] keys={"helmet","chestplate","leggings","boots","hand"};int n=0;
            for(int i=0;i<5;i++){if(!UiState.flag("armor."+keys[i],true))continue;int xx=p+(horizontal?n*48:0),yy=p+(horizontal?0:n*(22+UiState.number("armor.spacing",4,0,12)));n++;GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glEnable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(true);GlStateManager.bindTexture(0);GlStateManager.resetColor();GlStateManager.enableRescaleNormal();RenderHelper.enableGUIStandardItemLighting();Minecraft.getMinecraft().getRenderItem().renderItemAndEffectIntoGUI(items[i],xx,yy);RenderHelper.disableStandardItemLighting();GlStateManager.disableRescaleNormal();GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);int tx=horizontal?xx:xx+21,ty=horizontal?yy+21:yy+2;String fmt=UiState.get("armor.format","Remaining");if(!fmt.equals("Off"))pixel(fmt.equals("Percentage")?"100%":fmt.equals("Both")?items[i].getMaxDamage()+" (100%)":fmt.equals("Remaining / Max")?items[i].getMaxDamage()+" / "+items[i].getMaxDamage():""+items[i].getMaxDamage(),tx,ty,color);if(UiState.flag("armor.bar",true))Painter.fill(tx,ty+14,28,2,0xffeef0f4);}
        }else if(id.equals("minimap")){Painter.fill(0,0,128,128,0xff22272c);Painter.outline(1,1,126,126,0x555c6670);Painter.icon("minimap",48,45,32,Painter.MUTED);pixel("Map preview",29,92,color);}
        GL11.glPopMatrix();
    }
    static void crosshair(float x,float y){
        int color=UiState.on("crosshair")&&UiState.flag("crosshair.target",false)&&Minecraft.getMinecraft().objectMouseOver!=null&&Minecraft.getMinecraft().objectMouseOver.entityHit!=null?UiState.color("crosshair.targetColor","#FF7979"):UiState.color("crosshair.color","#FFFFFF");color=(UiState.number("crosshair.opacity",100,0,100)*255/100<<24)|(color&0xffffff);int size=UiState.number("crosshair.size",5,2,20),gap=UiState.number("crosshair.gap",2,0,10),t=UiState.number("crosshair.thickness",1,1,5),dot=UiState.number("crosshair.dotSize",1,1,7);String shape=UiState.get("crosshair.shape","Cross");GL11.glPushMatrix();GL11.glTranslatef(x,y,0);GL11.glRotatef(UiState.number("crosshair.rotation",0,-180,180),0,0,1);float scale=UiState.number("crosshair.scale",100,50,200)/100f;GL11.glScalef(scale,scale,1);
        if(shape.equals("Custom")){String p=Pattern.pixels();for(int yy=0;yy<15;yy++)for(int xx=0;xx<15;xx++)if(p.charAt(yy*15+xx)=='1')stroke(xx-7,yy-7,1,1,color);}
        else if(!shape.equals("Dot")){stroke(-gap-size,-t/2,size,t,color);stroke(gap,-t/2,size,t,color);stroke(-t/2,gap,t,size,color);if(!shape.equals("T"))stroke(-t/2,-gap-size,t,size,color);}
        if(shape.equals("Dot")||UiState.flag("crosshair.dot",false))stroke(-dot/2,-dot/2,dot,dot,color);GL11.glPopMatrix();
    }
    private static void stroke(float x,float y,float w,float h,int color){if(UiState.flag("crosshair.outline",true)){int o=UiState.number("crosshair.outlineWidth",1,1,3);Painter.round(x-o,y-o,w+o*2,h+o*2,0,UiState.color("crosshair.outlineColor","#000000"));}Painter.round(x,y,w,h,0,color);}
    static void chat(float x,float y,float w){Painter.clip(x,y,w,80);GL11.glPushMatrix();GL11.glTranslatef(x,y,0);float scale=UiState.number("chat.scale",100,50,100)/100f;GL11.glScalef(scale,scale,1);int cw=(int)(w/scale*UiState.number("chat.width",100,20,100)/100f),spacing=12+UiState.number("chat.spacing",0,0,100)/8;String[] lines={"<Alex> Anyone joining?","<Steve> On my way!","Alex joined the game"};for(int i=0;i<3;i++){if(UiState.flag("chat.background",false))Painter.fill(0,i*spacing,cw,spacing,(UiState.number("chat.opacity",50,0,100)*255/100<<24)|(UiState.color("chat.color","#000000")&0xffffff));Painter.text(lines[i],4,i*spacing+2,(UiState.number("chat.textOpacity",100,10,100)*255/100<<24)|0xffffff);}GL11.glPopMatrix();Painter.unclip();}
}
