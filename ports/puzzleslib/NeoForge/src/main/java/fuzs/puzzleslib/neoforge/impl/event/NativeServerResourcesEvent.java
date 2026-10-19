package fuzs.puzzleslib.neoforge.impl.event;
import net.minecraft.server.ReloadableServerResources;
import net.minecraft.core.RegistryAccess;
import net.neoforged.bus.api.Event;
/** Backport of the server-resource context added to later NeoForge tag events. */
public final class NativeServerResourcesEvent extends Event {
 public final ReloadableServerResources resources;
 public final RegistryAccess registries;
 public NativeServerResourcesEvent(ReloadableServerResources resources, RegistryAccess registries){this.resources=resources;this.registries=registries;}
}
