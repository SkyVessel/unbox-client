package dev.unbox.unpvp;

/** Shared palettes from the 26.3 handoff; preferences remain personal. */
final class SolidTheme {
    static int index(){return UiState.number("ui.theme",1,0,2)==2?2:1;}
    static String scene(String page){return (index()==2?"yellow-":"red-")+page;}
    static int background(){return index()==2?0xff383b3e:0xffe8e8ec;}
    static int panel(){return index()==2?0xff323437:0xffdcdde2;}
    static int text(){return index()==2?0xffd1d1c8:0xff29262f;}
    static int muted(){return index()==2?0xffaaa99f:0xff64606b;}
    static int accent(){return index()==2?0xffe4b900:0xffc9203d;}
    static int card(int i){return index()==2?0xff383b3e:0xffd0d1d6;}
    static int input(){return index()==2?0xff4b4e52:0xffbfc1c9;}
    static float opacity(){return UiState.number("ui.opacity",100,0,100)/100f;}
    static boolean reduced(){return UiState.flag("ui.reducedMotion",false);}
    static void select(int i){UiState.set("ui.theme",UiState.clamp(i,1,2));UiState.save();}
}
