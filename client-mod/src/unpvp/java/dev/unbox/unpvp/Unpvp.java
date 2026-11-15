package dev.unbox.unpvp;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.Keyboard;

/** Independent UI and four native HUD modules; upstream engines handle world optimization. */
@Mod(modid="unpvp",name="Unbox unpvp",version="0.2.0",clientSideOnly=true,acceptableRemoteVersions="*",acceptedMinecraftVersions="[1.8.9]")
public final class Unpvp {
    @SubscribeEvent public void frame(TickEvent.RenderTickEvent event){if(event.phase==TickEvent.Phase.END){UiTransition.frame();ModelOcclusion.idle();ConnectionDiagnostics.frame();}if(event.phase==TickEvent.Phase.END&&Boolean.getBoolean("unpvp.test.lobby"))LobbyProbe.frame();if(event.phase==TickEvent.Phase.END&&Boolean.getBoolean("unpvp.test.response"))ResponseProbe.frame();if(event.phase==TickEvent.Phase.END&&Boolean.getBoolean("unpvp.benchmark"))PerformanceProbe.frame();if(event.phase==TickEvent.Phase.END&&Boolean.getBoolean("unpvp.test.retina"))RetinaProbe.frame();if(event.phase==TickEvent.Phase.END&&Boolean.getBoolean("unpvp.test.load"))LoadProbe.frame();}
    @SubscribeEvent public void world(net.minecraftforge.client.event.RenderWorldLastEvent event){if(Boolean.getBoolean("unpvp.test.load"))LoadProbe.world();}
    private final PvpControls pvp=new PvpControls();
    private final KeyBinding menu=new KeyBinding("Unbox menu",Keyboard.KEY_RSHIFT,"Unbox");
    @Mod.EventHandler public void init(FMLInitializationEvent event){UiState.load();ModelOcclusion.register();ClientRegistry.registerKeyBinding(menu);ClientRegistry.registerKeyBinding(pvp.zoom);ClientRegistry.registerKeyBinding(pvp.look);UiKeys.register();MinecraftForge.EVENT_BUS.register(pvp);MinecraftForge.EVENT_BUS.register(new ExtraHud());MinecraftForge.EVENT_BUS.register(new SocialNotifications());SocialBridge.background(JoinDiagnostics::init);MinecraftForge.EVENT_BUS.register(new ChatHistory());MinecraftForge.EVENT_BUS.register(this);MinecraftForge.EVENT_BUS.register(new LiveHud());net.minecraftforge.fml.common.FMLCommonHandler.instance().bus().register(this);System.out.println("[unpvp] HUD loaded: FPS, CPS, keystrokes, latency");}
    @SubscribeEvent public void open(GuiOpenEvent event){if(event.gui instanceof net.minecraft.client.multiplayer.GuiConnecting)Feedback.event("connecting",SocialBridge.joining()?"friend":"public-direct");if(event.gui instanceof net.minecraft.client.gui.GuiDisconnected)Feedback.event("disconnected",SocialBridge.joining()?"friend":"public-direct");if(event.gui instanceof GuiMainMenu)event.gui=new HomeScreen(false);UiTransition.open(event.gui);}
    @SubscribeEvent public void tick(TickEvent.ClientTickEvent event){if(event.phase!=TickEvent.Phase.END)return;Minecraft mc=Minecraft.getMinecraft();LiveHud.tick();ConnectionDiagnostics.tick();SocialBridge.tick();SocialNotifications.tick();LocalSkin.tick();NameBadges.tick();if(Boolean.getBoolean("unpvp.test.badges"))BadgeProbe.tick();if(!System.getProperty("unpvp.test.social","").isEmpty())SocialProbe.tick();UiWarmup.tick();if(Boolean.getBoolean("unpvp.test.lobby"))LobbyProbe.tick();UiKeys.tick();pvp.tick();if(Boolean.getBoolean("unpvp.test.pvp")||Boolean.getBoolean("unpvp.test"))PvpProbe.tick();if(Boolean.getBoolean("unpvp.test.load"))LoadProbe.tick();if(Boolean.getBoolean("unpvp.benchmark"))PerformanceProbe.tick();while(menu.isPressed())if(mc.theWorld!=null&&mc.currentScreen==null)mc.displayGuiScreen(new HomeScreen(true));if(mc.currentScreen instanceof UiScreen)Backdrop.tick();else if(Backdrop.active())Backdrop.release();}
}
