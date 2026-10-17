package dev.unbox.client;

import java.lang.reflect.Method;

/** Keeps Wick's own runtime switch aligned with the Unbox module setting. */
public final class DynamicLightsBridge {
 private static Method config,enabled,setEnabled,save;private static boolean resolved;
 public static void sync(){
  boolean wanted=UnboxClient.configured("dynamiclight");
  try{resolve();if(config==null)return;Object value=config.invoke(null);boolean current=(boolean)enabled.invoke(value);if(current!=wanted){setEnabled.invoke(value,wanted);save.invoke(null);}}catch(ReflectiveOperationException ignored){}
 }
 private static void resolve()throws ReflectiveOperationException{if(resolved)return;resolved=true;if(!Platform.loaded("wick"))return;Class<?> client=Class.forName("dev.wick.client.WickClient"),type=Class.forName("dev.wick.config.WickConfig");config=client.getMethod("config");save=client.getMethod("saveConfig");enabled=type.getMethod("enabled");setEnabled=type.getMethod("setEnabled",boolean.class);}
 private DynamicLightsBridge(){}
}
