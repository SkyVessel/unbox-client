package dev.unbox.client;

/** Small bounded, monochrome canvas. Share codes contain settings, never executable code. */
final class CrosshairPattern {
    static final int SIDE=15;
    static final String[] PRESETS={"Fine cross","Open cross","Dot","Ring","Square","Chevron","Diagonal","Diamond"};
    static String preset(int index){char[] p="0".repeat(225).toCharArray();for(int y=0;y<15;y++)for(int x=0;x<15;x++){int dx=Math.abs(x-7),dy=Math.abs(y-7);boolean on=switch(index){case 0->(dx==0&&dy<=5||dy==0&&dx<=5);case 1->(dx==0&&dy>=3&&dy<=6||dy==0&&dx>=3&&dx<=6);case 2->dx<=1&&dy<=1;case 3->dx*dx+dy*dy>=18&&dx*dx+dy*dy<=28;case 4->Math.max(dx,dy)==5;case 5->y>=5&&y<=10&&dx==y-5;case 6->dx==dy&&dx>=2&&dx<=5;default->dx+dy==5;};if(on)p[y*15+x]='1';}return new String(p);}
    static void applyPreset(int index){UnboxClient.set("crosshair.pixels",preset(index));UnboxClient.set("crosshair.shape","Custom");UnboxClient.save();}
    static String pixels(){String s=ClientHud.value("crosshair.pixels","");return s.matches("[01]{225}")?s:"0".repeat(225);}
    static void paint(int x,int y,boolean on){if(x<0||x>=SIDE||y<0||y>=SIDE)return;char[] p=pixels().toCharArray();p[y*SIDE+x]=on?'1':'0';String mirror=ClientHud.value("crosshair.mirror","Off");if(!mirror.equals("Off"))p[y*SIDE+14-x]=on?'1':'0';if(mirror.equals("Both")){p[(14-y)*SIDE+x]=on?'1':'0';p[(14-y)*SIDE+14-x]=on?'1':'0';}UnboxClient.set("crosshair.pixels",new String(p));UnboxClient.set("crosshair.shape","Custom");}
    static String share(){return "UNBOX1:"+pixels();}
    static boolean importCode(String s){if(!s.matches("UNBOX1:[01]{225}"))return false;UnboxClient.set("crosshair.pixels",s.substring(7));UnboxClient.set("crosshair.shape","Custom");UnboxClient.save();return true;}
}
