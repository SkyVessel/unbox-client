package dev.unbox.unpvp;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraftforge.client.event.*;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.lwjgl.input.Keyboard;
/** Bounded in-memory history. Nothing is sent to a service or written to disk. */
final class ChatHistory {
 static final Deque<Entry> entries=new ArrayDeque<>();
 static final class Entry {final String text;final boolean player;Entry(String text,boolean player){this.text=text;this.player=player;}}
 @SubscribeEvent public void message(ClientChatReceivedEvent e){if(e.type==2)return;entries.addFirst(new Entry(e.message.getUnformattedText(),e.message.getUnformattedText().startsWith("<")));while(entries.size()>2000)entries.removeLast();}
 @SubscribeEvent public void key(GuiScreenEvent.KeyboardInputEvent.Pre e){if(e.gui instanceof GuiChat&&UiState.on("chat")&&Keyboard.getEventKeyState()&&Keyboard.getEventKey()==Keyboard.KEY_F&&GuiScreen.isCtrlKeyDown()){e.setCanceled(true);Minecraft.getMinecraft().displayGuiScreen(new HistoryScreen(e.gui));}}
 static final class HistoryScreen extends UiScreen {
  String query="",filter="All";int page;List<Entry> matches=new ArrayList<>();int left,top,pw,ph;
  HistoryScreen(GuiScreen parent){super(parent);}
  void layout(){scale=Math.max(.6f,Math.min(width/900f,height/600f));logicalWidth=Math.round(width/scale);logicalHeight=Math.round(height/scale);pw=Math.min(850,logicalWidth-40);ph=Math.min(550,logicalHeight-40);left=(logicalWidth-pw)/2;top=(logicalHeight-ph)/2;
   button(left+16,top+14,60,28,"Back",this::close);field(left+100,top+20,pw-120,24,query,"Search chat history",s->{query=s;page=0;refresh();});int xx=left+16;for(String category:new String[]{"All","Players","System"}){button(xx,top+56,84,26,category,()->{filter=category;page=0;initGui();}).selected=filter.equals(category);xx+=92;}button(left+pw-180,top+56,74,26,"Previous",()->{page=Math.max(0,page-1);initGui();});button(left+pw-96,top+56,80,26,"Next",()->{if((page+1)*rows()<matches.size())page++;initGui();});refresh();
  }
  int rows(){return Math.max(1,(ph-110)/30);}
  void refresh(){controls.removeIf(c->c.clip);matches.clear();for(Entry entry:entries)if(entry.text.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))&&(filter.equals("All")||entry.player==filter.equals("Players")))matches.add(entry);int end=Math.min(matches.size(),(page+1)*rows());for(int i=page*rows();i<end;i++){final Entry entry=matches.get(i);button(left+pw-70,top+96+(i-page*rows())*30,55,25,"Copy",()->GuiScreen.setClipboardString(entry.text)).clip=true;}}
  void paint(float x,float y,float p){Painter.round(left,top,pw,ph,24,SolidTheme.panel());Painter.text("CHAT HISTORY",left+100,top-18,SolidTheme.text());int end=Math.min(matches.size(),(page+1)*rows());for(int i=page*rows();i<end;i++){int yy=top+102+(i-page*rows())*30;Painter.clip(left+16,yy,pw-100,22);Painter.text(matches.get(i).text,left+16,yy,SolidTheme.text());Painter.unclip();}Painter.text(matches.size()+" messages · Stored for this session",left+16,top+ph-20,SolidTheme.muted());}
 }
}
