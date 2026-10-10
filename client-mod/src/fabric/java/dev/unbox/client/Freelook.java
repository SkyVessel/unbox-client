package dev.unbox.client;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
public final class Freelook {
 public static final freelook.freelook.config.FreeLookConfig config=freelook.freelook.FreeLookMod.config;
 public static void init(){}public static void tick(Minecraft mc){}
 public static boolean active(){return freelook.freelook.FreeLookMod.isFreeLooking;}
 public static void activate(boolean on){freelook.freelook.FreeLookMod.isFreeLooking=on;}
 private static freelook.freelook.CameraOverriddenEntity camera(){return (freelook.freelook.CameraOverriddenEntity)Minecraft.getInstance().player;}
 public static void rotation(float y,float p){camera().freelook$setCameraYaw(y);camera().freelook$setCameraPitch(p);}
 public static float yaw(){return camera().freelook$getCameraYaw();}public static float pitch(){return camera().freelook$getCameraPitch();}
 public static void turn(LocalPlayer p,double x,double y){p.turn(x,y);}
}
