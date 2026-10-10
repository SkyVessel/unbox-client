package dev.unbox.client;
import net.minecraft.client.*;
import net.minecraft.client.player.LocalPlayer;
/** Native camera-only freelook. Never changes the player's network rotation. */
public final class Freelook {
 public static final Settings config=new Settings();
 private static KeyMapping key;private static boolean active,held;private static float yaw,pitch;private static CameraType prior;
 public static void init(){key=Platform.key(new KeyMapping("freelook.key.activate",342,KeyMapping.Category.MISC));}
 public static boolean active(){return active;}
 public static void activate(boolean on){active=on;}
 public static void rotation(float y,float p){yaw=y;pitch=p;}public static float yaw(){return yaw;}public static float pitch(){return pitch;}
 public static void turn(LocalPlayer p,double x,double y){if(active){yaw+=x*.15;pitch=Math.clamp(pitch+(float)y*.15f,-90,90);}else p.turn(x,y);}
 public static void tick(Minecraft m){boolean down=key.isDown();boolean requested=config.isToggle()?(down&&!held?!active:active):down;held=down;
  if(!UnboxClient.enabled("freelook")||m.player==null||m.screen!=null)requested=false;
  if(requested&&!active){rotation(m.player.getYRot(),m.player.getXRot());prior=m.options.getCameraType();m.options.setCameraType(config.getPerspective());active=true;}
  else if(!requested&&active){active=false;if(prior!=null)m.options.setCameraType(prior);prior=null;}
 }
 public static final class Settings {
  private boolean toggle;private CameraType perspective=CameraType.THIRD_PERSON_BACK;
  public boolean isToggle(){return toggle;}public void setToggle(boolean b){toggle=b;}
  public CameraType getPerspective(){return perspective;}
  public void setPerspective(int i){perspective=switch(i){case 1->CameraType.FIRST_PERSON;case 2->CameraType.THIRD_PERSON_FRONT;default->CameraType.THIRD_PERSON_BACK;};}
  public void nextPerspective(){perspective=perspective.cycle();}
  public void save(){}
 }
}
