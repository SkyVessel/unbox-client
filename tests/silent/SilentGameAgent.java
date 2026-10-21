import java.lang.instrument.*;
import java.security.ProtectionDomain;
import org.objectweb.asm.*;
/** Test-only agent. Suppresses visibility and focus before either GLFW or SDL creates a window. */
public final class SilentGameAgent {
 public static void premain(String args,Instrumentation instrumentation){
  System.setProperty("java.awt.headless","true");System.setProperty("apple.awt.UIElement","true");
  instrumentation.addTransformer(new ClassFileTransformer(){@Override public byte[] transform(ClassLoader loader,String name,Class<?> type,ProtectionDomain domain,byte[] bytes){
   if(!name.equals("org/lwjgl/glfw/GLFW")&&!name.equals("org/lwjgl/sdl/SDLVideo"))return null;
   try{ClassReader reader=new ClassReader(bytes);ClassWriter writer=new ClassWriter(reader,ClassWriter.COMPUTE_MAXS);
    reader.accept(new ClassVisitor(Opcodes.ASM9,writer){@Override public MethodVisitor visitMethod(int access,String method,String descriptor,String signature,String[] exceptions){
     MethodVisitor mv=super.visitMethod(access,method,descriptor,signature,exceptions);
     if((access&Opcodes.ACC_NATIVE)!=0)return mv;
     boolean suppress=method.equals("glfwShowWindow")||method.equals("glfwFocusWindow")||method.equals("SDL_ShowWindow")||method.equals("SDL_RaiseWindow")||method.equals("SDL_RestoreWindow")||method.equals("SDL_MaximizeWindow")||method.equals("SDL_SetWindowFullscreen")||method.equals("glfwRestoreWindow")||method.equals("glfwMaximizeWindow");
     if(suppress){mv.visitCode();if(descriptor.endsWith("Z")){mv.visitInsn(Opcodes.ICONST_1);mv.visitInsn(Opcodes.IRETURN);}else mv.visitInsn(Opcodes.RETURN);mv.visitMaxs(1,Type.getArgumentsAndReturnSizes(descriptor));mv.visitEnd();return null;}
     return new MethodVisitor(Opcodes.ASM9,mv){@Override public void visitCode(){super.visitCode();
      if(method.equals("glfwInit")){hint("glfwInitHint",0x00051002,0);}
      if(method.equals("glfwCreateWindow")){
       hint("glfwWindowHint",0x00020004,0); // GLFW_VISIBLE
       hint("glfwWindowHint",0x00020001,0); // GLFW_FOCUSED
       hint("glfwWindowHint",0x0002000C,0); // GLFW_FOCUS_ON_SHOW
       System.err.println("[SilentTest] GLFW creation instrumented: hidden, unfocused, no focus on show");
      }
      if(method.equals("SDL_CreateWindow")&&descriptor.endsWith("IIJ)J")){
       int slot=0;for(Type t:Type.getArgumentTypes(descriptor)){if(t.getSort()==Type.LONG){visitVarInsn(Opcodes.LLOAD,slot);visitLdcInsn(8L);visitInsn(Opcodes.LOR);visitVarInsn(Opcodes.LSTORE,slot);}slot+=t.getSize();}
       System.err.println("[SilentTest] SDL creation instrumented: hidden");
      }
     }
     private void hint(String call,int key,int value){visitLdcInsn(key);visitLdcInsn(value);visitMethodInsn(Opcodes.INVOKESTATIC,"org/lwjgl/glfw/GLFW",call,"(II)V",false);}
     };
    }},0);return writer.toByteArray();
   }catch(Throwable e){e.printStackTrace();Runtime.getRuntime().halt(78);return null;}
  }});
 }
}
