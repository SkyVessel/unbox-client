package dev.unbox.pvp.mixin;
import java.util.List;
import net.minecraft.client.shader.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ShaderGroup.class)
public interface ShaderAccess {@Accessor("listShaders") List<Shader> unbox$shaders();}
