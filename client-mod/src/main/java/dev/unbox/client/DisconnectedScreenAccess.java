package dev.unbox.client.mixin;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccess {
 @Accessor("details") DisconnectionDetails unboxDetails();
}
