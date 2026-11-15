package dev.unbox.unpvp;
import net.minecraft.client.gui.GuiScreen;
import com.google.gson.*;
import java.util.*;
/** Same selection/equip composition and launcher protocol as 26.3 Appearance. */
final class SkinsScreen extends SolidScreen {
 private final SkinSelectionPreview preview=new SkinSelectionPreview();
 private int skinPage;private List<JsonObject> skins=new ArrayList<>();
 private String selected="",model="classic",asset="",equipped="",signature="";
 private float rotation,lastX;private boolean rotating;
 SkinsScreen(GuiScreen parent){super("appearance",parent);equipment();asset=equipped;}
 private static String text(JsonObject o,String k){return SocialBridge.text(o,k);}
 private void equipment(){equipped="";try{for(JsonElement e:SocialBridge.view.getAsJsonObject("assets").getAsJsonArray("equipment")){JsonObject v=e.getAsJsonObject();if(text(v,"slot").equals("logo"))equipped=text(v,"item");}}catch(Exception ignored){}}
 private boolean owned(String id){if(id.isEmpty())return true;try{for(JsonElement e:SocialBridge.view.getAsJsonObject("assets").getAsJsonArray("items"))if(id.equals(text(e.getAsJsonObject(),"id")))return true;}catch(Exception ignored){}return false;}
 @Override void build(){
  skins=new ArrayList<>();if(LocalSkin.library.has("skins"))for(JsonElement e:LocalSkin.library.getAsJsonArray("skins"))skins.add(e.getAsJsonObject());
  if(selected.isEmpty()&&LocalSkin.library.has("active")&&LocalSkin.library.get("active").isJsonObject()){JsonObject a=LocalSkin.library.getAsJsonObject("active");selected=text(a,"id");model=text(a,"model");}
  hit(125,100,45,45,"Back",this::close);hit(1275,100,45,45,"Close",this::close);
  hit(1190,177,110,45,"Redeem",()->navigate(new RedeemScreen(this))).active=NameBadges.eligible();
  text(mc.getSession().getUsername(),399,180,220,26,SolidTheme.text());if(equipped.isEmpty())icon("logo",358,178,28,SolidTheme.text());
  skinPage=Math.max(0,Math.min(skinPage,Math.max(0,skins.size()-3)));
  for(int i=skinPage;i<Math.min(skins.size(),skinPage+3);i++){JsonObject skin=skins.get(i);int y=238+(i-skinPage)*88;round(138,y,76,76,24,text(skin,"id").equals(selected)?SolidTheme.accent():SolidTheme.card(1));hit(138,y,76,76,text(skin,"name"),()->{selected=text(skin,"id");model=text(skin,"model");rebuild();}).active=LocalSkin.pending.isEmpty();}
  if(skins.size()>3){solidButton(138,510,36,34,"↑",SolidTheme.card(1),()->{skinPage--;rebuild();});solidButton(178,510,36,34,"↓",SolidTheme.card(1),()->{skinPage++;rebuild();});}
  solidButton(268,600,138,42,"Classic",model.equals("classic")?SolidTheme.card(2):SolidTheme.card(1),()->{model="classic";rebuild();});
  solidButton(410,600,138,42,"Slim",model.equals("slim")?SolidTheme.card(2):SolidTheme.card(1),()->{model="slim";rebuild();});
  solidButton(268,658,280,52,LocalSkin.pending.isEmpty()?"Use skin":"Applying…",SolidTheme.accent(),()->{LocalSkin.apply(selected,model);rebuild();}).active=!selected.isEmpty()&&LocalSkin.pending.isEmpty();
  hit(385,550,44,36,"Rotate player",()->rotation+=45);
  String[] ids={"","unbox-creator-crown","unbox-fluttering-cash"};
  for(int i=0;i<3;i++){String id=ids[i];int x=688+i*205;hit(x,246,190,228,i==0?"Unbox":i==1?"Crown":"Cash",()->{asset=id;rebuild();});if(asset.equals(id))round(x+14,481,162,4,2,SolidTheme.accent());if(!owned(id))text("Locked",x+20,503,150,18,SolidTheme.muted());}
  boolean same=asset.equals(equipped);
  solidButton(1068,658,220,52,SocialBridge.pending()?"Updating…":same?"Equipped":owned(asset)?"Equip":"Locked",same?SolidTheme.card(1):SolidTheme.accent(),()->SocialBridge.command("equip-logo",asset)).active=owned(asset)&&NameBadges.eligible()&&!SocialBridge.pending()&&!same;
  String chosen="Import skins in the launcher";for(JsonObject skin:skins)if(text(skin,"id").equals(selected))chosen=text(skin,"name");center(chosen,250,564,320,19,SolidTheme.muted());signature=signature();
 }
 private String signature(){return LocalSkin.library.toString()+LocalSkin.pending+SocialBridge.view.get("assets")+SocialBridge.pending()+NameBadges.eligible();}
 @Override public void updateScreen(){super.updateScreen();equipment();if(!signature.equals(signature()))rebuild();}
 @Override void live(float mx,float my,float partial){
  if(!equipped.isEmpty())BadgeTextures.icon(NameBadges.logo(mc.getSession().getProfile().getId()),ux(358,178),uy(358,178),28);
  for(int i=skinPage;i<Math.min(skins.size(),skinPage+3);i++)SocialPanel.Face.draw(skins.get(i),ux(150,250),uy(150,250+(i-skinPage)*88),52);
  String message=LocalSkin.message.isEmpty()?SocialBridge.message:LocalSkin.message;
  SolidText.draw(message,ux(688,567),uy(688,567),18,SolidTheme.muted(),false);
  HomeScreen.player(ux(258,219),uy(258,219),300,331,rotation,mx,my,rotating,preview.get(selected),model);
 }
 @Override boolean interceptClick(float x,float y,int b){if(b==0&&x>=ux(258,219)&&x<ux(558,219)&&y>=uy(258,219)&&y<uy(558,550)){rotating=true;lastX=x;return true;}return false;}
 @Override protected void mouseClickMove(int x,int y,int b,long held){if(rotating){float next=x/scale;rotation+=next-lastX;lastX=next;}else super.mouseClickMove(x,y,b,held);}
 @Override protected void mouseReleased(int x,int y,int b){rotating=false;super.mouseReleased(x,y,b);}
 @Override void scroll(float x,float y,int direction){if(x>=ux(130,238)&&x<ux(220,238)){skinPage-=direction;rebuild();}}
 @Override public void onGuiClosed(){super.onGuiClosed();preview.close();}
}
