package dev.unbox.unpvp.bootstrap;
import net.minecraft.launchwrapper.IClassTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Preserve the shutdown future while allowing an already stopped server to finish disconnecting. */
public final class LoadTransformer implements IClassTransformer {
    public byte[] transform(String name,String transformed,byte[] bytes){
        if(bytes==null||!transformed.equals("net.minecraft.server.integrated.IntegratedServer"))return bytes;
        ClassNode node=new ClassNode();new ClassReader(bytes).accept(node,0);int found=0;
        for(MethodNode method:node.methods)if(method.name.equals("initiateShutdown")||method.name.equals("func_71263_m"))for(AbstractInsnNode i:method.instructions.toArray())if(i instanceof MethodInsnNode){MethodInsnNode c=(MethodInsnNode)i;
            if(c.owner.equals("com/google/common/util/concurrent/Futures")&&c.name.equals("getUnchecked")&&c.desc.equals("(Ljava/util/concurrent/Future;)Ljava/lang/Object;")){
                method.instructions.insertBefore(i,new VarInsnNode(Opcodes.ALOAD,0));
                method.instructions.set(i,new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/unbox/unpvp/WorldLoading","awaitShutdown","(Ljava/util/concurrent/Future;Lnet/minecraft/server/integrated/IntegratedServer;)Ljava/lang/Object;",false));found++;
            }
        }
        if(found!=1)throw new IllegalStateException("Unbox server shutdown hook did not match Forge 1.8.9");
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_MAXS);node.accept(writer);return writer.toByteArray();
    }
}
