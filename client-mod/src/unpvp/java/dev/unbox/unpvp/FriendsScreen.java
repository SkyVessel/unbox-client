package dev.unbox.unpvp;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
/** Friend management inside the shared vector composition. */
final class FriendsScreen extends HomeScreen {
 FriendsScreen(GuiScreen parent){super(Minecraft.getMinecraft().theWorld!=null,parent);page="friend";}
 @Override void close(){UiState.save();navigate(parent);}
}
