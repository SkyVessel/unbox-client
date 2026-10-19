package fuzs.puzzleslib.neoforge.impl.core.context;
import net.minecraft.core.HolderLookup;
/** Per-thread dynamic lookup while NeoForge 26.1 binds item components. */
public final class NativeComponentContext {
 public static final ThreadLocal<HolderLookup.Provider> LOOKUP=new ThreadLocal<>();
}
