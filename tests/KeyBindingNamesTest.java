package dev.unbox.client;
final class KeyBindingNamesTest {
 private static void expect(String expected,String value,String actual,String defaultKey){String result=KeyBindingNames.migrate(value,actual,defaultKey);if(!java.util.Objects.equals(expected,result))throw new AssertionError(value+" migrated to "+result+", expected "+expected);}
 public static void main(String[] args){
  // GLFW C/M must not become SDL scancodes 67/77 when a profile is copied.
  expect("key.keyboard.c","67","key.keyboard.z","key.keyboard.z");
  expect("key.keyboard.m","77","key.keyboard.z","key.keyboard.z");
  expect("key.keyboard.left.control","341","key.keyboard.z","key.keyboard.z");
  expect("key.keyboard.f5","294","key.keyboard.z","key.keyboard.z");
  expect("key.keyboard.unknown","-1","key.keyboard.z","key.keyboard.z");
  // Options is authoritative for previously saved custom keyboard/mouse binds.
  expect("key.keyboard.x","67","key.keyboard.x","key.keyboard.c");
  expect("key.mouse.4","3","key.mouse.4","key.keyboard.c");
  expect("scancode.29","67","scancode.29","key.keyboard.c");
  expect("key.keyboard.unknown","67","key.keyboard.unknown","key.keyboard.c");
  // Stable config changes remain explicit, including unbinding and rebinding.
  expect("key.keyboard.r","key.keyboard.r","key.keyboard.x","key.keyboard.c");
  expect("key.keyboard.unknown","key.keyboard.unknown","key.keyboard.x","key.keyboard.c");
  expect(null,"999999999999999","key.keyboard.c","key.keyboard.c");
  expect(null,"999","key.keyboard.c","key.keyboard.c");
  System.out.println("Key binding migration: 13 cases passed");
 }
}
