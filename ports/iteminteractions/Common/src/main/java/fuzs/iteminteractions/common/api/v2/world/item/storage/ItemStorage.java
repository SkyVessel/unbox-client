package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.puzzleslib.common.api.init.v3.registry.RegistryFactory;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import org.joml.Vector2ic;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * A provider for an item that enables bundle-like inventory interactions for a container item, meaning items can be
 * extracted and inserted by right-clicking onto the item, as well as bundle-like tooltips.
 * <p>
 * This overrides any already implemented behavior, the default providers in Easy Shulker Boxes actually do this for
 * vanilla bundles.
 */
public interface ItemStorage {
    /**
     * The {@link ItemStorageType} registry key.
     */
    ResourceKey<Registry<ItemStorageType<?>>> REGISTRY_KEY = ResourceKey.createRegistryKey(ItemInteractions.id(
            "item_storage_type"));
    /**
     * The {@link ItemStorageType} registry.
     */
    Registry<ItemStorageType<?>> REGISTRY = RegistryFactory.INSTANCE.createSynced(REGISTRY_KEY,
            ItemInteractions.id("empty"));
    /**
     * Codec that additionally to the provider itself also includes the provider type.
     */
    MapCodec<ItemStorage> CODEC = REGISTRY.byNameCodec().dispatchMap(ItemStorage::getType, ItemStorageType::codec);
    /**
     * Stream codec that additionally to the provider itself also includes the provider type.
     */
    StreamCodec<RegistryFriendlyByteBuf, ItemStorage> STREAM_CODEC = ByteBufCodecs.registry(ItemStorage.REGISTRY_KEY)
            .dispatch(ItemStorage::getType, ItemStorageType::streamCodec);

    /**
     * Does this provider support extracting and inserting items for the container item.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return are item inventory interactions allowed
     *
     * @see Slot#allowModification(Player)
     */
    boolean allowModification(ItemStack itemStack, Player player);

    /**
     * Does the item stack have data for stored items.
     * <p>
     * An easy check if the corresponding container is empty without having to create a container instance. Mainly used
     * by the tooltip image and the client-side mouse scroll handler.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return is the item stack data with stored items present
     */
    boolean hasContents(ItemStack itemStack, Player player);

    /**
     * Called when the item stack providing the storage is stacked onto another item.
     *
     * @param holder      the holder for this provider
     * @param itemStack   the item stack providing the storage
     * @param slot        access to the slot holding the item stack providing the storage
     * @param otherItem   the item stack the storage is stacked onto
     * @param otherSlot   the slot holding the item stack the storage is stacked onto
     * @param clickAction the click action
     * @param player      the player performing the interaction
     * @return was the interaction handled
     */
    boolean overrideStackedOnOther(ItemStorageHolder holder, ItemStack itemStack, SlotAccess slot, ItemStack otherItem, Slot otherSlot, ClickAction clickAction, Player player);

    /**
     * Called when another item stack is stacked onto the item stack providing the storage.
     *
     * @param holder      the holder for this provider
     * @param itemStack   the item stack providing the storage
     * @param slot        the slot holding the item stack providing the storage
     * @param otherItem   the item stack stacked onto the storage
     * @param otherSlot   access to the slot holding the item stack stacked onto the storage
     * @param clickAction the click action
     * @param player      the player performing the interaction
     * @return was the interaction handled
     */
    boolean overrideOtherStackedOnMe(ItemStorageHolder holder, ItemStack itemStack, Slot slot, ItemStack otherItem, SlotAccess otherSlot, ClickAction clickAction, Player player);

    /**
     * Is the provided item stack allowed to be added to the container provided by the item stack providing the
     * storage.
     * <p>
     * This should be the same behavior as vanilla's {@link Item#canFitInsideContainerItems()}.
     *
     * @param otherItem the stack to be added to the container
     * @return is the stack allowed to be added to the container
     *
     * @see Slot#mayPlace(ItemStack)
     */
    boolean mayPlace(ItemStack otherItem);

    /**
     * Get the container implementation provided by the item stack providing the storage as a {@link SimpleContainer},
     * must not return {@code null}.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the provided container
     */
    Container getReadOnlyContainer(ItemStack itemStack, Player player);

    /**
     * Get the container implementation provided by the item stack providing the storage for modifications, writing any
     * changes back to the item stack.
     *
     * @param itemStack  the item stack providing the storage
     * @param player     the player performing the interaction
     * @param slotAccess access to the slot holding the item stack providing the storage, or {@code null} when only
     *                   reading
     * @return the provided container
     */
    Container getWritableContainer(ItemStack itemStack, Player player, SlotAccess slotAccess);

    /**
     * Is there enough space in the container provided by the item stack providing the storage to add the provided stack
     * (not necessarily the full stack).
     *
     * @param itemStack the item stack providing the storage
     * @param otherItem the stack to be added to the container
     * @param player    the player interacting with both item stacks
     * @return is adding any portion of the stack to the container possible
     */
    boolean canAddItem(ItemStack itemStack, ItemStack otherItem, Player player);

    /**
     * How much space is available in the container provided by the item stack providing the storage to add the provided
     * stack.
     * <p>
     * Mainly used by bundles, otherwise {@link ItemStorage#canAddItem} should be enough.
     *
     * @param itemStack the item stack providing the storage
     * @param otherItem the stack to be added to the container
     * @param player    the player interacting with both item stacks
     * @return the portion of the stack that can be added to the container
     */
    int getAcceptableItemCount(ItemStack itemStack, ItemStack otherItem, Player player);

    /**
     * Get the tooltip image for the container item.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the tooltip image if present
     *
     * @see Item#getTooltipImage(ItemStack)
     */
    Optional<Optional<TooltipComponent>> getTooltipImage(ItemStack itemStack, Player player);

    /**
     * Is a bar visible for the container item, indicating how full the container is.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return is the bar visible
     *
     * @see Item#isBarVisible(ItemStack)
     */
    Optional<Boolean> isBarVisible(ItemStack itemStack, Player player);

    /**
     * Get the width of the bar for the container item.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the bar width
     *
     * @see Item#getBarWidth(ItemStack)
     */
    OptionalInt getBarWidth(ItemStack itemStack, Player player);

    /**
     * Get the color of the bar for the container item.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the bar color
     *
     * @see Item#getBarColor(ItemStack)
     */
    OptionalInt getBarColor(ItemStack itemStack, Player player);

    /**
     * Get the selected item index inside the container.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @return the selected item index
     */
    int getSelectedItem(ItemStack itemStack, Player player);

    /**
     * Update the selected item index when scrolling through the tooltip.
     *
     * @param itemStack the item stack providing the storage
     * @param player    the player performing the interaction
     * @param container the container provided by the item stack
     * @param scrollXY  the scroll direction
     * @return the updated selected item index
     */
    int scrollSelectedItem(ItemStack itemStack, Player player, Container container, Vector2ic scrollXY);

    /**
     * Called when the selected item index for a container item changes from the player scrolling through the tooltip.
     *
     * @param itemStack    the item stack providing the storage
     * @param player       the player
     * @param selectedItem the updated selected slot inside the container item
     * @param slotClicked  the action was triggered by clicking on the item slot
     * @see net.minecraft.world.item.BundleItem#toggleSelectedItem(ItemStack, int)
     */
    void toggleSelectedItem(ItemStack itemStack, Player player, int selectedItem, boolean slotClicked);

    /**
     * Plays a sound when extracting an item from the container.
     *
     * @param player the player performing the interaction
     */
    void playRemoveOneSound(Player player);

    /**
     * Plays a sound when inserting an item into the container.
     *
     * @param player the player performing the interaction
     */
    void playInsertSound(Player player);

    /**
     * @return the item storage provider type
     */
    ItemStorageType<?> getType();

    /**
     * A definition for which items use a specific {@link ItemStorage}.
     *
     * @param items   the items this definition applies to
     * @param storage the item storage provider
     * @see net.minecraft.world.item.crafting.Recipe
     */
    record Definition(HolderSet<Item> items, ItemStorage storage) {
        /**
         * The {@link Definition} registry key.
         */
        public static final ResourceKey<Registry<Definition>> REGISTRY_KEY = ResourceKey.createRegistryKey(
                ItemInteractions.id("item_storage"));
        /**
         * Codec for a {@link Definition}.
         */
        public static final Codec<Definition> DIRECT_CODEC = RecordCodecBuilder.create(instance -> {
            return instance.group(Ingredient.NON_AIR_HOLDER_SET_CODEC.lenientOptionalFieldOf("supported_items",
                            HolderSet.empty()).forGetter(Definition::items), ItemStorage.CODEC.forGetter(Definition::storage))
                    .apply(instance, Definition::new);
        });
        /**
         * Codec for a {@link Definition} registry key.
         */
        public static final Codec<ResourceKey<Definition>> KEY_CODEC = ResourceKey.codec(REGISTRY_KEY);
        /**
         * Codec for a {@link Definition} holder.
         */
        public static final Codec<Holder<Definition>> CODEC = RegistryFileCodec.create(REGISTRY_KEY, DIRECT_CODEC);
        /**
         * Codec for a {@link Definition} holder set.
         */
        public static final Codec<HolderSet<Definition>> LIST_CODEC = RegistryCodecs.homogeneousList(REGISTRY_KEY,
                DIRECT_CODEC);
    }
}
