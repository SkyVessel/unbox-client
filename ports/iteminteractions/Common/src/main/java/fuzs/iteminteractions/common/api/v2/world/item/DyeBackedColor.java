package fuzs.iteminteractions.common.api.v2.world.item;

import com.google.common.collect.BiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Lifecycle;
import net.minecraft.util.ARGB;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.material.MapColor;
import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Adapted from {@link net.minecraft.network.chat.TextColor} to use {@link DyeColor} instead of
 * {@link net.minecraft.ChatFormatting}.
 */
public final class DyeBackedColor {
    private static final String CUSTOM_COLOR_PREFIX = "#";
    /**
     * Codec for a {@link DyeBackedColor}.
     */
    public static final Codec<DyeBackedColor> CODEC = Codec.STRING.comapFlatMap(DyeBackedColor::parseColor,
            DyeBackedColor::serialize);
    private static final BiMap<DyeColor, DyeBackedColor> LEGACY_FORMAT_TO_COLOR = Stream.of(DyeColor.values())
            .collect(ImmutableBiMap.toImmutableBiMap(Function.identity(),
                    (DyeColor color) -> new DyeBackedColor(color.getTextureDiffuseColor(), color.getName())));
    private static final Map<String, DyeBackedColor> NAMED_COLORS = LEGACY_FORMAT_TO_COLOR.values()
            .stream()
            .collect(ImmutableMap.toImmutableMap((DyeBackedColor color) -> color.name, Function.identity()));

    private final int value;
    @Nullable
    private final String name;

    private DyeBackedColor(int value, String name) {
        this.value = ARGB.transparent(value);
        this.name = name;
    }

    private DyeBackedColor(int value) {
        this.value = ARGB.transparent(value);
        this.name = null;
    }

    /**
     * @return the packed color value
     */
    public int getValue() {
        return this.value;
    }

    /**
     * @return the serialized color
     */
    public String serialize() {
        return this.name != null ? this.name : this.formatValue();
    }

    /**
     * @return the color formatted as a hex string
     */
    public String formatValue() {
        return String.format(Locale.ROOT, "%s%06X", CUSTOM_COLOR_PREFIX, this.value);
    }

    /**
     * @return the dye color backing this color, or {@code null} if there is none
     */
    @Nullable
    public DyeColor unwrap() {
        return LEGACY_FORMAT_TO_COLOR.inverse().get(this);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object != null && this.getClass() == object.getClass()) {
            DyeBackedColor textColor = (DyeBackedColor) object;
            return this.value == textColor.value;
        } else {
            return false;
        }
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.value, this.name);
    }

    @Override
    public String toString() {
        return this.serialize();
    }

    /**
     * @param dyeColor the dye color
     * @return the color for the dye color
     */
    public static DyeBackedColor fromDyeColor(DyeColor dyeColor) {
        return LEGACY_FORMAT_TO_COLOR.get(dyeColor);
    }

    /**
     * @param mapColor the map color
     * @return the color for the map color
     */
    public static DyeBackedColor fromMapColor(MapColor mapColor) {
        return fromRgb(mapColor.col);
    }

    /**
     * @param color the packed rgb color
     * @return the color for the packed rgb value
     */
    public static DyeBackedColor fromRgb(int color) {
        return new DyeBackedColor(color);
    }

    /**
     * @param color the serialized color
     * @return the parsed color
     */
    public static DataResult<DyeBackedColor> parseColor(String color) {
        if (color.startsWith(CUSTOM_COLOR_PREFIX)) {
            try {
                int i = Integer.parseInt(color.substring(1), 16);
                return i >= 0 && i <= 16777215 ? DataResult.success(fromRgb(i), Lifecycle.stable()) :
                        DataResult.error(() -> "Color value out of range: " + color);
            } catch (NumberFormatException var2) {
                return DataResult.error(() -> "Invalid color value: " + color);
            }
        } else {
            DyeBackedColor textColor = NAMED_COLORS.get(color);
            return textColor == null ? DataResult.error(() -> "Invalid color name: " + color) :
                    DataResult.success(textColor, Lifecycle.stable());
        }
    }
}
