package dev.unbox.client.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Display.TextDisplay.class)
public interface GraveTextDisplayAccess {
    @Invoker("setBackgroundColor") void unbox$setBackgroundColor(int color);
    @Invoker("setFlags") void unbox$setFlags(byte flags);
    @Invoker("getText") Component unbox$getText();
    @Invoker("setText") void unbox$setText(Component text);
}
