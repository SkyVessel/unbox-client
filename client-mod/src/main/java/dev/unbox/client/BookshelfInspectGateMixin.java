package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="net.anvian.chiseledbookshelfvisualizer.client.raycast.BlockInspector",remap=false)
public abstract class BookshelfInspectGateMixin {
 @Inject(method="inspect",at=@At("HEAD"),cancellable=true,remap=false)
 private void unbox$inspect(CallbackInfo ci){if(!ClientIntegrations.active("bookshelf"))ci.cancel();}
}
