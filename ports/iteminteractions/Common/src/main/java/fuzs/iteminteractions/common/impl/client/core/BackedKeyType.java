package fuzs.iteminteractions.common.impl.client.core;

import com.mojang.datafixers.util.Either;
import fuzs.puzzleslib.common.api.client.key.v1.KeyMappingHelper;
import fuzs.puzzleslib.common.api.event.v1.core.EventResult;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

public interface BackedKeyType extends KeyType {

    Either<KeyType, KeyMapping> getBackingType();

    @Override
    default boolean isUsed() {
        return this.getBackingType().map(KeyType::isUsed, (KeyMapping _) -> this.getUseState().booleanValue());
    }

    @Override
    default @Nullable Component getComponent(String translationId) {
        return this.getBackingType().map((KeyType keyType) -> {
            return keyType.getComponent(translationId);
        }, (KeyMapping _) -> {
            return KeyType.super.getComponent(translationId);
        });
    }

    @Override
    default Component getUsageComponent() {
        return this.getBackingType().map(KeyType::getUsageComponent, (KeyMapping _) -> TOGGLE_COMPONENT);
    }

    @Override
    default Component getNameComponent() {
        return this.getBackingType().map(KeyType::getNameComponent, KeyMapping::getTranslatedKeyMessage);
    }

    default MutableBoolean getUseState() {
        throw new UnsupportedOperationException();
    }

    private boolean keyPressed(KeyEvent keyEvent) {
        Optional<KeyMapping> optional = this.getBackingType()
                .right()
                .filter((KeyMapping keyMapping) -> KeyMappingHelper.isKeyActiveAndMatches(keyMapping, keyEvent));
        if (optional.isPresent()) {
            MutableBoolean useState = this.getUseState();
            useState.setValue(!useState.booleanValue());
            return true;
        } else {
            return false;
        }
    }

    static EventResult onKeyPressed(BackedKeyType[] activationTypeProviders, KeyEvent keyEvent) {
        for (BackedKeyType activationTypeProvider : activationTypeProviders) {
            if (activationTypeProvider.keyPressed(keyEvent)) {
                return EventResult.INTERRUPT;
            }
        }

        return EventResult.PASS;
    }
}
