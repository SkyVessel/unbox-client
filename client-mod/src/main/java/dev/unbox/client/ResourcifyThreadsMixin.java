package dev.unbox.client.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Resource fetch workers must not keep the JVM alive after the client closes. */
@Pseudo @Mixin(targets="dev.dediamondpro.resourcify.util.MultiThreading",remap=false)
public abstract class ResourcifyThreadsMixin {
 @Inject(method="createThread",at=@At("RETURN"),remap=false)
 private void unbox$daemonWorker(Runnable task,CallbackInfoReturnable<Thread> result){result.getReturnValue().setDaemon(true);}
}
