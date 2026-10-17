package dev.unbox.client;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Full stacks live in dimension saved data, never exposed as loot before an owner mines the grave. */
final class GraveStorage extends SavedData {
    record Grave(BlockPos pos, UUID owner, String name, UUID label, List<ItemStack> items, List<UUID> model) {
        Grave(BlockPos pos, UUID owner, String name, UUID label, List<ItemStack> items) {
            this(pos, owner, name, label, items, List.of());
        }
        static final Codec<Grave> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockPos.CODEC.fieldOf("pos").forGetter(Grave::pos),
            UUIDUtil.CODEC.fieldOf("owner").forGetter(Grave::owner),
            Codec.STRING.fieldOf("name").forGetter(Grave::name),
            UUIDUtil.CODEC.fieldOf("label").forGetter(Grave::label),
            ItemStack.CODEC.listOf().fieldOf("items").forGetter(Grave::items),
            UUIDUtil.CODEC.listOf().optionalFieldOf("model", List.of()).forGetter(Grave::model)
        ).apply(instance, Grave::new));
    }

    private static final Codec<GraveStorage> CODEC = Grave.CODEC.listOf().fieldOf("graves").codec()
        .xmap(GraveStorage::new, storage -> new ArrayList<>(storage.graves.values()));
    static final SavedDataType<GraveStorage> TYPE = new SavedDataType<>(
        Identifier.fromNamespaceAndPath("unbox", "stone_graves"), GraveStorage::new, CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    final Map<BlockPos, Grave> graves = new HashMap<>();
    // Server-thread-only transaction guards; never serialized or treated as ownership permission.
    final Set<BlockPos> removing = new HashSet<>();

    private GraveStorage() {}
    private GraveStorage(List<Grave> entries) {
        for (Grave grave : entries) graves.put(grave.pos(), grave);
    }
    static GraveStorage get(ServerLevel level) { return level.getDataStorage().computeIfAbsent(TYPE); }
}
