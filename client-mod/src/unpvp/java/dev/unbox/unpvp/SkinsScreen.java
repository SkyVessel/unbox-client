package dev.unbox.unpvp;
import net.minecraft.client.gui.GuiScreen;
/** Shared Appearance composition, showing only the actual session skin and honest connection state. */
final class SkinsScreen extends SolidScreen {
 private int page;private boolean logos;private java.util.List<com.google.gson.JsonObject> items=new java.util.ArrayList<>();private String signature="";private float rotation,lastX;private boolean rotating;
 SkinsScreen(GuiScreen parent){super("appearance",parent);}
 @Override void build(){
  hit(125,100,45,45,"Back",this::close);hit(1275,100,45,45,"Close",this::close);
  hit(1190,177,110,45,"Redeem",()->navigate(new RedeemScreen(this))).active=NameBadges.eligible();
  text(mc.getSession().getUsername(),399,180,220,26,SolidTheme.text());
  solidButton(688,215,280,36,"Skins",logos?SolidTheme.card(0):SolidTheme.card(1),()->{logos=false;page=0;rebuild();});solidButton(988,215,290,36,"Logos",logos?SolidTheme.card(1):SolidTheme.card(0),()->{logos=true;page=0;rebuild();});
  java.util.List<com.google.gson.JsonObject> skins=new java.util.ArrayList<>();if(LocalSkin.library.has("skins"))for(com.google.gson.JsonElement e:LocalSkin.library.getAsJsonArray("skins"))skins.add(e.getAsJsonObject());if(logos){skins.clear();com.google.gson.JsonObject basic=new com.google.gson.JsonObject();basic.addProperty("id","");basic.addProperty("name","Unbox");skins.add(basic);try{if(NameBadges.eligible())for(com.google.gson.JsonElement e:SocialBridge.view.getAsJsonObject("assets").getAsJsonArray("items")){com.google.gson.JsonObject item=e.getAsJsonObject();if(SocialBridge.text(item,"kind").equals("logo"))skins.add(item);}}catch(Exception ignored){}}
  items=skins;page=Math.min(page,Math.max(0,(skins.size()-1)/4));
  for(int i=page*4;i<Math.min(skins.size(),page*4+4);i++){com.google.gson.JsonObject item=skins.get(i);solidButton(688,268+(i-page*4)*62,590,48,SocialBridge.text(item,"name"),SolidTheme.card(0),()->{if(logos)SocialBridge.command("equip-logo",SocialBridge.text(item,"id"));else LocalSkin.apply(SocialBridge.text(item,"id"),null);}).active=logos?NameBadges.eligible()&&!SocialBridge.pending():LocalSkin.pending.isEmpty();}
  hit(385,550,44,36,"Rotate player",()->rotation+=45);center("Current game skin",250,564,320,19,SolidTheme.muted());
  if(skins.isEmpty())text("Save skins in the launcher to use them here.",688,300,600,20,SolidTheme.muted());
  if(skins.size()>4){solidButton(688,530,100,36,"Previous",SolidTheme.card(0),()->{page=Math.max(0,page-1);rebuild();});solidButton(1178,530,100,36,"Next",SolidTheme.card(0),()->{page=Math.min((skins.size()-1)/4,page+1);rebuild();});}
  com.google.gson.JsonObject active=LocalSkin.library.has("active")&&LocalSkin.library.get("active").isJsonObject()?LocalSkin.library.getAsJsonObject("active"):new com.google.gson.JsonObject();String id=SocialBridge.text(active,"id");
  solidButton(268,658,134,52,"Classic",SolidTheme.card(1),()->LocalSkin.apply(id,"classic")).active=!id.isEmpty()&&LocalSkin.pending.isEmpty();solidButton(414,658,134,52,"Slim",SolidTheme.card(1),()->LocalSkin.apply(id,"slim")).active=!id.isEmpty()&&LocalSkin.pending.isEmpty();
  solidButton(1068,658,220,52,"Back",SolidTheme.card(1),this::close);signature=signature();

 }
 private String signature(){return LocalSkin.library.toString()+LocalSkin.pending+String.valueOf(SocialBridge.view.get("assets"))+SocialBridge.pending()+NameBadges.eligible();}
 @Override public void updateScreen(){super.updateScreen();if(!signature.equals(signature()))rebuild();}
 @Override void live(float mx,float my,float partial){BadgeTextures.icon(NameBadges.logo(mc.getSession().getProfile().getId()),ux(358,178),uy(358,178),28);if(logos){for(int i=page*4;i<Math.min(items.size(),page*4+4);i++){com.google.gson.JsonObject item=items.get(i);int y=280+(i-page*4)*62;BadgeTextures.icon(NameBadges.bundled(item),ux(714,y),uy(714,y),26);String id=SocialBridge.text(item,"id");String equipped=NameBadges.logo(mc.getSession().getProfile().getId());if(equipped.equals(NameBadges.bundled(item)))SolidText.draw("Equipped",ux(1160,y),uy(1160,y),17,SolidTheme.muted(),false);}}
  SolidText.draw(logos?(NameBadges.eligible()?SocialBridge.message:"Microsoft sign-in required for logo equipment"):LocalSkin.message,ux(688,605),uy(688,605),18,SolidTheme.muted(),false);HomeScreen.player(ux(258,219),uy(258,219),300,331,rotation,mx,my,rotating);}
 @Override boolean interceptClick(float mx,float my,int b){if(b==0&&mx>=ux(258,219)&&mx<ux(558,219)&&my>=uy(258,219)&&my<uy(558,550)){rotating=true;lastX=mx;return true;}return false;}
 @Override protected void mouseClickMove(int x,int y,int b,long held){if(rotating){float next=x/scale;rotation-=next-lastX;lastX=next;}else super.mouseClickMove(x,y,b,held);}
 @Override protected void mouseReleased(int x,int y,int b){rotating=false;super.mouseReleased(x,y,b);}
}
