package dev.unbox.client;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
public final class Freelook {
 public static final Config config=new Config();private static CameraType previous;
 public static final class Config {
  public boolean isToggle(){return freelook.freelook.client.FreelookmodClient.isToggle;}
  public void setToggle(boolean on){freelook.freelook.client.FreelookmodClient.isToggle=on;}
  public CameraType getPerspective(){try{return new freelook.freelook.VariableStorage().getStoredPerspective();}catch(java.io.IOException e){return CameraType.THIRD_PERSON_BACK;}}
  public void setPerspective(int n){try{new freelook.freelook.VariableStorage().write(n);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
  public void nextPerspective(){setPerspective(switch(getPerspective()){case FIRST_PERSON->2;case THIRD_PERSON_FRONT->3;default->1;});}
  public void save(){try{var p=Platform.gameDir().resolve("t.txt");if(isToggle())java.nio.file.Files.writeString(p,"toggle");else java.nio.file.Files.deleteIfExists(p);}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
 }
 public static void init(){} public static void tick(Minecraft mc){}
 public static boolean active(){return freelook.freelook.client.FreelookmodClient.isFreeLooking;}
 public static void activate(boolean on){var mc=Minecraft.getInstance();if(on&&!active()){previous=mc.options.getCameraType();if(mc.player!=null)rotation(mc.player.getYRot(),mc.player.getXRot());mc.options.setCameraType(config.getPerspective());}else if(!on&&active()&&previous!=null)mc.options.setCameraType(previous);freelook.freelook.client.FreelookmodClient.isFreeLooking=on;}
 public static void suspend(){activate(false);}
 private static freelook.freelook.CameraOverriddenEntity camera(){return(freelook.freelook.CameraOverriddenEntity)Minecraft.getInstance().player;}
 public static void rotation(float y,float p){camera().setCameraYaw(y);camera().setCameraPitch(p);}public static float yaw(){return camera().getCameraYaw();}public static float pitch(){return camera().getCameraPitch();}public static void turn(LocalPlayer p,double x,double y){p.turn(x,y);}
}
