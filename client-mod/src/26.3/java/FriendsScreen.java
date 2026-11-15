package dev.unbox.client;
import net.minecraft.client.gui.screens.Screen;
/** Shared friend rail; opening from the world browser returns to that browser. */
public final class FriendsScreen extends UnboxHomeScreen {
 public FriendsScreen(Screen parent){super(net.minecraft.client.Minecraft.getInstance().level!=null,"quick",parent);}
}
