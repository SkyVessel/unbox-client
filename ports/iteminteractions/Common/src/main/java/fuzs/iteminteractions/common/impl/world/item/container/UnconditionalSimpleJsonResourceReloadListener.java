package fuzs.iteminteractions.common.impl.world.item.container;

import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;

/**
 * Fixes a bug in NeoForge, where the {@link com.mojang.serialization.DynamicOps} instance passed is an instance of
 * {@code ConditionalOps}, but vanilla in some cases checks for {@link net.minecraft.resources.RegistryOps} specifically
 * for certain codec operations, such as in {@link net.minecraft.resources.HolderSetCodec}.
 */
public abstract class UnconditionalSimpleJsonResourceReloadListener<T> extends SimpleJsonResourceReloadListener<T> {

    protected UnconditionalSimpleJsonResourceReloadListener(HolderLookup.Provider registries, Codec<T> codec, ResourceKey<? extends Registry<T>> registryKey) {
        super(registries, codec, registryKey);
    }

    protected UnconditionalSimpleJsonResourceReloadListener(Codec<T> codec, FileToIdConverter lister) {
        super(codec, lister);
    }

    @Override
    protected Map<Identifier, T> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, T> map = new HashMap<>();
        scanDirectory(resourceManager, this.lister, this.ops, this.codec, map);
        return map;
    }
}
