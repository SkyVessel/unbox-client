package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
public final class SyncFeedbackScreen extends SolidScreen {
 public SyncFeedbackScreen(Screen parent){super("Feedback","dialog",parent);}
 @Override protected void build(){themePicker();text("Multiplayer feedback",182,105,800,30,SolidTheme.text());button(125,100,45,45,"←",SolidTheme.card(1),this::onClose);
  text("Help us diagnose shared mod downloads",350,250,760,26,SolidTheme.text());
  String[] lines={"Records your account, join stages, file IDs, byte counts and errors.","No chat, passwords, file contents or gameplay recording.","Uploads once on exit, after the launcher checks the shared files.","Keep the launcher open. Default off; reports expire after 7 days."};
  for(int i=0;i<lines.length;i++)text(lines[i],350,305+i*36,800,20,SolidTheme.muted());
  button(350,490,300,52,SyncFeedback.active()?"Stop & discard":"Start recording",SolidTheme.accent(),()->{if(SyncFeedback.active())SyncFeedback.discard();else SyncFeedback.start();rebuild();});button(670,490,200,52,"Back",SolidTheme.card(1),this::onClose);text(SyncFeedback.status,350,570,800,20,SolidTheme.muted());
 }
}
