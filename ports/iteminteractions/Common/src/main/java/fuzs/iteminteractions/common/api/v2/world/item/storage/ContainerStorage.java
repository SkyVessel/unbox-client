package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fuzs.iteminteractions.common.api.v2.world.inventory.tooltip.ItemContentsTooltip;
import fuzs.iteminteractions.common.api.v2.world.item.DyeBackedColor;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * A {@link ComponentBackedStorage} implementation backed by an {@link ItemContainerContents} data component, like
 * shulker boxes.
 */
public class ContainerStorage extends ComponentBackedStorage {
    /**
     * Codec for a {@link ContainerStorage} instance.
     */
    public static final MapCodec<ContainerStorage> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(inventoryWidthCodec(),
                        inventoryHeightCodec(),
                        backgroundColorCodec(),
                        itemContentsCodec(),
                        interactionPermissionsCodec(),
                        equipmentSlotsCodec())
                .apply(instance,
                        (Integer inventoryWidth, Integer inventoryHeight, Optional<DyeBackedColor> dyeColor, StorageOptions storageOptions, InteractionPermissions interactionPermissions, EquipmentSlotGroup equipmentSlots) -> {
                            return new ContainerStorage(inventoryWidth,
                                    inventoryHeight,
                                    dyeColor.orElse(null),
                                    storageOptions).interactionPermissions(interactionPermissions)
                                    .equipmentSlots(equipmentSlots);
                        });
    });
    private static final EquipmentSlot[] EQUIPMENT_SLOTS = EquipmentSlot.values();
    /**
     * The default number of columns of the inventory.
     */
    public static final int DEFAULT_INVENTORY_WIDTH = 9;
    /**
     * The default number of rows of the inventory.
     */
    public static final int DEFAULT_INVENTORY_HEIGHT = 3;

    final int inventoryWidth;
    final int inventoryHeight;
    @Nullable
    final DyeBackedColor dyeColor;
    InteractionPermissions interactionPermissions = InteractionPermissions.ALWAYS;
    EquipmentSlotGroup equipmentSlots = EquipmentSlotGroup.ANY;

    /**
     * Creates a new storage with the default inventory size.
     */
    public ContainerStorage() {
        this(DEFAULT_INVENTORY_WIDTH, DEFAULT_INVENTORY_HEIGHT);
    }

    /**
     * @param inventoryWidth  the number of columns of the inventory
     * @param inventoryHeight the number of rows of the inventory
     */
    public ContainerStorage(int inventoryWidth, int inventoryHeight) {
        this(inventoryWidth, inventoryHeight, null);
    }

    /**
     * @param inventoryWidth  the number of columns of the inventory
     * @param inventoryHeight the number of rows of the inventory
     * @param dyeColor        the dye color used for the tooltip image background, or {@code null} for the default
     */
    public ContainerStorage(int inventoryWidth, int inventoryHeight, @Nullable DyeBackedColor dyeColor) {
        this(inventoryWidth, inventoryHeight, dyeColor, StorageOptions.DEFAULT);
    }

    /**
     * @param dyeColor       the dye color used for the tooltip image background, or {@code null} for the default
     * @param storageOptions the common options for this storage
     */
    public ContainerStorage(@Nullable DyeBackedColor dyeColor, StorageOptions storageOptions) {
        this(DEFAULT_INVENTORY_WIDTH, DEFAULT_INVENTORY_HEIGHT, dyeColor, storageOptions);
    }

    /**
     * @param inventoryWidth  the number of columns of the inventory
     * @param inventoryHeight the number of rows of the inventory
     * @param dyeColor        the dye color used for the tooltip image background, or {@code null} for the default
     * @param storageOptions  the common options for this storage
     */
    public ContainerStorage(int inventoryWidth, int inventoryHeight, @Nullable DyeBackedColor dyeColor, StorageOptions storageOptions) {
        super(storageOptions);
        this.inventoryWidth = inventoryWidth;
        this.inventoryHeight = inventoryHeight;
        this.dyeColor = dyeColor;
    }

    /**
     * @param <T> the storage type
     * @return the codec builder for {@link #getInventoryWidth()}
     */
    protected static <T extends ContainerStorage> RecordCodecBuilder<T, Integer> inventoryWidthCodec() {
        return ExtraCodecs.POSITIVE_INT.fieldOf("inventory_width").forGetter(ContainerStorage::getInventoryWidth);
    }

    /**
     * @param <T> the storage type
     * @return the codec builder for {@link #getInventoryHeight()}
     */
    protected static <T extends ContainerStorage> RecordCodecBuilder<T, Integer> inventoryHeightCodec() {
        return ExtraCodecs.POSITIVE_INT.fieldOf("inventory_height").forGetter(ContainerStorage::getInventoryHeight);
    }

    /**
     * @param <T> the storage type
     * @return the codec builder for {@link #getDyeColor()}
     */
    protected static <T extends ContainerStorage> RecordCodecBuilder<T, Optional<DyeBackedColor>> backgroundColorCodec() {
        return DyeBackedColor.CODEC.optionalFieldOf("background_color")
                .forGetter((T storage) -> Optional.ofNullable(storage.getDyeColor()));
    }

    /**
     * @param <T> the storage type
     * @return the codec builder for {@link #getInteractionPermissions()}
     */
    protected static <T extends ContainerStorage> RecordCodecBuilder<T, InteractionPermissions> interactionPermissionsCodec() {
        return InteractionPermissions.CODEC.fieldOf("interaction_permissions")
                .orElse(InteractionPermissions.ALWAYS)
                .forGetter(ContainerStorage::getInteractionPermissions);
    }

    /**
     * @param <T> the storage type
     * @return the codec builder for {@link #getEquipmentSlots()}
     */
    protected static <T extends ContainerStorage> RecordCodecBuilder<T, EquipmentSlotGroup> equipmentSlotsCodec() {
        return EquipmentSlotGroup.CODEC.fieldOf("equipment_slots")
                .orElse(EquipmentSlotGroup.ANY)
                .forGetter(ContainerStorage::getEquipmentSlots);
    }

    /**
     * Set the interaction permissions.
     *
     * @param interactionPermissions the new interaction permissions
     * @return the updated storage
     */
    public ContainerStorage interactionPermissions(InteractionPermissions interactionPermissions) {
        this.interactionPermissions = interactionPermissions;
        return this;
    }

    /**
     * Set the equipment slots required for interactions.
     *
     * @param equipmentSlots the new equipment slots
     * @return the updated storage
     */
    public ContainerStorage equipmentSlots(EquipmentSlotGroup equipmentSlots) {
        this.equipmentSlots = equipmentSlots;
        return this;
    }

    @Override
    public int getGridWidth(int itemCount) {
        return this.getInventoryWidth();
    }

    @Override
    public int getGridHeight(int itemCount) {
        return this.getInventoryHeight();
    }

    /**
     * @return the number of columns of the inventory
     */
    public int getInventoryWidth() {
        return this.inventoryWidth;
    }

    /**
     * @return the number of rows of the inventory
     */
    public int getInventoryHeight() {
        return this.inventoryHeight;
    }

    /**
     * @return the total number of slots of the inventory
     */
    public int getInventorySize() {
        return this.getInventoryWidth() * this.getInventoryHeight();
    }

    /**
     * @return the dye color used for the tooltip image background, or {@code null} for the default
     */
    public @Nullable DyeBackedColor getDyeColor() {
        return this.dyeColor;
    }

    /**
     * @return the interaction permissions
     */
    public InteractionPermissions getInteractionPermissions() {
        return this.interactionPermissions;
    }

    /**
     * @return the equipment slots required for interactions
     */
    public EquipmentSlotGroup getEquipmentSlots() {
        return this.equipmentSlots;
    }

    @Override
    public boolean allowModification(ItemStack itemStack, Player player) {
        if (!itemStack.has(DataComponents.CONTAINER)) {
            return false;
        } else if (!this.getInteractionPermissions().allowsPlayerInteractions(player)) {
            return false;
        } else if (!this.matchesRequiredEquipmentSlots(itemStack, player)) {
            return false;
        } else {
            return true;
        }
    }

    private boolean matchesRequiredEquipmentSlots(ItemStack itemStack, Player player) {
        if (player.getAbilities().instabuild || this.getEquipmentSlots() == EquipmentSlotGroup.ANY) {
            return true;
        } else {
            for (EquipmentSlot slot : EQUIPMENT_SLOTS) {
                if (this.getEquipmentSlots().test(slot)) {
                    ItemStack itemInSlot = player.getItemBySlot(slot);
                    if (itemStack == itemInSlot) {
                        return true;
                    }
                }
            }

            return false;
        }
    }

    @Override
    public boolean hasContents(ItemStack itemStack, Player player) {
        ItemContainerContents contents = itemStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        return contents != ItemContainerContents.EMPTY && contents.nonEmptyItemCopyStream().findAny().isPresent();
    }

    @Override
    public SimpleContainer getWritableContainer(ItemStack itemStack, Player player, @Nullable SlotAccess slotAccess) {
        return new SimpleContainer(this.getInventorySize()) {
            {
                itemStack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(this.getItems());
            }

            @Override
            public void setChanged() {
                if (slotAccess != null) {
                    if (this.stillValid(player)) {
                        itemStack.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(this.getItems()));
                    }
                } else {
                    throw new UnsupportedOperationException();
                }
            }

            @Override
            public boolean stillValid(Player player) {
                return slotAccess == null || slotAccess.get() == itemStack;
            }
        };
    }

    @Override
    public TooltipComponent createTooltipImageComponent(ItemStack itemStack, Player player, NonNullList<ItemStack> itemList) {
        return new ItemContentsTooltip(itemList,
                this.getSelectedItem(itemStack, player),
                this.getGridWidth(itemList.size()),
                this.getGridHeight(itemList.size()),
                this.getResolvedDyeColor());
    }

    /**
     * @return the resolved dye color value, or {@code -1} when no dye color is set
     */
    public int getResolvedDyeColor() {
        return this.getDyeColor() != null ? this.getDyeColor().getValue() : -1;
    }

    @Override
    public ItemStorageType<?> getType() {
        return ModRegistry.CONTAINER_ITEM_STORAGE_TYPE.value();
    }

    /**
     * The interaction permissions controlling who may modify the container.
     */
    public enum InteractionPermissions implements StringRepresentable {
        /**
         * Player interactions are always allowed.
         */
        ALWAYS,
        /**
         * Player interactions are only allowed in creative mode.
         */
        CREATIVE_ONLY,
        /**
         * Player interactions are never allowed.
         */
        NEVER;

        /**
         * Codec for an {@link InteractionPermissions} value.
         */
        public static final Codec<InteractionPermissions> CODEC = StringRepresentable.fromValues(InteractionPermissions::values);

        /**
         * @param player the player performing the interaction
         * @return are player interactions allowed
         */
        public boolean allowsPlayerInteractions(Player player) {
            return this == ALWAYS || this != NEVER && player.getAbilities().instabuild;
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
