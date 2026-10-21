package org.lwjgl.sdl;
public class SDLVideo {
 public static long SDL_CreateWindow(CharSequence title,int w,int h,long flags){if((flags&8)==0)throw new AssertionError("SDL visible");return 1;}
 public static boolean SDL_ShowWindow(long h){throw new AssertionError("SDL show");}public static boolean SDL_RaiseWindow(long h){throw new AssertionError("SDL raise");}
}
