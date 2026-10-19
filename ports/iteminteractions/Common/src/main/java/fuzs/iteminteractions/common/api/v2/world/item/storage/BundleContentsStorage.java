package fuzs.iteminteractions.common.api.v2.world.item.storage;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import fuzs.iteminteractions.common.api.v2.world.inventory.tooltip.BundleContentsTooltip;
import fuzs.iteminteractions.common.impl.init.ModRegistry;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.BundleContents;
import org.apache.commons.lang3.math.Fraction;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.Stream;

/**
 * A {@link ComponentBackedStorage} implementation backed by a {@link BundleContents} data component, like bundles.
 */
public class BundleContentsStorage extends ComponentBackedStorage {
    /**
     * Codec for a {@link BundleContentsStorage} instance.
     */
    public static final MapCodec<BundleContentsStorage> CODEC = RecordCodecBuilder.mapCodec(instance -> {
        return instance.group(capacityMultiplierCodec(), itemContentsCodec())
                .apply(instance, BundleContentsStorage::new);
    });
    /**
     * The default capacity multiplier.
     */
    public static final int DEFAULT_CAPACITY_MULTIPLIER = 1;

    final int capacityMultiplier;

    /**
     * Creates a new storage with the default capacity multiplier.
     */
    public BundleContentsStorage() {
        this(StorageOptions.DEFAULT);
    }

    /**
     * @param storageOptions the common options for this storage
     */
    public BundleContentsStorage(StorageOptions storageOptions) {
        this(DEFAULT_CAPACITY_MULTIPLIER, storageOptions);
    }

    /**
     * @param capacityMultiplier the capacity multiplier, must be positive
     * @param storageOptions     the common options for this storage
     */
    public BundleContentsStorage(int capacityMultiplier, StorageOptions storageOptions) {
        super(storageOptions);
        Preconditions.checkArgument(capacityMultiplier > 0, "capacity multiplier must be positive");
        this.capacityMultiplier = capacityMultiplier;
    }

    /**
     * Codec for the {@link #capacityMultiplier} stored under the {@code capacity_multiplier} key.
     *
     * @param <T> the storage type
     * @return the record codec builder
     */
    protected static <T extends BundleContentsStorage> RecordCodecBuilder<T, Integer> capacityMultiplierCodec() {
        return ExtraCodecs.POSITIVE_INT.fieldOf("capacity_multiplier")
                .forGetter((T storage) -> storage.capacityMultiplier);
    }

    /**
     * @param itemStack the item stack providing the storage
     * @return the maximum weight of the container
     */
    public Fraction getMaxWeight(ItemStack itemStack) {
        return Fraction.getFraction(this.capacityMultiplier, DEFAULT_CAPACITY_MULTIPLIER);
    }

    /**
     * @param itemStack the item stack providing the storage
     * @return the current weight of the container
     */
    public Fraction getWeight(ItemStack itemStack) {
        BundleContents contents = itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return BundleItem.getWeightSafe(contents);
    }

    /**
     * @param itemStack the item stack providing the storage
     * @return the current weight of the container relative to its maximum weight
     */
    public Fraction getRelativeWeight(ItemStack itemStack) {
        return this.getWeight(itemStack).divideBy(this.getMaxWeight(itemStack));
    }

    @Override
    public boolean allowModification(ItemStack itemStack, Player player) {
        return itemStack.has(DataComponents.BUNDLE_CONTENTS);
    }

    @Override
    public boolean hasContents(ItemStack itemStack, Player player) {
        BundleContents contents = itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return contents != BundleContents.EMPTY && !contents.isEmpty();
    }

    @Override
    public SimpleContainer getWritableContainer(ItemStack itemStack, Player player, @Nullable SlotAccess slotAccess) {
        return new SimpleContainer(this.getItemStream(itemStack, slotAccess != null).toArray(ItemStack[]::new)) {
            @Override
            public void setChanged() {
                if (slotAccess != null) {
                    if (this.stillValid(player)) {
                        itemStack.set(DataComponents.BUNDLE_CONTENTS,
                                BundleContentsStorage.this.createUpdatedContents(this, this.getItems()));
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

    private Stream<ItemStack> getItemStream(ItemStack itemStack, boolean acceptItems) {
        Stream<ItemStack> itemStream = itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY)
                .itemCopyStream();
        if (acceptItems) {
            // Add one additional slot, so we can add items in the inventory.
            return switch (this.getRemovalDirection()) {
                case POSITIVE -> Stream.concat(Stream.of(ItemStack.EMPTY), itemStream);
                case NEGATIVE -> Stream.concat(itemStream, Stream.of(ItemStack.EMPTY));
            };
        } else {
            return itemStream;
        }
    }

    private BundleContents createUpdatedContents(Container container, NonNullList<ItemStack> itemList) {
        if (container.isEmpty()) {
            return BundleContents.EMPTY;
        } else {
            ImmutableList.Builder<ItemStackTemplate> builder = ImmutableList.builder();
            for (ItemStack item : itemList) {
                if (!item.isEmpty()) {
                    builder.add(ItemStackTemplate.fromNonEmptyStack(item));
                }
            }

            // We discard the selected item, but as this is only triggered when the container has changed, the selected item would have been erased anyways in vanilla.
            return new BundleContents(builder.build());
        }
    }

    @Override
    public boolean canAddItem(ItemStack itemStack, ItemStack otherItem, Player player) {
        return this.getMaxAmountToAdd(itemStack, otherItem) > 0;
    }

    @Override
    public int getAcceptableItemCount(ItemStack itemStack, ItemStack otherItem, Player player) {
        return Math.min(this.getMaxAmountToAdd(itemStack, otherItem),
                super.getAcceptableItemCount(itemStack, otherItem, player));
    }

    /**
     * Get the maximum amount of items that can still be added to the container.
     *
     * @param itemStack the item stack providing the storage
     * @param otherItem the stack to be added to the container
     * @return the maximum amount of items that can be added
     *
     * @see BundleContents.Mutable#getMaxAmountToAdd(Fraction)
     * @see BundleContents.Mutable#tryInsert(ItemStack)
     */
    public int getMaxAmountToAdd(ItemStack itemStack, ItemStack otherItem) {
        Fraction itemWeight = this.getWeight(itemStack);
        Fraction remainingWeight = this.getMaxWeight(itemStack).subtract(itemWeight);
        DataResult<Fraction> otherItemWeight = BundleContents.getWeight(otherItem);
        if (otherItemWeight.isError()) {
            return 0;
        } else {
            return Math.max(remainingWeight.divideBy(otherItemWeight.getOrThrow()).intValue(), 0);
        }
    }

    /**
     * Adding items at the front is consistent with vanilla behavior.
     * <p>
     * This requires additional handling when dealing with the selected item.
     */
    @Override
    public Direction.AxisDirection getRemovalDirection() {
        return Direction.AxisDirection.POSITIVE;
    }

    @Override
    public int getGridWidth(int itemCount) {
        return Math.max(1, Mth.ceil(Math.sqrt(itemCount)));
    }

    @Override
    public int getGridHeight(int itemCount) {
        return Mth.positiveCeilDiv(itemCount, this.getGridWidth(itemCount));
    }

    @Override
    public void toggleSelectedItem(ItemStack itemStack, Player player, int selectedItem, boolean slotClicked) {
        this.setSelectedItem(itemStack, player, selectedItem);
        BundleItem.toggleSelectedItem(itemStack, selectedItem);
    }

    @Override
    public TooltipComponent createTooltipImageComponent(ItemStack itemStack, Player player, NonNullList<ItemStack> itemList) {
        BundleContents contents = itemStack.getOrDefault(DataComponents.BUNDLE_CONTENTS, BundleContents.EMPTY);
        return new BundleContentsTooltip(contents,
                this.getRelativeWeight(itemStack),
                this.getGridWidth(contents.size()),
                this.getGridHeight(contents.size()));
    }

    /**
     * @see net.minecraft.world.item.BundleItem#isBarVisible(ItemStack)
     */
    @Override
    public Optional<Boolean> isBarVisible(ItemStack itemStack, Player player) {
        return Optional.of(this.getRelativeWeight(itemStack).compareTo(Fraction.ZERO) > 0);
    }

    /**
     * @see net.minecraft.world.item.BundleItem#getBarWidth(ItemStack)
     */
    @Override
    public OptionalInt getBarWidth(ItemStack itemStack, Player player) {
        return OptionalInt.of(Math.min(1 + Mth.mulAndTruncate(this.getRelativeWeight(itemStack), 12), 13));
    }

    /**
     * @see net.minecraft.world.item.BundleItem#getBarColor(ItemStack)
     */
    @Override
    public OptionalInt getBarColor(ItemStack itemStack, Player player) {
        boolean isFull = this.getRelativeWeight(itemStack).compareTo(Fraction.ONE) >= 0;
        return OptionalInt.of(isFull ? BundleItem.FULL_BAR_COLOR : BundleItem.BAR_COLOR);
    }

    @Override
    public ItemStorageType<?> getType() {
        return ModRegistry.BUNDLE_ITEM_STORAGE_TYPE.value();
    }
}
