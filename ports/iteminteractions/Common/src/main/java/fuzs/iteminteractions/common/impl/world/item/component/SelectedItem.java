package fuzs.iteminteractions.common.impl.world.item.component;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.component.BundleContents;

import java.util.Objects;

public final class SelectedItem {
    public static final int DEFAULT_SELECTED_ITEM = BundleContents.NO_SELECTED_ITEM_INDEX;
    public static final SelectedItem DEFAULT = new SelectedItem(DEFAULT_SELECTED_ITEM);
    public static final StreamCodec<ByteBuf, SelectedItem> STREAM_CODEC = StreamCodec.composite(ByteBufCodecs.VAR_INT,
            SelectedItem::selectedItem,
            SelectedItem::new);

    final int selectedItemIndex;

    SelectedItem(int selectedItemIndex) {
        this.selectedItemIndex = selectedItemIndex;
    }

    public static SelectedItem of(int selectedItemIndex) {
        if (selectedItemIndex == DEFAULT_SELECTED_ITEM) {
            return DEFAULT;
        } else if (selectedItemIndex >= 0) {
            return new SelectedItem(selectedItemIndex);
        } else {
            throw new IllegalArgumentException("Invalid selectedItemIndex: " + selectedItemIndex);
        }
    }

    public int selectedItem() {
        return this.selectedItemIndex;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj == null || this.getClass() != obj.getClass()) {
            return false;
        } else {
            return this.selectedItemIndex == ((SelectedItem) obj).selectedItemIndex;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.selectedItemIndex);
    }

    @Override
    public String toString() {
        return "SelectedItem[" + this.selectedItemIndex + "]";
    }
}
