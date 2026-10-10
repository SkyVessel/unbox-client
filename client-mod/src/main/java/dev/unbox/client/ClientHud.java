package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** HUD geometry is shared by the renderer, preview, and direct-manipulation editor. */
public final class ClientHud {
    public static final List<String> IDS=List.of("fps","cps","coordinates","armor","keystrokes","ping","minimap");
    private static final Map<String,List<String>> TEXT=new HashMap<>();
    private static final Map<String,Integer> TEXT_WIDTH=new HashMap<>();
    private static final EquipmentSlot[] SLOTS={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET,EquipmentSlot.MAINHAND,EquipmentSlot.OFFHAND};
    private static ItemStack[] previewItems;
    private static final String[] SLOT_KEYS={"helmet","chestplate","leggings","boots","hand","offhand"};
    private static int leftClicks,rightClicks;private static long fpsUpdate,positionUpdate;

    public record Bounds(int x,int y,int w,int h){public boolean contains(double px,double py){return px>=x&&px<=x+w&&py>=y&&py<=y+h;}}
    public static boolean flag(String key,boolean fallback){return Boolean.parseBoolean(UnboxClient.CONFIG.getProperty(key,""+fallback));}
    public static String value(String key,String fallback){return UnboxClient.CONFIG.getProperty(key,fallback);}
    public static int color(String key,String fallback){try{return 0xff000000|Integer.parseInt(value(key,fallback).replace("#",""),16);}catch(NumberFormatException e){return 0xffffffff;}}
    public static void update(int left,int right){
        Minecraft m=Minecraft.getInstance();leftClicks=left;rightClicks=right;
        int interval=switch(value("fps.interval","250 ms")){case "100 ms"->100;case "500 ms"->500;case "1000 ms"->1000;default->250;};if(System.nanoTime()-fpsUpdate>interval*1_000_000L){TEXT.put("fps",List.of(m.getFps()+(flag("fps.label",true)?" FPS":"")));fpsUpdate=System.nanoTime();}
        var connection=m.getConnection();var info=connection==null||m.player==null?null:connection.getPlayerInfo(m.player.getUUID());
        TEXT.put("ping",List.of(m.hasSingleplayerServer()?"Local":info==null?"— ms":Math.max(0,info.getLatency())+(flag("ping.label",true)?" ms":"")));
        TEXT_WIDTH.put("ping",PanelStyle.hudWidth(lines("ping").getFirst()));
        TEXT_WIDTH.put("fps",PanelStyle.hudWidth(lines("fps").getFirst()));
        if(m.player==null||System.nanoTime()-positionUpdate<100_000_000L)return;positionUpdate=System.nanoTime();
        int decimals=UnboxClient.number("coordinates.decimals",0,0,2);String f="%."+decimals+"f";
        String[] axes={String.format(Locale.ROOT,f,m.player.getX()),String.format(Locale.ROOT,f,m.player.getY()),String.format(Locale.ROOT,f,m.player.getZ())};
        boolean labels=flag("coordinates.labels",true);for(int i=0;i<3;i++)if(labels)axes[i]="XYZ".charAt(i)+"  "+axes[i];
        List<String> lines=new ArrayList<>();if(value("coordinates.layout","Stacked").equals("Stacked"))lines.addAll(Arrays.asList(axes));else lines.add(String.join(" / ",axes));
        if(flag("coordinates.direction",true))lines.add(m.player.getDirection().getName().toUpperCase(Locale.ROOT));if(flag("coordinates.biome",false))lines.add(m.level.getBiome(m.player.blockPosition()).unwrapKey().map(k->k.identifier().getPath().replace('_',' ')).orElse("Unknown biome"));
        if(flag("coordinates.dimension",false))lines.add(m.level.dimension().identifier().getPath().replace('_',' '));
        if(flag("coordinates.nether",false)){boolean nether=m.level.dimension().equals(net.minecraft.world.level.Level.NETHER);if(nether||m.level.dimension().equals(net.minecraft.world.level.Level.OVERWORLD)){double factor=nether?8:.125;lines.add((nether?"Overworld ":"Nether ")+Math.round(m.player.getX()*factor)+" / "+Math.round(m.player.getZ()*factor));}}TEXT.put("coordinates",lines);
        TEXT.forEach((id,ls)->TEXT_WIDTH.put(id,ls.stream().mapToInt(PanelStyle::hudWidth).max().orElse(40)));
    }
    static List<String> lines(String id){return TEXT.getOrDefault(id,List.of(id.equals("fps")?"240 FPS":id.equals("cps")?"0 | 0 CPS":"XYZ  0 / 64 / 0"));}
    static List<ItemStack> equipment(boolean preview){
        var m=Minecraft.getInstance();if(m.level==null)return List.of();if(preview&&previewItems==null)previewItems=new ItemStack[]{new ItemStack(Items.DIAMOND_HELMET),new ItemStack(Items.DIAMOND_CHESTPLATE),new ItemStack(Items.DIAMOND_LEGGINGS),new ItemStack(Items.DIAMOND_BOOTS),new ItemStack(Items.DIAMOND_SWORD),new ItemStack(Items.SHIELD)};List<ItemStack> items=new ArrayList<>();int count=6;
        for(int i=0;i<count;i++){if(!flag("armor."+SLOT_KEYS[i],i!=5))continue;ItemStack s=preview?previewItems[i]:m.player.getItemBySlot(SLOTS[i]);if(!s.isEmpty()||flag("armor.empty",false))items.add(s);}if(flag("armor.reverse",false))Collections.reverse(items);return items;
    }
    public static float unit(){return Math.min(1f,2f/Minecraft.getInstance().getWindow().getGuiScale());}
    public static float scale(String id){return UnboxClient.number(id+".scale",100,50,200)/100f;}
    static int[] size(String id,boolean preview){
        int p=UnboxClient.number(id+".padding",4,0,12);if(id.equals("minimap"))return new int[]{128,128};if(id.equals("keystrokes"))return KeystrokesHud.size();if(id.equals("cps")){int w=UnboxClient.number("cps.width",48,36,80);return new int[]{value("cps.mode","Both").equals("Both")?w*2+UnboxClient.number("cps.spacing",4,0,16):w,UnboxClient.number("cps.height",32,28,48)};}if(id.equals("armor")){int n=equipment(preview).size();if(n==0)return new int[]{0,0};int[] cell=armorCell(preview);return value("armor.layout","Vertical").equals("Vertical")?new int[]{p*2+cell[0],p*2+n*cell[1]}:new int[]{p*2+n*cell[0],p*2+cell[1]};}
        var m=Minecraft.getInstance();List<String> ls=lines(id);return new int[]{TEXT_WIDTH.getOrDefault(id,60)+p*2,ls.size()*12+p*2};
    }
    static String durability(ItemStack item){int left=item.getMaxDamage()-item.getDamageValue(),pct=left*100/Math.max(1,item.getMaxDamage());return switch(value("armor.format","Remaining")){case "Percentage"->pct+"%";case "Both"->left+" ("+pct+"%)";case "Remaining / Max"->left+" / "+item.getMaxDamage();case "Off"->"";default->""+left;};}
    static int[] armorCell(boolean preview){boolean horizontal=value("armor.layout","Vertical").equals("Horizontal"),names=flag("armor.names",false);int text=24;for(ItemStack item:equipment(preview)){if(item.isDamageableItem())text=Math.max(text,PanelStyle.hudWidth(durability(item)));if(names)text=Math.max(text,PanelStyle.hudWidth(item.getHoverName().getString()));}int lines=(names?12:0)+(value("armor.format","Remaining").equals("Off")?0:12)+(flag("armor.bar",true)?6:0),gap=UnboxClient.number("armor.spacing",4,0,12);return new int[]{(horizontal?Math.max(20,text):21+text)+gap,(horizontal?21+lines:Math.max(18,lines))+gap};}
    static int clicks(int button){return button==0?leftClicks:rightClicks;}
    public static Bounds bounds(String id,int width,int height,boolean preview){
        int[] sz=size(id,preview);int w=Math.round(sz[0]*scale(id)),h=Math.round(sz[1]*scale(id));float unit=unit();width=Math.round(width/unit);height=Math.round(height/unit);
        int defaultY=switch(id){case "minimap"->60;case "ping"->36;case "fps"->12;case "cps"->48;case "coordinates"->84;case "keystrokes"->320;default->164;};
        int x=UnboxClient.number(id+".x",id.equals("minimap")?Math.max(0,width-w-12):id.equals("keystrokes")?150:UnboxClient.number("hud.x",12,0,width),0,Math.max(0,width-w));
        int y=UnboxClient.number(id+".y",defaultY,0,Math.max(0,height-h));return new Bounds(Math.round(x*unit),Math.round(y*unit),Math.round(w*unit),Math.round(h*unit));
    }
    public static void render(GuiGraphicsExtractor g){
        var m=Minecraft.getInstance();if(m.player==null||m.options.hideGui)return;
        boolean editor=m.screen instanceof UnboxScreen s&&s.isEditingHud();
        // Menus have their own live previews; HUD editor deliberately shows every module.
        if(m.screen instanceof UnboxScreen||m.screen instanceof UnboxHomeScreen||m.screen instanceof WorldMap.MapScreen)return;
        for(String id:IDS){if(!editor&&!UnboxClient.enabled(id))continue;if(!editor&&m.screen instanceof ChatScreen&&!flag(id+".inChat",true))continue;if(!editor&&m.getDebugOverlay().showDebugScreen()&&!flag(id+".inDebug",false))continue;
            Bounds b=bounds(id,g.guiWidth(),g.guiHeight(),editor);draw(g,id,b.x,b.y,editor);
        }
        if(UnboxClient.enabled("sprint")&&flag("sprint.indicator",true)&&m.player.isSprinting())g.text(m.font,"Sprinting",8,g.guiHeight()-38,0xff86aaff);
    }
    public static void draw(GuiGraphicsExtractor g,String id,int x,int y,boolean preview){
        var m=Minecraft.getInstance();int[] sz=size(id,preview);if(sz[0]==0)return;int p=UnboxClient.number(id+".padding",4,0,12);
        g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale(id)*unit());
        if(!id.equals("fps")&&!id.equals("cps")&&!id.equals("keystrokes")&&flag(id+".background",true)){int rgb=color(id+".backgroundColor","#191E24")&0xffffff;int a=UnboxClient.number(id+".opacity",60,0,100)*255/100;PanelStyle.round(g,0,0,sz[0],sz[1],flag(id+".rounded",true)?4:0,(a<<24)|rgb);}
        int color=color(id+".color","#F4F6F8");boolean shadow=flag(id+".shadow",true);
        if(id.equals("minimap")){WorldMap.draw(g,0,0,128,false);}
        else if(id.equals("keystrokes")){KeystrokesHud.draw(g,preview);}
        else if(id.equals("cps")){
            int bw=UnboxClient.number("cps.width",48,36,80),bh=UnboxClient.number("cps.height",32,28,48),gap=UnboxClient.number("cps.spacing",4,0,16);
            String mode=value("cps.mode","Both");int n=0;for(int button=0;button<2;button++){if(mode.equals("Left")&&button==1||mode.equals("Right")&&button==0)continue;int bx=n++*(bw+gap);float flash=UnboxClient.clickLight(button);if(!flag("cps.feedback",true))flash=0;if(flash>0)g.fill(bx,0,bx+bw,bh,((int)(flash*UnboxClient.number("cps.pressOpacity",35,10,100)*255/100)<<24)|(color("cps.pressColor","#FFFFFF")&0xffffff));g.outline(bx,0,bw,bh,0xffffffff);int text=PanelStyle.mix(color,color("cps.pressText","#FFFFFF"),flash);PanelStyle.hudCenter(g,button==0?"LCPS":"RCPS",bx+bw/2,4,text,shadow);PanelStyle.hudCenter(g,""+(button==0?leftClicks:rightClicks),bx+bw/2,bh-14,text,shadow);}
        }else if(id.equals("armor")){
            boolean horizontal=value("armor.layout","Vertical").equals("Horizontal"),names=flag("armor.names",false);int[] cell=armorCell(preview);int n=0;
            for(ItemStack item:equipment(preview)){
                int ix=p+(horizontal?n*cell[0]:0),iy=p+(horizontal?0:n*cell[1]);if(!item.isEmpty())g.item(item,ix,iy);else g.outline(ix,iy,16,16,0x557e8c92);
                if(flag("armor.count",true)&&item.getCount()>1)PanelStyle.hudLabel(g,""+item.getCount(),ix+10,iy+10,color,shadow);
                int tx=horizontal?ix:ix+21,ty=horizontal?iy+21:iy+2;
                if(names&&!item.isEmpty()){PanelStyle.hudLabel(g,item.getHoverName().getString(),tx,ty,color,shadow);ty+=12;}
                if(item.isDamageableItem()){int remaining=item.getMaxDamage()-item.getDamageValue(),percent=remaining*100/item.getMaxDamage();String fmt=value("armor.format","Remaining"),text=durability(item);
                    int tint=percent<UnboxClient.number("armor.warning",20,0,50)?ClientHud.color("armor.warningColor","#EF7078"):flag("armor.dynamic",true)?PanelStyle.mix(0xffef7078,0xffeef0f4,percent/100f):color;
                    if(!fmt.equals("Off")){PanelStyle.hudLabel(g,text,tx,ty,tint,shadow);ty+=12;}
                    if(flag("armor.bar",true)){int bw=Math.max(24,cell[0]-(horizontal?6:27));g.fill(tx,ty+2,tx+bw,ty+4,0x554c5861);g.fill(tx,ty+2,tx+bw*percent/100,ty+4,tint);}
                }n++;
            }
        }else {int iy=p;for(String s:lines(id)){PanelStyle.hudLabel(g,s,p,iy,color,shadow);iy+=12;}}
        g.pose().popMatrix();
    }
}
