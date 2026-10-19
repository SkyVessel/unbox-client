package fuzs.iteminteractions.common.impl.config;

import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public enum ContentsPreview {
    NAME_ONLY {
        @Override
        public List<ItemStack> buildPreviewItems(List<ItemStack> items) {
            return List.of();
        }
    },
    INDIVIDUAL_ITEMS {
        @Override
        public List<ItemStack> buildPreviewItems(List<ItemStack> items) {
            List<ItemStack> nonEmptyItems = new ArrayList<>();
            for (ItemStack itemStack : items) {
                if (!itemStack.isEmpty()) {
                    nonEmptyItems.add(itemStack.copy());
                }
            }

            return nonEmptyItems;
        }
    },
    COMBINED_ITEMS {
        @Override
        public List<ItemStack> buildPreviewItems(List<ItemStack> items) {
            Object2IntMap<Item> itemCounts = new Object2IntLinkedOpenHashMap<>();
            for (ItemStack itemStack : items) {
                if (!itemStack.isEmpty()) {
                    itemCounts.merge(itemStack.getItem(), itemStack.getCount(), Integer::sum);
                }
            }

            List<ItemStack> nonEmptyItems = new ArrayList<>();
            List<Object2IntMap.Entry<Item>> entries = new ArrayList<>(itemCounts.object2IntEntrySet());
            entries.sort(Comparator.<Object2IntMap.Entry<Item>>comparingInt(Object2IntMap.Entry::getIntValue)
                    .reversed());
            for (Object2IntMap.Entry<Item> entry : entries) {
                Item item = entry.getKey();
                int itemCount = entry.getIntValue();
                ItemStack itemStack = new ItemStack(item, itemCount);
                nonEmptyItems.add(itemStack);
            }

            return nonEmptyItems;
        }
    };

    public abstract List<ItemStack> buildPreviewItems(List<ItemStack> items);
}
