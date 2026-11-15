#!/usr/bin/env python3
"""Compile-time API adaptations; original sources remain authoritative for 26.1.

26.3 moved UI ownership to Gui/Hud and rendering types to RenderPearl.
This generates Java sources before javac, never rewrites third-party mod JARs.
"""
import pathlib,re,shutil,sys
from port1201 import EXCLUDED_SOURCES, without_happy_ghast, java17, adapt_api

import importlib.util
_spec=importlib.util.spec_from_file_location('solid_backport',pathlib.Path(__file__).with_name('solid-backport.py'))
solid_backport=importlib.util.module_from_spec(_spec);_spec.loader.exec_module(solid_backport)

ROOT=pathlib.Path(__file__).resolve().parents[1]

def prepare(version,loader):
    target=ROOT/'client-mod/build'/f'sources-{loader}-{version}'
    shutil.rmtree(target,ignore_errors=True)
    target.mkdir(parents=True)
    sources=[*(ROOT/'client-mod/src/main/java').rglob('*.java'),*(ROOT/f'client-mod/src/{loader}/java').rglob('*.java')]
    names={p.name for p in sources}
    if version=='1.21.11':
        sources += [p for p in (ROOT/'client-mod/src/26.3/java').glob('*.java') if p.stem in solid_backport.FILES and p.name not in names]
    for source in sources:
        if version=='1.20.1' and source.name in EXCLUDED_SOURCES:continue
        override=ROOT/'client-mod/src'/version/'java'/source.name
        native_override=ROOT/'client-mod/src'/version/loader/'java'/source.name
        if native_override.exists():override=native_override
        if version=='1.21.11' and not override.exists() and source.stem in solid_backport.FILES:override=ROOT/'client-mod/src/26.3/java'/source.name
        text=(override if override.exists() else source).read_text()
        if version in ('1.21.11','26.3'):
            if source.name=='UnboxClient.java':
                text=text.replace('TestStartupDiagnostics.tick(client);','UiWarmup.tick(client);TestStartupDiagnostics.tick(client);')
                text=text.replace('PerformanceMonitor.draw(g);','PerformanceMonitor.draw(g);if(Minecraft.getInstance().screen==null)UiTransition.draw(g);')
            if source.name in ('ClientHud.java','ModOptions.java'):
                text=text.replace('id+".background","Background",true','id+".background","Background",false').replace('id+".background",true','id+".background",false').replace('+".background",true','+".background",false')
            if source.name=='ModOptions.java':
                text=text.replace('"keystrokes.opacity","Key opacity %",40','"keystrokes.opacity","Key opacity %",0').replace('"keystrokes.border","Key border",true','"keystrokes.border","Key border",false')
                text=text.replace('"chat.opacity","Background opacity %",50','"chat.opacity","Background opacity %",0').replace('"scoreboard.opacity","Background opacity %",35','"scoreboard.opacity","Background opacity %",0').replace('"scoreboard.titleOpacity","Title opacity %",50','"scoreboard.titleOpacity","Title opacity %",0')
                text=text.replace('"effectbars.opacity","Background opacity %",50','"effectbars.opacity","Background opacity %",0')
            if source.name=='KeystrokesHud.java':text=text.replace('"keystrokes.opacity",40','"keystrokes.opacity",0').replace('"keystrokes.border",true','"keystrokes.border",false')
            if source.name=='ChatMixin.java':text=text.replace('"chat.opacity",50','"chat.opacity",0')
            if source.name=='ScoreboardAppearance.java':text=text.replace('title ? 50 : 35','0')
            if version=='1.21.11' and loader=='fabric':
                if source.name=='ClientFeaturePolicy.java':
                    text=text.replace('if(!ClientIntegrations.available(module(key)))return false;', 'if(module(key).equals("viewmodel")&&Platform.loaded("handtweaks"))return false;if(!ClientIntegrations.available(module(key)))return false;')
                    text=text.replace('if (allowed(key)) return "";', 'if (allowed(key)) return "";if(module(key).equals("viewmodel")&&Platform.loaded("handtweaks"))return "Disable TweakHands and restart to use Viewmodel";')
                if source.name=='ModOptions.java':
                    text=text.replace('new Mod("performance",', 'new Mod("viewmodel","Viewmodel","Visuals","First-person hand position, rotation and size"),new Mod("daytime","Daytime","Visuals","Local sky time; server time is unchanged"),new Mod("performance",')
                    text=text.replace('        List<Option> o=new ArrayList<>();','''        List<Option> o=new ArrayList<>();
        if(id.equals("daytime"))return List.of(slider("daytime.ticks","Time of day (ticks)",6000,0,23999));
        if(id.equals("viewmodel")){
            o.add(toggle("viewmodel.separateHand","Separate empty hand",false));o.add(toggle("viewmodel.separateOffhand","Separate offhand",false));
            for(String section:List.of("item","empty","off")){String p="viewmodel."+section+".";String label=section.equals("item")?"Main hand":section.equals("empty")?"Empty hand":"Offhand";
                o.add(slider(p+"x",label+" X",0,-100,100));o.add(slider(p+"y",label+" Y",0,-100,100));o.add(slider(p+"z",label+" Z",0,-100,100));o.add(slider(p+"scale",label+" size %",100,25,150));
                o.add(slider(p+"pitch",label+" pitch",0,-180,180));o.add(slider(p+"yaw",label+" yaw",0,-180,180));o.add(slider(p+"roll",label+" roll",0,-180,180));
            }return o;
        }''')
                if source.name=='UnboxClient.java':
                    text=text.replace('case "fps","coordinates","armor","zoom"', 'case "fps","coordinates","armor","zoom","cps","ping","keystrokes","sprint","viewmodel","daytime","chat","scoreboard"')
            if source.name=='ClientIntegrations.java':text=text.replace('"effectbars.opacity",50','"effectbars.opacity",0')
        if version=='1.21.11' and loader=='fabric':
            if source.name=='ModOptions.java':
                text=text.replace('public static Mod mod(String id){','public static Mod mod(String id){if(PvpSettings.supports(id))return PvpModules.entries().stream().filter(m->m.id().equals(id)).findFirst().orElseThrow();')
                text=text.replace('public static List<Option> options(String id,String tab){','public static List<Option> options(String id,String tab){if(PvpSettings.supports(id))return PvpSettings.options(id,tab);')
                text=text.replace('public static List<String> tabs(String id){','public static List<String> tabs(String id){if(PvpSettings.supports(id))return PvpSettings.tabs(id);')
                text=text.replace('public static boolean available(Option o){','public static boolean available(Option o){if(PvpSettings.owns(o.key()))return true;')
                text=text.replace('public static String value(Option o){','public static String value(Option o){if(PvpSettings.owns(o.key()))return PvpSettings.value(o.key(),o.fallback());')
            if source.name=='ClientHud.java':
                text=text.replace('public static String value(String key,String fallback){','public static String value(String key,String fallback){if(PvpSettings.owns(key))return PvpSettings.value(key,fallback);')
            if source.name=='UnboxClient.java':
                text=text.replace('public static void set(String key,Object value){','public static void set(String key,Object value){if(PvpSettings.owns(key)){PvpSettings.write(key,String.valueOf(value));return;}')
                text=text.replace('public static void save(){','public static void save(){PvpSettings.flush();')
                text=text.replace('public static int number(String key,int fallback,int min,int max){','public static int number(String key,int fallback,int min,int max){if(PvpSettings.owns(key))try{return Math.clamp(Integer.parseInt(PvpSettings.value(key,""+fallback)),min,max);}catch(NumberFormatException e){return fallback;}')
                text=text.replace('public static boolean configured(String key){','public static boolean configured(String key){if(PvpSettings.supports(key))return PvpModules.enabled(key);')
                text=text.replace('public static boolean enabled(String key){','public static boolean enabled(String key){if(PvpSettings.supports(key))return PvpModules.enabled(key);')
        if version=='1.20.1':text=adapt_api(source.name,java17(without_happy_ghast(source.name,text)))
        if version=='1.21.11':
            if source.stem in solid_backport.FILES:text=solid_backport.adapt(source.name,text)
            replacements={
                'GuiGraphicsExtractor':'GuiGraphics',
                'GuiGraphics;text(':'GuiGraphics;drawString(',
                'net.minecraft.client.renderer.state.gui.':'net.minecraft.client.gui.render.state.',
                'net.minecraft.client.renderer.state.level.':'net.minecraft.client.renderer.state.',
                'SavedDataStorage':'DimensionDataStorage',
                'net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper':'net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper',
                'KeyMappingHelper.registerKeyMapping':'KeyBindingHelper.registerKeyBinding',
                'extractBackground':'renderBackground',
                'extractPanorama':'renderPanorama',
                'extractBlurredBackground':'renderBlurredBackground',
                'extractWidgetRenderState':'renderWidget',
                'extractContents':'renderContents',
                'extractContent':'renderContent',
                'extractCrosshair':'renderCrosshair',
                'extractRenderStateWithTooltipAndSubtitles':'renderWithTooltipAndSubtitles',
                'extractDeferredSubtitles':'renderDeferredSubtitles',
                '.resizeGui()':'.resizeDisplay()',
                '.addClientSystemMessage(':'.addMessage(',
                '.addGuiElement(':'.submitGuiElement(',
                'PayloadTypeRegistry.serverboundPlay()':'PayloadTypeRegistry.playC2S()',
                'PayloadTypeRegistry.clientboundPlay()':'PayloadTypeRegistry.playS2C()',
                'new OptionsScreen(this,minecraft.options,false)':'new OptionsScreen(this,minecraft.options)',
                '.entityTags()':'.getTags()',
                '.getBlockStateModelSet()':'.getBlockModelShaper()',
            }
            for before,after in replacements.items():text=text.replace(before,after)
            text=text.replace('nl.requios.effortlessbuilding.',loader+'.nl.requios.effortlessbuilding.')
            text=text.replace('"chiseledbookshelfvisualizer"','"chiseled-bookshelf-visualizer"')
            text=text.replace('fuzs.iteminteractions.common.impl.','fuzs.iteminteractions.impl.')
            text=text.replace('fuzs.puzzleslib.common.api.config.v3.','fuzs.puzzleslib.api.config.v3.')
            if source.name=='ClientIntegrations.java':
                text=text.replace('Object c=autoConfig("net.anvian.chiseledbookshelfvisualizer.client.config.ClientConfig");set(c,"scale",number("bookshelf.scale",100,50,200)/100.0);set(c,"useRoman",flag("bookshelf.roman",true));','Object c=singleton("net.anvian.chiseledbookshelfvisualizer.ChiseledBookshelfVisualizerClient","CONFIG");c.getClass().getMethod("scale",double.class).invoke(c,number("bookshelf.scale",100,50,200)/100.0);c.getClass().getMethod("useRoman",boolean.class).invoke(c,flag("bookshelf.roman",true));')
                text=text.replace('choice(c,"itemStorageTooltip",on?ClientHud.value("shulker.preview","SHIFT"):"NEVER");','choice(c,"visualItemContents",ClientHud.value("shulker.preview","SHIFT").equals("ALWAYS")?"ALWAYS":"SHIFT");')
                text=text.replace('set(c,"itemStorageContentsCount",on&&flag("shulker.count",true));','// Counts and disabled previews are gated by ShulkerTooltipGateMixin on this API.')
                text=text.replace('"itemStorageIndicator"','"containerItemIndicator"').replace('set(c,"nestedItemTooltips",on&&flag("shulker.nested",true));','choice(c,"selectedItemTooltips","ALWAYS");').replace('"reverseSingleItemScrolling"','"invertPrecisionModeScrolling"')
            if source.name=='SpatialSettings.java':
                text=text.replace('new Setting("spatial.blur","blurWorldBackground","false",0,0,false),','')
            if source.name=='ModToggleChecks.java':
                text=text.replace('inspector.getConstructor(java.util.function.Consumer.class).newInstance((java.util.function.Consumer<Object>)packet->{throw new IllegalStateException("Disabled bookshelf sent a request");})','inspector.getConstructor().newInstance()')
            if source.name=='FeatureSmokeTest.java':
                text=text.replace('checks.addAll(ModToggleChecks.run(mc));','checks.addAll(ModToggleChecks.run(mc));checks.addAll(WickIntegrationChecks.run(mc));')
                text=text.replace('private static boolean finished;', 'private static boolean finished,spatialBlurChecked;')
                text=text.replace('case 9->{check(', 'case 9->{if(!spatialBlurChecked){Screenshot.grab(mc.gameDirectory,"spatial-first-person-sharp.png",mc.getMainRenderTarget(),1,c->{});UnboxClient.set("spatial.blur",true);spatialBlurChecked=true;stage--;wait=30;break;}check(')
                text=text.replace('"spatial-first-person.png"', '"spatial-first-person-blur.png"')
                text=text.replace('UnboxClient.set("spatial",false);UnboxClient.set("shulker",false);', 'UnboxClient.set("spatial",false);UnboxClient.set("spatial.blur",false);UnboxClient.set("shulker",false);')
                text=text.replace('calledGhast.setPos(p.getX()+6,160,p.getZ());callStartDistance=calledGhast.distanceToSqr(p);\n    });wait=10;}', 'calledGhast.setPos(p.getX()+6,160,p.getZ());calledGhast.setDeltaMovement(Vec3.ZERO);calledGhast.getMoveControl().setWantedPosition(calledGhast.getX(),calledGhast.getY(),calledGhast.getZ(),0);callStartDistance=calledGhast.distanceToSqr(p);\n    });wait=1;}')
                text=text.replace('calledGhast.setPos(player(mc).getX()+3,player(mc).getY(),player(mc).getZ());HappyGhastRecall.receive', 'calledGhast.setPos(player(mc).getX()+3,player(mc).getY(),player(mc).getZ());calledGhast.setDeltaMovement(Vec3.ZERO);calledGhast.getMoveControl().setWantedPosition(calledGhast.getX(),calledGhast.getY(),calledGhast.getZ(),0);HappyGhastRecall.receive')
                text=text.replace('private static double callStartDistance;', 'private static double callStartDistance;private static long callAgainTick;')
                text=text.replace('UnboxClient.set("ghast.wait",4);', 'callAgainTick=player(mc).level().getGameTime();UnboxClient.set("ghast.wait",4);')
                text=text.replace('"Called ghast flies toward the player using native movement"', '"Called ghast flies toward the player using native movement (start="+callStartDistance+", now="+calledGhast.distanceToSqr(player(mc))+", called="+HappyGhastRecall.called(calledGhast)+", player="+player(mc).position()+", ghast="+calledGhast.position()+")"')
                text=text.replace('"Arrived ghast resumes normal behavior after four seconds without interaction"', '"Arrived ghast resumes normal behavior after four seconds without interaction (serverTicks="+(player(mc).level().getGameTime()-callAgainTick)+", distanceSquared="+calledGhast.distanceToSqr(player(mc))+")"')
            if source.name in ('ShulkerHostGateMixin.java','FeatureSmokeTest.java'):
                text=text.replace('fuzs.iteminteractions.common.api.v2.world.item.storage.ContainerStorage','fuzs.iteminteractions.api.v1.provider.impl.ContainerProvider').replace('"allowModification"','"allowsPlayerInteractions"').replace('type.getConstructor().newInstance()','type.getConstructor(int.class,int.class).newInstance(9,3)')
            if source.name.endswith(('SmokeTest.java','Checks.java')):
                fields={'leftPos':'field_2776','topPos':'field_2800','hoveredSlot':'field_2787','clickCount':'field_1661','accumulatedDX':'field_1789','accumulatedDY':'field_1787'}
                for name,mapped in fields.items():
                    text=re.sub(r'([\w.]+)\.getDeclaredField\("'+name+r'"\)',r'PortReflection.field(\1,"'+name+'","'+mapped+'")',text)
                text=text.replace('info.getClass().getDeclaredMethod("setGameMode",','PortReflection.method(info.getClass(),"setGameMode","method_2963",')
                for owner,name,mapped in [('net.minecraft.client.MouseHandler','turnPlayer','method_1606'),('net.minecraft.client.MouseHandler','onScroll','method_1598'),('net.minecraft.client.gui.Font','getGlyphSource','method_72732')]:
                    text=text.replace(owner+'.class.getDeclaredMethod("'+name+'",','PortReflection.method('+owner+'.class,"'+name+'","'+mapped+'",')
                if source.name=='UiSmokeTest.java':
                    text=text.replace('check(UnboxClient.zoomKey.matches(new KeyEvent(GLFW.GLFW_KEY_W,0,0)),"Conflicting movement key reassigned");check(m.options.keyUp.isUnbound(),"Previous binding cleared");','check(UnboxClient.zoomKey.saveString().equals(originalKey),"Conflicting movement key preserves the module binding");check(m.options.keyUp.matches(new KeyEvent(GLFW.GLFW_KEY_W,0,0)),"Movement binding stays assigned after conflict");')
                    text=text.replace('var align=net.minecraft.client.Camera.class.getDeclaredMethod("alignWithEntity",float.class);align.setAccessible(true);','').replace('align.invoke(renderedCamera,1f);','renderedCamera.setup(m.level,m.player,m.options.getCameraType().isFirstPerson()==false,m.options.getCameraType().isMirrored(),1f);')
            # GUI extraction was named render before 26.1; entity extraction is unchanged.
            text=re.sub(r'extractRenderState(?=\(GuiGraphics\b)', 'render', text)
            text=re.sub(r'\b(super|widget|w|child|renderable)\.extractRenderState\(', r'\1.render(', text)
            text=re.sub(r'\bg\.(text|centeredText|outline|item|entity)\(',lambda m:'g.'+{'text':'drawString','centeredText':'drawCenteredString','outline':'renderOutline','item':'renderItem','entity':'submitEntityRenderState'}[m[1]]+'(',text)
            if source.name=='GraveStorage.java':
                text=text.replace('Identifier.fromNamespaceAndPath("unbox", "stone_graves")','"unbox_stone_graves"')
            if source.name in ('FrameBenchmarkMixin.java','PerformanceGameMixin.java'):
                text=text.replace('method="renderFrame"','method="runTick"')
            if source.name=='CameraMixin.java':
                text=text.replace('@Mixin(Camera.class)','@Mixin(net.minecraft.client.renderer.GameRenderer.class)').replace('method="calculateFov"','method="getFov"').replace('unboxZoom(float delta,','unboxZoom(Camera camera,float delta,boolean useSetting,')
            if source.name=='NeoFreelookCameraMixin.java':
                text=text.replace('method="alignWithEntity"','method="setup"')
            if source.name=='HomeSmokeTest.java':
                text=text.replace('AbstractWidget.class.getDeclaredField("tooltip")','java.util.Arrays.stream(AbstractWidget.class.getDeclaredFields()).filter(f->f.getType()==net.minecraft.client.gui.components.WidgetTooltipHolder.class).findFirst().orElseThrow()')
            if source.name=='UnboxClient.java':
                text=text.replace('TestStartupDiagnostics.tick(client);','PortWorldBootstrap.tick(client);PortUiChecks.tick(client);TestStartupDiagnostics.tick(client);')
            if source.name=='ChatMixin.java':
                text=text.replace('extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V','render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V').replace('lambda$extractRenderState$1','method_75802' if loader=='fabric' else 'lambda$render$1')
        if version=='26.3':
            if source.name=='UnboxClient.java':text=text.replace('TestStartupDiagnostics.tick(client);','SolidUiChecks.tick(client);TestStartupDiagnostics.tick(client);')
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
            if source.name in ('TitleScreenMixin.java','UiTransitionMixin.java'):
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
    for extra in [*(ROOT/'client-mod/src'/version/'java').glob('*.java'),*(ROOT/'client-mod/src'/version/loader/'java').glob('*.java')]:
        if not (target/extra.name).exists():(target/extra.name).write_text(extra.read_text())
    return target

if __name__=='__main__':print(prepare(sys.argv[1],sys.argv[2]))
