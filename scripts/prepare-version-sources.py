#!/usr/bin/env python3
"""Compile-time API adaptations; original sources remain authoritative for 26.1.

26.3 moved UI ownership to Gui/Hud and rendering types to RenderPearl.
This generates Java sources before javac, never rewrites third-party mod JARs.
"""
import pathlib,re,shutil,sys

ROOT=pathlib.Path(__file__).resolve().parents[1]

def prepare(version,loader):
    target=ROOT/'client-mod/build'/f'sources-{loader}-{version}'
    shutil.rmtree(target,ignore_errors=True)
    target.mkdir(parents=True)
    for source in [*(ROOT/'client-mod/src/main/java').rglob('*.java'),*(ROOT/f'client-mod/src/{loader}/java').rglob('*.java')]:
        text=source.read_text()
        if version=='26.3':
            replacements={
                'com.mojang.blaze3d.pipeline.RenderPipeline':'com.mojang.renderpearl.api.pipeline.RenderPipeline',
                'com.mojang.blaze3d.textures.':'com.mojang.renderpearl.api.textures.',
                '.options.hideGui':'.gui.hud.isHidden()',
                '.getOverlay()':'.gui.overlay()',
                '.setScreen(':'.gui.setScreen(',
                '.getMainRenderTarget()':'.gameRenderer.mainRenderTarget()',
                '.getMainCamera()':'.mainCamera()',
                '.gui.setOverlayMessage(':'.gui.hud.setOverlayMessage(',
                '.gui.getChat()':'.gui.hud.getChat()',
                '.gui.extractDeferredSubtitles()':'.gui.hud.extractDeferredSubtitles()',
                'InputConstants.Type.KEYSYM':'InputConstants.Type.KEYBOARD',
                'EntityType.BLOCK_DISPLAY':'net.minecraft.world.entity.EntityTypes.BLOCK_DISPLAY',
                'EntityType.TEXT_DISPLAY':'net.minecraft.world.entity.EntityTypes.TEXT_DISPLAY',
                'EntityType.PLAYER':'net.minecraft.world.entity.EntityTypes.PLAYER',
                'net.minecraft.world.entity.net.minecraft.world.entity.':'net.minecraft.world.entity.',
                '.setInvulnerable(':'.setPermanentlyInvulnerable(',
                'Minecraft.renderNames()':'!Minecraft.getInstance().gui.hud.isHidden()',
                'new OptionsScreen(this,minecraft.options,false)':'new OptionsScreen(this,minecraft.options)',
                'mc.player.chatAbilities());':'mc.player.chatAbilities(),new net.minecraft.client.player.ItemActivation());',
                'Blocks.RED_WOOL':'Blocks.WOOL.red()',
            }
            for before,after in replacements.items():text=text.replace(before,after)
            text=text.replace('import org.lwjgl.glfw.GLFW;','')
            text=re.sub(r'(?:org.lwjgl.glfw.)?GLFW.GLFW_KEY_(\w+)',lambda m:'com.mojang.blaze3d.platform.InputConstants.KEY_'+{'RIGHT_SHIFT':'RSHIFT','ENTER':'RETURN'}.get(m[1],m[1]),text)
            text=re.sub(r'(\.button\(\)\s*[!=]=\s*)([01])\b',lambda m:m[1]+('1' if m[2]=='0' else '3'),text)
            text=re.sub(r'(new (?:net.minecraft.client.input.)?MouseButtonInfo\()([01]),',lambda m:m[1]+('1' if m[2]=='0' else '3')+',',text)
            if source.name=='SocialNotifications.java':text=text.replace('button==0','button==1')
            if source.name=='MouseMixin.java':text=text.replace('UnboxClient.mouse(button.button(),action)','UnboxClient.mouse(button.button()==1?0:button.button()==3?1:-1,action)')
            text=re.sub(r'\b(m|mc|minecraft|client|Minecraft.getInstance\(\))\.screen\b',r'\1.gui.screen()',text)
            text=re.sub(r'\.publishServer\(\w+\.getDefaultGameType\(\),',r'.publishServer(net.minecraft.server.MinecraftServer.MultiplayerScope.LAN,',text)
            text=re.sub(r'mc\.gui\.hud\.isHidden\(\) = (true|false);',r'if(mc.gui.hud.isHidden()!=\1)mc.gui.hud.toggle();',text)
            if source.name in ('GuiMixin.java','ScoreboardAppearanceMixin.java'):
                text=text.replace('@Mixin(Gui.class)','@Mixin(net.minecraft.client.gui.Hud.class)')
            if source.name=='TitleScreenMixin.java':
                text=text.replace('@Mixin(Minecraft.class)','@Mixin(net.minecraft.client.gui.Gui.class)').replace('((Minecraft)(Object)this).isDemo()','Minecraft.getInstance().isDemo()')
            if source.name=='GraveBreakingRenderMixin.java':
                text=text.replace('collector.submitBreakingBlockModel(pose,model,stone.getSeed(pos),breaking.progress());',
                    'var parts=new java.util.ArrayList<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart>();\n            model.collectParts(net.minecraft.util.RandomSource.create(stone.getSeed(pos)),parts);\n            collector.submitBreakingBlockModel(pose,java.util.List.copyOf(parts),breaking.progress(),model.hasMaterialFlag(1));')
            if source.name=='NameplateSmokeTest.java':
                text=text.replace('remote.setPos(', 'remote.setId(DetachedEntityIds.next());\n        remote.setPos(')
            if source.name=='NativeModIntegrationChecks.java':
                text=text.replace('entity.tickCount=20;', 'entity.setId(DetachedEntityIds.next());entity.tickCount=20;')
                text=text.replace('args[6] instanceof List<?> quads&&!quads.isEmpty()', 'args[6] instanceof net.minecraft.client.resources.model.geometry.ItemQuads quads&&!quads.isEmpty()')
            if source.name=='VeinMinerGateMixin.java':
                text=text.replace('method="destroyBlock"','method="scheduleBreak$lambda$0$0"')
                text=text.replace('private void unbox$protectStoredGraves(BlockState state,ItemStack tool,Level level,BlockPos pos,Player player,BlockPos origin,CallbackInfo ci)',
                                  'private static void unbox$protectStoredGraves(net.minecraft.server.level.ServerPlayer player,BlockPos pos,CallbackInfoReturnable<Boolean> ci)')
                text=text.replace('GraveKeeper.isGrave(level,pos))ci.cancel()','GraveKeeper.isGrave(player.level(),pos))ci.setReturnValue(false)')
        (target/source.name).write_text(text)
    return target

if __name__=='__main__':print(prepare(sys.argv[1],sys.argv[2]))
