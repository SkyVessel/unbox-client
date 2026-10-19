package fuzs.iteminteractions.common.impl.client.core;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import fuzs.puzzleslib.common.api.util.v1.CommonHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.InputQuirks;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public enum SimpleKeyType implements KeyType {
    ALWAYS {
        @Override
        public boolean isUsed() {
            return true;
        }

        @Override
        public Component getNameComponent() {
            throw new UnsupportedOperationException();
        }
    },
    NEVER {
        @Override
        public boolean isUsed() {
            return false;
        }

        @Override
        public @Nullable Component getComponent(String translationId) {
            return null;
        }

        @Override
        public Component getNameComponent() {
            throw new UnsupportedOperationException();
        }
    },
    SHIFT {
        @Override
        public boolean isUsed() {
            return CommonHelper.hasShiftDown();
        }

        @Override
        public Component getNameComponent() {
            return SHIFT_COMPONENT;
        }
    },
    CONTROL {
        @Override
        public boolean isUsed() {
            return CommonHelper.hasControlDown();
        }

        @Override
        public Component getNameComponent() {
            return CONTROL_COMPONENT;
        }
    },
    /**
     * We need this extra type as on macOS while the control key is held, all mouse clicks are handled as right-clicks.
     */
    CONTROL_OR_COMMAND {
        /**
         * @see Minecraft#hasControlDown()
         */
        @Override
        public boolean isUsed() {
            if (InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY) {
                Window window = Minecraft.getInstance().getWindow();
                return InputConstants.isKeyDown(window, InputConstants.KEY_LSUPER) || InputConstants.isKeyDown(window,
                        InputConstants.KEY_RSUPER);
            } else {
                return CommonHelper.hasControlDown();
            }
        }

        @Override
        public Component getNameComponent() {
            return InputQuirks.REPLACE_CTRL_KEY_WITH_CMD_KEY ? COMMAND_COMPONENT : CONTROL_COMPONENT;
        }
    },
    ALT {
        @Override
        public boolean isUsed() {
            return CommonHelper.hasAltDown();
        }

        @Override
        public Component getNameComponent() {
            return ALT_COMPONENT;
        }
    }
}
