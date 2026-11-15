package dev.unbox.unpvp.bootstrap;

import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Mac-only adaptation of the original resize, GUI scale and absolute-input paths. */
public final class RetinaTransformer implements IClassTransformer {
    private static final String HOOK="dev/unbox/unpvp/RetinaDisplay";
    private static final String MC="net/minecraft/client/Minecraft";
    private static final byte[] MOUSE="org/lwjgl/input/Mouse".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    public byte[] transform(String name,String transformed,byte[] bytes){
        if(bytes==null||!System.getProperty("os.name","").startsWith("Mac")||!Boolean.parseBoolean(System.getProperty("org.lwjgl.opengl.Display.enableHighDPI","true")))return bytes;
        if(transformed.equals("dev.unbox.unpvp.RetinaDisplay")||!(transformed.startsWith("net.minecraft.client.")||transformed.startsWith("dev.unbox.unpvp.")))return bytes;
        // Most client classes have no absolute mouse input. Avoid building their ASM trees.
        if(!transformed.equals("net.minecraft.client.Minecraft")&&!transformed.equals("net.minecraft.client.gui.ScaledResolution")&&!mentionsMouse(bytes))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean changed=false;
        if(transformed.equals("net.minecraft.client.Minecraft")){
            MethodNode resize=find(node,"(II)V","resize","func_71370_a"),check=find(node,"()V","checkWindowResize","func_175604_i");
            MethodNode create=find(node,"()V","createDisplay","func_175609_am"),loop=find(node,"()V","runGameLoop","func_71411_J");
            check.instructions.clear();check.tryCatchBlocks.clear();if(check.localVariables!=null)check.localVariables.clear();
            LabelNode end=new LabelNode();check.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));check.instructions.add(call("needsResize","(L"+MC+";)Z"));check.instructions.add(new JumpInsnNode(Opcodes.IFEQ,end));
            check.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));check.instructions.add(call("width","()I"));check.instructions.add(call("height","()I"));check.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.name,resize.name,resize.desc,false));
            check.instructions.add(end);check.instructions.add(new FrameNode(Opcodes.F_SAME,0,null,0,null));check.instructions.add(new InsnNode(Opcodes.RETURN));
            for(AbstractInsnNode i:create.instructions.toArray())if(i.getOpcode()==Opcodes.RETURN){InsnList hook=new InsnList();hook.add(new VarInsnNode(Opcodes.ALOAD,0));hook.add(call("initialize","(L"+MC+";)V"));create.instructions.insertBefore(i,hook);}
            InsnList head=new InsnList();head.add(new VarInsnNode(Opcodes.ALOAD,0));head.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,node.name,check.name,check.desc,false));loop.instructions.insert(head);changed=true;
        }
        if(transformed.equals("net.minecraft.client.gui.ScaledResolution")){
            String factor=null;for(FieldNode f:node.fields)if(f.name.equals("scaleFactor")||f.name.equals("field_78330_e"))factor=f.name;
            if(factor==null)throw new IllegalStateException("Retina GUI scale field missing");
            for(MethodNode m:node.methods)if(m.name.equals("<init>"))for(AbstractInsnNode i:m.instructions.toArray()){
                if(i instanceof FieldInsnNode&&i.getOpcode()==Opcodes.GETFIELD){FieldInsnNode f=(FieldInsnNode)i;if(f.owner.equals(MC)&&(f.name.equals("displayWidth")||f.name.equals("displayHeight")||f.name.equals("field_71443_c")||f.name.equals("field_71440_d")))m.instructions.insert(i,call("points","(I)I"));}
                if(i.getOpcode()==Opcodes.RETURN){InsnList tail=new InsnList();tail.add(new VarInsnNode(Opcodes.ALOAD,0));tail.add(new InsnNode(Opcodes.DUP));tail.add(new FieldInsnNode(Opcodes.GETFIELD,node.name,factor,"I"));tail.add(call("pixels","(I)I"));tail.add(new FieldInsnNode(Opcodes.PUTFIELD,node.name,factor,"I"));m.instructions.insertBefore(i,tail);}
            }
            changed=true;
        }
        // Only absolute GUI positions change units. Relative camera motion and scroll stay untouched.
        for(MethodNode m:node.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)i;
            if(c.owner.equals("org/lwjgl/input/Mouse")&&c.desc.equals("()I")&&(c.name.equals("getX")||c.name.equals("getY")||c.name.equals("getEventX")||c.name.equals("getEventY"))){m.instructions.insert(i,call("pixels","(I)I"));changed=true;}
        }
        if(!changed)return bytes;
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
    private static MethodInsnNode call(String name,String desc){return new MethodInsnNode(Opcodes.INVOKESTATIC,HOOK,name,desc,false);}
    private static boolean mentionsMouse(byte[] bytes){outer:for(int i=0;i<=bytes.length-MOUSE.length;i++){for(int j=0;j<MOUSE.length;j++)if(bytes[i+j]!=MOUSE[j])continue outer;return true;}return false;}
    private static MethodNode find(ClassNode node,String desc,String named,String srg){for(MethodNode m:node.methods)if(m.desc.equals(desc)&&(m.name.equals(named)||m.name.equals(srg)))return m;throw new IllegalStateException("Retina hook missing: "+named);}
}
