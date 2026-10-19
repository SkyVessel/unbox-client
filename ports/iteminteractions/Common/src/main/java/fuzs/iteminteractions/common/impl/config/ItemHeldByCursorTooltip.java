package fuzs.iteminteractions.common.impl.config;

import com.mojang.datafixers.util.Either;
import fuzs.iteminteractions.common.impl.ItemInteractions;
import fuzs.iteminteractions.common.impl.client.core.BackedKeyType;
import fuzs.iteminteractions.common.impl.client.core.KeyType;
import fuzs.iteminteractions.common.impl.client.core.SimpleKeyType;
import fuzs.puzzleslib.common.api.client.key.v1.KeyMappingHelper;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import org.apache.commons.lang3.mutable.MutableBoolean;

/**
 * An almost perfect mirror apart from the custom {@link #KEY_MAPPING}.
 *
 * @see ItemStorageTooltip
 */
public enum ItemHeldByCursorTooltip implements BackedKeyType {
    ALWAYS {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.ALWAYS);
        }
    },
    NEVER {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.NEVER);
        }
    },
    SHIFT {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.SHIFT);
        }
    },
    CONTROL {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.CONTROL);
        }
    },
    CONTROL_OR_COMMAND {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.CONTROL_OR_COMMAND);
        }
    },
    ALT {
        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.left(SimpleKeyType.ALT);
        }
    },
    KEY {
        private final MutableBoolean useState = new MutableBoolean();

        @Override
        public Either<KeyType, KeyMapping> getBackingType() {
            return Either.right(KEY_MAPPING);
        }

        @Override
        public MutableBoolean getUseState() {
            return this.useState;
        }
    };

    private static final ItemHeldByCursorTooltip[] VALUES = values();
    public static final KeyMapping KEY_MAPPING = KeyMappingHelper.registerUnboundKeyMapping(ItemInteractions.id(
            "toggle_item_held_by_cursor_tooltip"));

    public static EventResult onBeforeKeyPressed(AbstractContainerScreen<?> screen, KeyEvent keyEvent) {
        return BackedKeyType.onKeyPressed(VALUES, keyEvent);
    }
}
