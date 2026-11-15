package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
/** Optional resource pack download information; modules use UnboxScreen directly. */
public final class PvpModuleScreen extends SolidScreen {
 private final String id;private String notice="";
 PvpModuleScreen(String id,Screen parent){super("PvP settings","dialog",parent);this.id=id;}
 protected void build(){
  if(id.equals("eatingpack")){
   center("Theone's Eating Animation Pack",400,240,640,30,SolidTheme.text());
   center("Optional resource pack · download from the author",350,310,740,22,SolidTheme.muted());
   button(520,380,400,48,"Author download page",SolidTheme.accent(),()->net.minecraft.util.Util.getPlatform().openUri("https://www.planetminecraft.com/texture-pack/theone-s-eating-animation-pack/"));
   button(520,450,400,48,"Resource packs",SolidTheme.card(0),()->{notice=ClientIntegrations.openResources(this);rebuild();});
   button(520,520,400,48,"Back",SolidTheme.card(0),this::onClose);center(notice,280,640,900,20,SolidTheme.text());return;
  }
 }
}
