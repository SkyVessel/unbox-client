package dev.unbox.unpvp;
/** Stable, dependency-free bridge for the independently attributed Forge ports. */
public final class PvpSettings {
    private PvpSettings(){}
    private static volatile java.util.Set<String> enabled=java.util.Collections.emptySet();
    static void refresh(){java.util.Set<String> next=new java.util.HashSet<>();for(UiState.Module m:UiState.MODULES)if(UiState.on(m.id))next.add(m.id);enabled=java.util.Collections.unmodifiableSet(next);}
    public static boolean on(String id){return enabled.contains(id);}
    public static boolean flag(String key,boolean fallback){return UiState.flag(key,fallback);}
    public static int number(String key,int fallback,int min,int max){return UiState.number(key,fallback,min,max);}
    public static String text(String key,String fallback){return UiState.get(key,fallback);}
    public static int color(String key,String fallback){return UiState.color(key,fallback);}
    public static boolean coversWorld(){net.minecraft.client.gui.GuiScreen screen=net.minecraft.client.Minecraft.getMinecraft().currentScreen;return screen instanceof SolidScreen&&((SolidScreen)screen).coversWorld();}
    public static boolean sneak(){return PvpControls.sneaking;}
    public static String nickname(String name){net.minecraft.client.Minecraft mc=net.minecraft.client.Minecraft.getMinecraft();String nick=text("nickname.value","");return on("nickname")&&!nick.isEmpty()&&mc.thePlayer!=null&&name.equals(mc.thePlayer.getName())?nick:name;}
}
