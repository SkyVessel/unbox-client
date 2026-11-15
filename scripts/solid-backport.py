"""Native 1.21.11 API bridge for the shared modern vector UI sources."""
from pathlib import Path
FILES=set('SolidLayout SolidSurface SolidGeometry SolidTheme VectorModsScreen SolidScreen VectorScene PanelStyle UnboxScreen UnboxHomeScreen FriendsScreen SkinLibraryScreen SkinSelectionPreview RedeemScreen PlayerPreview OpaqueSolidMenuMixin UiTransition UiTransitionMixin UiWarmup PvpModules PvpSettings'.split())
def adapt(name,text):
    for a,b in {
        'com.mojang.renderpearl.api.GpuFormat.RGBA8_UNORM':'com.mojang.blaze3d.textures.TextureFormat.RGBA8',
        'com.mojang.renderpearl.api.textures.':'com.mojang.blaze3d.textures.',
        '.gui.overlay()':'.getOverlay()', '.gui.screen()':'.screen', '.gameRenderer.mainRenderTarget()':'.getMainRenderTarget()', '@Mixin(net.minecraft.client.gui.Gui.class)':'@Mixin(Minecraft.class)', '.gui.setScreen(':'.setScreen(',
        '.gui.hud.isHidden()':'.options.hideGui',
        'InputConstants.KEY_RSHIFT':'InputConstants.KEY_RSHIFT',
    }.items():text=text.replace(a,b)
    return text
