package dev.unbox.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import dev.unbox.client.mixin.GraveDisplayAccess;
import dev.unbox.client.mixin.GraveTextDisplayAccess;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;

/** Server-authoritative grave storage for the integrated host world. */
public final class GraveKeeper {
    private static final String GRAVE = "unbox.grave.";
    private static final String OWNER = "unbox.owner.";
    private static volatile boolean remoteHostEnabled;
    private static volatile boolean remoteHostKnown;
    private static Boolean lastBroadcast;

    record HostState(boolean enabled) implements CustomPacketPayload {
        static final Type<HostState> TYPE = new Type<>(Identifier.fromNamespaceAndPath("unbox", "host_features"));
        static final StreamCodec<RegistryFriendlyByteBuf, HostState> CODEC = CustomPacketPayload.codec(
            (packet, buffer) -> buffer.writeBoolean(packet.enabled),
            buffer -> new HostState(buffer.readBoolean()));
        public Type<HostState> type() { return TYPE; }
    }

    private GraveKeeper() {}

    public static boolean hostEnabled() {
        return Minecraft.getInstance().getSingleplayerServer() != null
            ? ClientFeaturePolicy.configured("gravestones") : remoteHostEnabled&&trustedRemote();
    }
    public static boolean hostKnown() { return Minecraft.getInstance().getSingleplayerServer()!=null || remoteHostKnown&&trustedRemote(); }
    private static boolean trustedRemote(){var mc=Minecraft.getInstance();return SocialLogin.trustedClientSession()||mc.getCurrentServer()!=null&&mc.getCurrentServer().isLan();}
    static void receive(HostState state) { if(trustedRemote()){remoteHostEnabled = state.enabled; remoteHostKnown=true;} }
    static void clearClientHost() { remoteHostEnabled = false; remoteHostKnown=false; }
    static void joined(ServerPlayer player) {
        if (MapTransport.canSendHostState(player)) MapTransport.sendHostState(player, new HostState(ClientFeaturePolicy.configured("gravestones")));
    }
    public static void tick(Minecraft minecraft) {
        var server = minecraft.getSingleplayerServer();
        if (server == null) { lastBroadcast = null; return; }
        boolean enabled = ClientFeaturePolicy.configured("gravestones");
        if (lastBroadcast != null && lastBroadcast == enabled) return;
        lastBroadcast = enabled;
        server.execute(() -> { for (var player : server.getPlayerList().getPlayers()) joined(player); });
    }

    public static void onDeath(ServerPlayer player) {
        if (!player.level().getServer().isSingleplayer()
            || !ClientFeaturePolicy.configured("gravestones")
            || Boolean.TRUE.equals(player.level().getGameRules().get(GameRules.KEEP_INVENTORY))) return;

        ServerLevel level = player.level();
        var staged = new ArrayList<ItemStack>();
        // In 26.1/26.1.2 this container includes main inventory, armor and offhand equipment.
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) staged.add(stack.copy());
        }
        ItemStack carried = player.containerMenu.getCarried();
        if (!carried.isEmpty()) staged.add(carried.copy());
        if (staged.isEmpty()) return;
        BlockPos pos = emptyPlacement(level, player);
        if (pos == null) return; // No safe space: leave vanilla death drops and the existing world intact.
        var storage = GraveStorage.get(level);
        var label = new Display.TextDisplay(EntityType.TEXT_DISPLAY, level);
        label.setPos(pos.getX() + 0.5, pos.getY() + 1.3, pos.getZ() + 0.5);
        label.setNoGravity(true);
        label.setInvulnerable(true);
        String name = player.getGameProfile().name();
        ((GraveTextDisplayAccess) label).unbox$setText(Component.literal(name));
        ((GraveDisplayAccess) label).unbox$setBillboard(Display.BillboardConstraints.CENTER);
        label.addTag("unbox.stone_grave_label");
        updateLabel(label);
        // Spawn first so a failed nameplate creation cannot erase replaceable vegetation.
        if (!level.addFreshEntity(label)) return;
        if (!level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL)) {
            label.discard();
            return;
        }
        storage.graves.put(pos, new GraveStorage.Grave(pos, player.getUUID(), name, label.getUUID(), staged));
        storage.setDirty();
        GraveModel.ensure(level, label);
        // Commit all owned stacks, including cursor and cursed items, only after storage exists.
        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.getInventory().clearContent();
        player.getInventory().setChanged();
    }

    public static boolean isGrave(Level level, BlockPos pos) {
        return level instanceof ServerLevel serverLevel && GraveStorage.get(serverLevel).graves.containsKey(pos);
    }

    /** Stops commands, explosions, fluids and block replacement from orphaning the stored contents. */
    public static boolean blocksReplacement(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        var storage = GraveStorage.get(serverLevel);
        return storage.graves.containsKey(pos) && !storage.removing.contains(pos);
    }

    /** Called only after vanilla's server mining timer reaches completion; UUID is the authority. */
    public static boolean mine(ServerPlayer player, BlockPos pos) {
        ServerLevel level = player.level();
        var storage = GraveStorage.get(level);
        var grave = storage.graves.get(pos);
        if (grave == null || !grave.owner().equals(player.getUUID()) || storage.removing.contains(pos)
            || !GraveModel.isAnchor(level.getBlockState(pos))
            || player.blockActionRestricted(level, pos, player.gameMode.getGameModeForPlayer())
            || !level.mayInteract(player, pos)
            || !player.getMainHandItem().canDestroyBlock(level.getBlockState(pos), level, pos, player)) return false;

        var spawned = new ArrayList<ItemEntity>();
        for (ItemStack stack : grave.items()) {
            ItemEntity item = new ItemEntity(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack.copy());
            item.setTarget(grave.owner());
            item.setUnlimitedLifetime();
            item.setInvulnerable(true);
            item.addTag("unbox.grave_recovered");
            item.addTag(OWNER + grave.owner());
            if (!level.addFreshEntity(item)) {
                spawned.forEach(ItemEntity::discard);
                return false;
            }
            spawned.add(item);
        }
        boolean removed;
        storage.removing.add(pos);
        try { removed = level.removeBlock(pos, false); }
        finally { storage.removing.remove(pos); }
        if (!removed) {
            spawned.forEach(ItemEntity::discard);
            return false;
        }
        storage.graves.remove(pos);
        storage.setDirty();
        GraveModel.remove(level, grave);
        var label = level.getEntity(grave.label());
        if (label != null) label.discard();
        level.levelEvent(2001, pos, Block.getId(Blocks.STONE.defaultBlockState()));
        if (!player.preventsBlockDrops()) player.getMainHandItem().mineBlock(level, Blocks.STONE.defaultBlockState(), pos, player);
        return true;
    }

    static BlockPos emptyPlacement(ServerLevel level, ServerPlayer player) {
        Vec3 desired = placement(level, player.getX(), player.getY(), player.getZ());
        BlockPos center = BlockPos.containing(desired);
        // Grass/ferns occupy the player's foot block. Replace them like normal block placement
        // instead of searching upward and leaving the grave floating above the vegetation.
        for (int radius = 0; radius <= 4; radius++) {
            for (int dy = 0; dy <= 8; dy++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                BlockPos pos = center.offset(dx, dy, dz);
                if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)
                    || level.getBlockEntity(pos) != null || isGrave(level, pos)) continue;
                var state = level.getBlockState(pos);
                if (state.isAir() || state.canBeReplaced() && state.getFluidState().isEmpty()
                    && state.getCollisionShape(level,pos).isEmpty()) return pos;
            }
        }
        return null;
    }

    public static void updateLabel(Display label) {
        if (label.level() instanceof ServerLevel level && label.entityTags().contains("unbox.stone_grave_label")) {
            ((GraveDisplayAccess) label).unbox$setViewRange(UnboxClient.number("gravestones.nameDistance", 32, 16, 64) / 64f);
            GraveModel.ensure(level, label);
        }
    }

    public static boolean protectedItem(ItemEntity item) {
        return item.entityTags().contains("unbox.grave_recovered")
            || item.entityTags().stream().anyMatch(tag -> tag.startsWith(GRAVE));
    }

    /** Keeps ordinary deaths exact; a player already below the build floor is recovered at solid terrain. */
    static Vec3 placement(ServerLevel level, double x, double y, double z) {
        if (y >= level.getMinY() + 1.0) return new Vec3(x, Math.min(y, level.getMaxY() - 2.0), z);
        int blockX=(int)Math.floor(x),blockZ=(int)Math.floor(z);
        int safeY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, blockX, blockZ) + 1;
        if (safeY > level.getMinY() + 1) return new Vec3(x, safeY, z);
        // An End-island miss can have no column at the exact X/Z. Search a bounded nearby ring.
        for(int radius=8;radius<=128;radius+=8)for(int dx=-radius;dx<=radius;dx+=8)for(int dz=-radius;dz<=radius;dz+=8){
            if(Math.abs(dx)!=radius&&Math.abs(dz)!=radius)continue;
            int candidate=level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,blockX+dx,blockZ+dz)+1;
            if(candidate>level.getMinY()+1)return new Vec3(blockX+dx+0.5,candidate,blockZ+dz+0.5);
        }
        // A pathological completely empty dimension still keeps the grave inside the valid build range.
        return new Vec3(x, level.getMinY()+2.0, z);
    }

    /** Removes only an emptied marker; unclaimed item entities remain saved with the world. */
    public static void cleanup(ServerPlayer player) {
        if (player.tickCount % 20 != 0 || !player.level().getServer().isSingleplayer()) return;
        var nearby = player.level().getEntities(player, player.getBoundingBox().inflate(4.0), e ->
            e.entityTags().contains(OWNER + player.getUUID()));
        for (var entity : nearby) {
            if (!(entity instanceof ArmorStand)) continue;
            String graveTag = entity.entityTags().stream().filter(t -> t.startsWith(GRAVE)).findFirst().orElse("");
            if (!graveTag.isEmpty() && nearby.stream().noneMatch(e -> e instanceof ItemEntity && e.entityTags().contains(graveTag))) entity.discard();
        }
    }
}
