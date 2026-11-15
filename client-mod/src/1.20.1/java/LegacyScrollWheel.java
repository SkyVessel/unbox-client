package dev.unbox.client;
public final class LegacyScrollWheel {private double x,y;public record Steps(int x,int y){}public Steps onMouseScroll(double dx,double dy){if(Math.signum(dx)!=Math.signum(x))x=0;if(Math.signum(dy)!=Math.signum(y))y=0;x+=dx;y+=dy;int sx=(int)x,sy=(int)y;x-=sx;y-=sy;return new Steps(sx,sy);}}
