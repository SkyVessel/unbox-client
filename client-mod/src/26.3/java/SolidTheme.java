package dev.unbox.client;

/** The two palettes from the approved solid UI. Shared by all 26.3 menus. */
final class SolidTheme {
    static final String[] NAMES={"", "White / Red", "Gray / Yellow"};
    static int index(){return UnboxClient.number("ui.theme",1,0,2)==2?2:1;}
    static String scene(String page){return (index()==1?"red-":index()==2?"yellow-":"")+page;}
    static int background(){return switch(index()){case 1->0xffe8e8ec;case 2->0xff383b3e;default->0xfff5f3ed;};}
    static int panel(){return switch(index()){case 1->0xffdcdde2;case 2->0xff323437;default->0xfff5f3ed;};}
    static int text(){return index()==2?0xffd1d1c8:0xff29262f;}
    static int muted(){return index()==2?0xffaaa99f:0xff64606b;}
    static int accent(){return switch(index()){case 1->0xffc9203d;case 2->0xffe4b900;default->0xffac94f4;};}
    static int card(int i){return index()==0?new int[]{0xffac94f4,0xfff6f780,0xffffad8a,0xfff47c85}[Math.floorMod(i,4)]:index()==1?0xffd0d1d6:0xff383b3e;}
    static float opacity(){return Math.clamp(UnboxClient.number("ui.opacity",100,0,100),0,100)/100f;}
    static void select(int theme){UnboxClient.set("ui.theme",Math.clamp(theme,1,2));UnboxClient.save();}
    static boolean reduced(){return ClientHud.flag("ui.reducedMotion",false)||net.minecraft.client.Minecraft.getInstance().options.screenEffectScale().get()==0;}
}
