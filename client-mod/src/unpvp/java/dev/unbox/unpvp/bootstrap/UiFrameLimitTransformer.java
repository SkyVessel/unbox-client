package dev.unbox.unpvp.bootstrap;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
/** Removes vanilla's hard-coded 30 FPS title-menu cap only while our UI is visible. */
public final class UiFrameLimitTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformed,byte[] bytes){
        if(bytes!=null&&"Config".equals(transformed)&&System.getProperty("os.name","").startsWith("Mac")){
            // OptiFine's false/true resize workaround calls AppKit off its main thread on recent macOS.
            // Minecraft already creates a resizable window. Keep that state; do not alter input/fullscreen.
            ClassNode config=new ClassNode();new ClassReader(bytes).accept(config,0);int removed=0;
            for(MethodNode m:config.methods)for(AbstractInsnNode i:m.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode call=(MethodInsnNode)i;if(call.owner.equals("org/lwjgl/opengl/Display")&&call.name.equals("setResizable")&&call.desc.equals("(Z)V")){m.instructions.set(i,new InsnNode(Opcodes.POP));removed++;}}
            ClassWriter writer=new ClassWriter(0);config.accept(writer);System.out.println("[unpvp] macOS OptiFine resize compatibility: "+removed);return writer.toByteArray();
        }
        if(!"net.minecraft.client.Minecraft".equals(transformed))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);boolean found=false;
        for(MethodNode method:node.methods)if(method.desc.equals("()I")&&(method.name.equals("j")||method.name.equals("getLimitFramerate")||method.name.equals("func_90020_K"))){
            InsnList head=new InsnList();LabelNode fallback=new LabelNode();head.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/unbox/unpvp/UiFramePolicy","limit","()I",false));head.add(new InsnNode(Opcodes.DUP));head.add(new InsnNode(Opcodes.ICONST_M1));head.add(new JumpInsnNode(Opcodes.IF_ICMPEQ,fallback));head.add(new InsnNode(Opcodes.IRETURN));head.add(fallback);head.add(new FrameNode(Opcodes.F_SAME1,0,null,1,new Object[]{Opcodes.INTEGER}));head.add(new InsnNode(Opcodes.POP));method.instructions.insert(head);found=true;
        }
        if(!found)throw new IllegalStateException("Unbox UI frame-rate hook did not match Minecraft 1.8.9");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
