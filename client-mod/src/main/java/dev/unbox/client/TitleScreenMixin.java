package dev.unbox.client.mixin;

import dev.unbox.client.UnboxHomeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(Minecraft.class)
public class TitleScreenMixin {
    @ModifyVariable(method="setScreen",at=@At("HEAD"),argsOnly=true)
    private Screen unboxTitle(Screen screen){return screen!=null&&screen.getClass()==TitleScreen.class&&!((Minecraft)(Object)this).isDemo()?new UnboxHomeScreen(false):screen;}

    // Closing a parentless screen passes null; vanilla creates its title inside
    // setScreen after HEAD. Replace that fallback before it is installed as well.
    @ModifyVariable(method="setScreen",at=@At("STORE"),argsOnly=true)
    private Screen unboxFallbackTitle(Screen screen){return unboxTitle(screen);}
}
