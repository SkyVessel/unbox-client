package org.lwjgl.glfw;
public class GLFW {
 public static final java.util.Map<Integer,Integer> hints=new java.util.HashMap<>();
 public static void glfwInitHint(int k,int v){hints.put(k,v);}public static void glfwWindowHint(int k,int v){hints.put(k,v);}
 public static boolean glfwInit(){return true;}
 public static long glfwCreateWindow(int w,int h,CharSequence title,long monitor,long share){if(hints.get(0x20004)!=0||hints.get(0x20001)!=0||hints.get(0x2000c)!=0)throw new AssertionError("visible window");return 1;}
 public static void glfwShowWindow(long handle){throw new AssertionError("show");}public static void glfwFocusWindow(long handle){throw new AssertionError("focus");}
}
