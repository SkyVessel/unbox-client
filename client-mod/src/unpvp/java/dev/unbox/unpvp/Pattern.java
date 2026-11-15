package dev.unbox.unpvp;

/** Modern monochrome canvas, restricted to preview settings. */
final class Pattern {
    static final String[] NAMES={"Fine cross","Open cross","Dot","Ring","Square","Chevron","Diagonal","Diamond"};
    static String empty(){char[] chars=new char[225];java.util.Arrays.fill(chars,'0');return new String(chars);}
    static String pixels(){String p=UiState.get("crosshair.pixels","");return p.matches("[01]{225}")?p:empty();}
    static String preset(int index){char[] p=empty().toCharArray();for(int y=0;y<15;y++)for(int x=0;x<15;x++){int dx=Math.abs(x-7),dy=Math.abs(y-7);boolean on;switch(index){case 0:on=dx==0&&dy<=5||dy==0&&dx<=5;break;case 1:on=dx==0&&dy>=3&&dy<=6||dy==0&&dx>=3&&dx<=6;break;case 2:on=dx<=1&&dy<=1;break;case 3:on=dx*dx+dy*dy>=18&&dx*dx+dy*dy<=28;break;case 4:on=Math.max(dx,dy)==5;break;case 5:on=y>=5&&y<=10&&dx==y-5;break;case 6:on=dx==dy&&dx>=2&&dx<=5;break;default:on=dx+dy==5;}if(on)p[y*15+x]='1';}return new String(p);}
    static void paint(int x,int y,boolean on){if(x<0||y<0||x>=15||y>=15)return;char[] p=pixels().toCharArray();p[y*15+x]=on?'1':'0';String mirror=UiState.get("crosshair.mirror","Off");if(!mirror.equals("Off"))p[y*15+14-x]=on?'1':'0';if(mirror.equals("Both")){p[(14-y)*15+x]=on?'1':'0';p[(14-y)*15+14-x]=on?'1':'0';}UiState.set("crosshair.pixels",new String(p));UiState.set("crosshair.shape","Custom");}
}
