package dev.unbox.client.mixin;
import dev.unbox.client.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Pseudo @Mixin(targets="net.anvian.chiseledbookshelfvisualizer.client.render.BookInfoRenderer",remap=false)
public abstract class BookshelfRenderGateMixin {
 @Inject(method="hudRender",at=@At("HEAD"),cancellable=true,remap=false)
 private static void unbox$render(CallbackInfo ci){if(!ClientIntegrations.active("bookshelf"))ci.cancel();}
}
