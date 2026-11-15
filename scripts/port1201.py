"""Version-scoped source changes for the 1.20.1 port.

Happy Ghast removal is the user's explicit exception to feature parity. Other
modules and their regression checks stay in the generated sources.
"""
import re

EXCLUDED_SOURCES = {'HappyGhastRecall.java', 'HappyGhastRecallMixin.java'}


def call_args(text, prefix, replace):
    """Rewrite a Java call while respecting nested expressions and string literals."""
    cursor=0
    while True:
        start=text.find(prefix+'(',cursor)
        if start<0:return text
        opening=start+len(prefix);depth=1;quote=None;escape=False;end=opening+1;arg_start=end;args=[]
        while depth:
            c=text[end]
            if quote:
                if escape:escape=False
                elif c=='\\':escape=True
                elif c==quote:quote=None
            elif c in ('"',"'"):quote=c
            elif c in '([{':depth+=1
            elif c in ')]}':depth-=1
            elif c==',' and depth==1:args.append(text[arg_start:end].strip());arg_start=end+1
            end+=1
        args.append(text[arg_start:end-1].strip())
        replacement=replace(args)
        text=text[:start]+replacement+text[end:];cursor=start+len(replacement)


def adapt_api(name,text):
    replacements={
        'GuiGraphicsExtractor':'GuiGraphics',
        'method="renderFrame"':'method="runTick"',
        'method="extractRenderStateWithTooltipAndSubtitles"':'method="renderWithTooltip"',

        'EditBox':'LegacyEditBox',
        'net.minecraft.client.gui.components.LegacyEditBox':'dev.unbox.client.LegacyEditBox',
        '.identifier()':'.location()',
        '.getProfile().id()':'.getProfile().getId()',
        '.unboxProfile().id()':'.unboxProfile().getId()',
        '.getDebugOverlay().showDebugScreen()':'.options.renderDebug',
        '.getMinY()':'.getMinBuildHeight()', '.getMaxY()':'.getMaxBuildHeight()',
        'Blocks.SHORT_GRASS':'Blocks.GRASS',
        'ItemStack.isSameItemSameComponents':'ItemStack.isSameItemSameTags',
        'player.preventsBlockDrops()':'player.isCreative()',
        'player.getMainHandItem().canDestroyBlock(':'player.getMainHandItem().getItem().canAttackBlock(',
        'new VideoSettingsScreen(this,minecraft,minecraft.options)':'new VideoSettingsScreen(this,minecraft.options)',
        'w.getBottom()':'(w.getY()+w.getHeight())',
        '.matches(e)':'.matches(e.key(),e.scancode())',
        'minecraft.gui.extractDeferredSubtitles();':'',
        'ServerData.Type.OTHER':'false',
        'address,false),false,null)':'address,false),false)',
        'm.getFramerateLimitTracker().onInputReceived();':'',
        'mc.getMainRenderTarget(),1,c->{}':'mc.getMainRenderTarget(),c->{}',
        'Math.min(1f,2f/Minecraft.getInstance().getWindow().getGuiScale())':'(float)Math.min(1,2/Minecraft.getInstance().getWindow().getGuiScale())',

        '"freelook.key.activate"':'"Activate Freelook"',
        'net.minecraft.client.ScrollWheelHandler':'dev.unbox.client.LegacyScrollWheel',
        'ScrollWheelHandler':'LegacyScrollWheel',
        'steps.y*':'steps.y()*',
        'net.minecraft.world.level.chunk.status.ChunkStatus':'net.minecraft.world.level.chunk.ChunkStatus',
        'net.minecraft.world.level.gamerules.GameRules':'net.minecraft.world.level.GameRules',
        'import net.minecraft.client.renderer.RenderPipelines;':'',
        'import net.minecraft.server.permissions.Permissions;':'',
        'net.minecraft.server.players.NameAndId':'com.mojang.authlib.GameProfile',
        'NameAndId':'com.mojang.authlib.GameProfile',
        'owner.id()':'owner.getId()', 'profile.id()':'profile.getId()',
        '.getGameProfile().name()':'.getGameProfile().getName()',
        '.getGameProfile().id()':'.getGameProfile().getId()',
        '.dimension().identifier()':'.dimension().location()',
        '.getDeltaTracker().getGameTimeDeltaPartialTick(false)':'.getFrameTime()',
        'net.minecraft.client.gui.screens.options.VideoSettingsScreen':'net.minecraft.client.gui.screens.VideoSettingsScreen',
        'p.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER)':'p.hasPermissions(2)',
        'player.level().getGameRules().get(GameRules.KEEP_INVENTORY)':'player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)',
        'ServerLevel level = player.level()':'ServerLevel level = player.serverLevel()',
        'import net.minecraft.client.DeltaTracker;':'',
        'GuiGraphics g,DeltaTracker t':'GuiGraphics g',
        'method="extractCrosshair"':'method="renderCrosshair"',
        'unboxDetails().reason()':'unboxReason()',

        'net.minecraft.client.input.':'dev.unbox.client.input.',
        'Identifier':'ResourceLocation',
        'net.minecraft.client.gui.screens.options.OptionsScreen':'net.minecraft.client.gui.screens.OptionsScreen',
        'SavedDataStorage':'DimensionDataStorage',
        'net.minecraft.network.RegistryFriendlyByteBuf':'net.minecraft.network.FriendlyByteBuf',
        'RegistryFriendlyByteBuf':'FriendlyByteBuf',
        'net.minecraft.network.codec.StreamCodec':'dev.unbox.client.LegacyCodec',
        'StreamCodec':'LegacyCodec',
        'net.minecraft.network.protocol.common.custom.CustomPacketPayload':'dev.unbox.client.LegacyPayload',
        'CustomPacketPayload':'LegacyPayload',
        'KeyMapping.Category.MISC':'"key.categories.misc"',
        'getCurrentVersion().id()':'getCurrentVersion().getName()',
        '.getWindow().handle()':'.getWindow().getWindow()',
        '.getInventory().getSelectedSlot()':'.getInventory().selected',
        '.resizeGui()':'.resizeDisplay()',
        '.addClientSystemMessage(':'.addMessage(',
        '.entityTags()':'.getTags()',
        '.addFormatter(':'.setFormatter(',
        'new OptionsScreen(this,minecraft.options,false)':'new OptionsScreen(this,minecraft.options)',
        'extends Screen':'extends dev.unbox.client.LegacyScreen',
        'extends AbstractWidget':'extends dev.unbox.client.LegacyWidget',
        'extends Button':'extends dev.unbox.client.LegacyButton',
        'new FontDescription.Resource(':'(',
        'FontDescription FONT':'ResourceLocation FONT',
        '.pushMatrix()':'.pushPose()', '.popMatrix()':'.popPose()',
        'ResourceLocation.fromNamespaceAndPath(':'new ResourceLocation(',
        'ResourceLocation.withDefaultNamespace(':'new ResourceLocation("minecraft",',
        'ResourceLocation.parse(':'new ResourceLocation(',
    }
    for before,after in replacements.items():text=text.replace(before,after)
    text=text.replace('g.enableScissor(', 'dev.unbox.client.LegacyGui.enableScissor(g,').replace('g.disableScissor()', 'dev.unbox.client.LegacyGui.disableScissor(g)')
    text=text.replace('<T extends dev.unbox.client.LegacyWidget>','<T extends AbstractWidget>')
    text=re.sub(r'\bg\.(text|centeredText|outline|item)\(',lambda m:'g.'+{'text':'drawString','centeredText':'drawCenteredString','outline':'renderOutline','item':'renderItem'}[m[1]]+'(',text)
    text=re.sub(r'\b(widget|w|child|renderable)\.extractRenderState\(',r'\1.render(',text)
    for method,last in [('scale','1'),('translate','0')]:
        text=call_args(text,'g.pose().'+method,lambda a,method=method,last=last:'g.pose().'+method+'('+','.join([a[0],a[0],last] if len(a)==1 else a+[last] if len(a)==2 else a)+')')
    text=call_args(text,'g.pose().rotate',lambda a:'g.pose().mulPose(new org.joml.Quaternionf().rotateZ('+a[0]+'))')
    text=text.replace('g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED,','LegacyGui.blit(g,')
    text=re.sub(r'new DynamicTexture\(\(\)->"[^"]*",','new DynamicTexture(',text)
    if name=='ItemPhysicGateMixin.java':text=text.replace('method="submit"','method="render"')
    if name=='GraveDeathInventoryMixin.java':text=text.replace('ServerLevel level, CallbackInfo ci','CallbackInfo ci')
    if name=='ScoreboardAppearance.java':text=text.replace('.withColor(text(title, score))','.withStyle(style->style.withColor(text(title, score)))')
    if name=='WorldMap.java':text=text.replace('mc.player.getSkin()','mc.player.getSkinTextureLocation()').replace('DefaultPlayerSkin.get(id)','DefaultPlayerSkin.getDefaultSkin(id)').replace('info.getSkin()','info.getSkinLocation()').replace('skin.body().texturePath()','skin')
    if name=='TestStartupDiagnostics.java':text=text.replace('mc.screen.mouseClicked(new dev.unbox.client.input.MouseButtonEvent(w.getX()+w.getWidth()/2.,w.getY()+w.getHeight()/2.,new dev.unbox.client.input.MouseButtonInfo(0,0)),false)','mc.screen.mouseClicked(w.getX()+w.getWidth()/2.,w.getY()+w.getHeight()/2.,0)')
    if name=='EnvironmentSync.java':
        text=text.replace('return buffer().writeByte(0);','var response=buffer();response.writeByte(0);return response;').replace('return buffer().writeByte(2);','var response=buffer();response.writeByte(2);return response;').replace('return buffer().writeByte(d.batch?3:1).writeUtf(d.hash,64).writeLong(d.offset);','var response=buffer();response.writeByte(d.batch?3:1);response.writeUtf(d.hash,64);response.writeLong(d.offset);return response;')
    if name=='UnboxClient.java':
        text=text.replace('TestStartupDiagnostics.tick(client);','TestStartupDiagnostics.tick(client);LegacyStartupCheck.tick(client);')
        text=re.sub(r'\b\w+SmokeTest\.tick\(client\);','',text)
    if name=='EnvironmentSync.java':text=text.replace('if(PrivateWorldSmokeTest.interruptStreamForTest(mc))return null;','')
    if name=='SocialLoginAccess.java':
        text=text.replace('"authenticatedProfile"','"gameProfile"').replace('@Accessor("requestedUsername") String unboxUsername();','')
    text=text.replace('g.blit(RenderPipelines.GUI_TEXTURED,','LegacyGui.blit(g,')
    return text


def java17(text):
    # Keep the existing executor sizes, cancellation and socket ownership. Java
    # 17 uses daemon platform threads where the newer targets use virtual ones.
    text = re.sub(r'Thread\.ofVirtual\(\)\.name\(("[^"]*"),0\)\.factory\(\)',
                  r'dev.unbox.client.LegacyThreads.factory(\1)', text)
    text = re.sub(r'Thread\.ofVirtual\(\)\.name\(("[^"]*")\)\.start\(',
                  r'dev.unbox.client.LegacyThreads.start(\1,', text)
    text = text.replace('Thread.ofVirtual().start(', 'dev.unbox.client.LegacyThreads.start("unbox-worker",')
    text = text.replace('Math.clamp(', 'net.minecraft.util.Mth.clamp(')
    text = text.replace('.getFirst()', '.get(0)')
    text = text.replace('ClientHud.IDS.reversed()', 'com.google.common.collect.Lists.reverse(ClientHud.IDS)')
    text = text.replace('attempts.getLast()', 'attempts.get(attempts.size()-1)')
    # These are ArrayLists; the other removeFirst/removeLast calls are Deques.
    text = text.replace('attempts.removeFirst()', 'attempts.remove(0)').replace('latency.removeFirst()', 'latency.remove(0)')
    return text


def remove_block(text, marker):
    start = text.index(marker)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[:start] + text[end:]


def without_happy_ghast(name, text):
    text = text.replace(',"ghast"', '').replace('||id.equals("ghast")', '').replace('||detail.equals("ghast")', '')
    if name == 'UnboxClient.java':
        text = text.replace('HappyGhastRecall.init();', '').replace('HappyGhastRecall.tick();', '')
    elif name == 'ClientIntegrations.java':
        text = text.replace('if(id.equals("ghast"))return HappyGhastRecall.key;', '')
    elif name == 'ModOptions.java':
        text = re.sub(r'new Mod\("ghast",[^\n]*?\),', '', text)
        text = remove_block(text, 'case "ghast" ->')
    elif name == 'UnboxScreen.java':
        text = remove_block(text, 'if(settingQuery.isBlank()&&detail.equals("ghast"))')
        text = re.sub(r'\s*case "ghast"->List\.of\([^\n]*\);', '', text)
    elif name == 'WorldFeatures.java':
        text = re.sub(r'import net.minecraft.(world.entity.(animal.happyghast.HappyGhast|player.Player|ai.attributes.Attributes|ai.attributes.AttributeModifier)|resources.Identifier);\n', '', text)
        text = re.sub(r'    private static final Identifier SPEED=[^\n]*\n', '', text)
        text = text.replace(',remoteGhast', '').replace('case "ghast"->remoteGhast;', '')
        text = text.replace(',boolean ghast', '').replace('remoteGhast=ghast;', '')
        text = re.sub(r'    private static volatile int ghastSpeed=[^\n]*\n', '', text)
        text = re.sub(r'            UnboxClient.set\("ghast\.[^\n]*\n', '', text)
        text = re.sub(r'        int speed=UnboxClient.number\("ghast.speed"[^\n]*\n', '', text)
        text = re.sub(r'\+"ghast.speed="[^\n]*;', ';', text)
        text = text.replace('        ghastSpeed=speed;\n', '')
        text = remove_block(text, '    /** Runs only for a loaded ghast,')
    elif name == 'SurvivalHostMods.java':
        text = text.replace(',boolean ghast', '').replace('buffer.writeBoolean(packet.ghast);', '')
        text = text.replace('new HostState(buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean())', 'new HostState(buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean(),buffer.readBoolean())')
        text = text.replace('WorldFeatures.receive(state.building,state.shulker,state.ghast)', 'WorldFeatures.receive(state.building,state.shulker)')
        text = text.replace('WorldFeatures.receive(false,false,false)', 'WorldFeatures.receive(false,false)')
        text = text.replace(',WorldFeatures.serverEnabled("ghast")', '')
        text = text.replace('survival_host_features_v2', 'survival_host_features_1201')
    elif name == 'MapTransport.java':
        text = '\n'.join(line for line in text.split('\n') if 'HappyGhastRecall' not in line)
    elif name == 'FeatureSmokeTest.java':
        text = re.sub(r'import net.minecraft.world.entity.animal.happyghast.HappyGhast;\n', '', text)
        text = re.sub(r' private static (HappyGhast ghast,calledGhast|double callStartDistance|double originalSpeed);\n', '', text)
        text = text.replace('UnboxClient.set("ghast",true);UnboxClient.set("ghast.speed",200);', '')
        start = text.index('     ghast=(HappyGhast)')
        end = text.index('    });wait=80;}', start)
        text = text[:start] + text[end:]
        text = remove_block(text, '    case 5->')
        text = text.replace('check(Math.abs(ghast.getAttribute(Attributes.FLYING_SPEED).getValue()-originalSpeed)<.0001,"Disabling ghast speed restores original attribute");player(mc).stopRiding();ghast.discard();', '')
        text = text.replace('contains("ghast=false")', 'contains("shulker=false")')
        text = text.replace('UnboxClient.set("ghast",true);UnboxClient.set("ghast.wait",1);', '')
        for stage in range(14, 20):
            text = remove_block(text, f'    case {stage}->')
        text = text.replace('case 20->finish(mc,null);', 'case 14->finish(mc,null);')
    elif name == 'SurvivalLanSmokeTest.java':
        text = re.sub(r'    private static [^\n]*(?:recallFixture|recallDistance|recallSent,recallVerified);\n', '', text)
        text = remove_block(text, '                if(current==1)')
        text = text.replace('state.addProperty("recallVerified",recallVerified);', '')
        text = '\n'.join(line for line in text.split('\n') if 'HappyGhastRecall' not in line)
    return text
