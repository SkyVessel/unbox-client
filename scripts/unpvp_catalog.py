"""The executable 1.8.9 PvP catalogue; no modern-only or survival placeholders."""
def opt(key,label,kind,value,lo=0,hi=100,choices=()):
 return dict(key=key,label=label,kind=kind,fallback=str(value).lower() if isinstance(value,bool) else str(value),min=lo,max=hi,choices=list(choices))
def toggle(key,label,value=True):return opt(key,label,'toggle',value)
def slider(key,label,value,lo=0,hi=100):return opt(key,label,'slider',value,lo,hi)
def color(key,label,value):return opt(key,label,'color',value)
def choice(key,label,value,choices):return opt(key,label,'choice',value,choices=choices)
def catalog_for(original):
 keep={'fps','cps','ping','keystrokes','coordinates','armor','zoom','sprint','crosshair','nickname','scoreboard','chat','performance'}
 out=[m for m in original if m['id'] in keep]
 for m in out:
  if m['id']=='zoom':m['tabs']['General']=[o for o in m['tabs']['General'] if o['key']!='zoom.adaptive'];m['hint']='Hold the activation key to zoom. Configure it below.'
  if m['id']=='armor':
   for tab in m['tabs']:m['tabs'][tab]=[o for o in m['tabs'][tab] if o['key']!='armor.dynamic']
  if m['id']=='chat':
   m['tabs']['General']=[o for o in m['tabs']['General'] if o['key'] not in {'chat.spacing','chat.focused','chat.unfocused'}]
   m['tabs']['General'].append(slider('chat.history','Stored chat lines',500,100,2000));m['hint']='Appearance and history. Ctrl+F in chat opens search.'
  if m['id']=='sprint':m['title']='Auto Sprint';m['hint']='Automatically sprint while moving forward. Double-tap W is disabled while enabled.'
  if m['id']=='nickname':m['hint']='Local display only; your account and messages retain their identity.'
  if m['id']=='scoreboard':m['hint']='VanillaHUD renderer, adapted to Unbox.'
 def add(id,title,group,hint,options):out.append(dict(id=id,title=title,group=group,hint=hint,tabs={'General':options}))
 def appearance(id):return [slider(id+'.scale','Scale',100,50,200),color(id+'.color','Text color','#F4F6F8'),toggle(id+'.shadow','Text shadow'),toggle(id+'.background','Background',False),color(id+'.backgroundColor','Background color','#191E24'),slider(id+'.opacity','Background opacity',60),slider(id+'.padding','Padding',4,0,12),toggle(id+'.rounded','Rounded corners')]
 add('potions','Potion Effects','HUD','Active effects and remaining duration.',appearance('potions'))
 add('fullbright','Fullbright','Visuals','Brightens standard world lighting. Shaders may override this effect.',[])
 add('sneak','Toggle Sneak','Controls','Press sneak to toggle. Cleared in menus and on lost focus.',[])
 add('hitcolor','Hit Color','Visuals','DamageTint: local hurt overlay on players and mobs.',[color('hitcolor.color','Hurt color','#FF0000'),slider('hitcolor.opacity','Opacity',30),toggle('hitcolor.fade','Fade with hurt animation',False)])
 add('particles','Particles','Visuals','OverflowParticles adaptation; spawn filtering keeps rendering batched.',[slider('particles.density','Particle density',100),slider('particles.size','Particle size',100,25,200),slider('particles.limit','Particles per layer',2000,100,4000),toggle('particles.crit','Critical hits'),toggle('particles.magic','Enchanted hits'),toggle('particles.explosions','Explosions'),toggle('particles.smoke','Smoke'),toggle('particles.potions','Potion particles'),toggle('particles.blocks','Block particles'),toggle('particles.tint','Override color',False),color('particles.color','Particle color','#FFFFFF')])
 add('classic','Classic Animations','Visuals','OverflowAnimations 1.7 item transformations.',[toggle('classic.oldBlockhitting','Classic block hitting'),toggle('classic.firstTransformations','Classic item positions'),toggle('classic.fishingRodPosition','Classic fishing rod'),toggle('classic.oldItemRotations','Remove item sway')])
 add('hand','Hand Animations','Visuals','Antimations: independently suppress first-person animations.',[toggle('hand.'+key,label,False) for key,label in [('cancelAllHandResets','Suppress all equip resets'),('cancelItemUpdateHandResets','Suppress item update resets'),('cancelItemUseHandResets','Suppress use resets'),('cancelOwnBlockAnimations','Suppress blocking animation'),('cancelOwnBowAnimations','Suppress bow animation'),('cancelEatingAnimations','Suppress eating animation'),('cancelDrinkingAnimations','Suppress drinking animation')]])
 add('hurtcam','Hurt Camera','Visuals','Scale the damage camera rotation.',[slider('hurtcam.strength','Shake strength',0)])
 add('bobbing','View Bobbing','Visuals','NoScreenShake: control world and hand bobbing separately.',[toggle('bobbing.world','World bobbing',False),toggle('bobbing.hand','Hand bobbing')])
 add('time','Time Changer','Visuals','PolyTime adaptation. Visual time only; server time stays authoritative.',[slider('time.ticks','Visual time (0 = sunrise)',6000,0,23999)])
 add('lookback','Look Back','Controls','Hold the activation key to look behind. Configure it below.',[choice('lookback.mode','Mode','Hold',['Hold','Toggle'])])
 add('blur','Motion Blur','Visuals','PolyBlur phosphor effect. Uses additional GPU time; off by default.',[slider('blur.strength','Strength',3,1,9)])
 add('hotbar','Hotbar','HUD','VanillaHUD adaptation: preserve vanilla slots and item rendering.',[slider('hotbar.scale','Scale',100,50,150),slider('hotbar.offset','Bottom offset',0,0,150),toggle('hotbar.label','Held item name')])
 add('tab','Player List','HUD','VanillaHUD adaptation: player list header, footer and numeric latency.',[toggle('tab.header','Server header'),toggle('tab.footer','Server footer'),toggle('tab.ping','Numeric ping')])
 add('itemphysics','Item Physics','Visuals','Local dropped-item rotation and resting pose.',[slider('itemphysics.speed','Spin speed',100,0,200)])
 add('resources','Resource Packs','Visuals','Browse installed packs and compatible Modrinth packs.',[])
 def transform(prefix):return [slider(prefix+'.x','Horizontal offset',0,-100,100),slider(prefix+'.y','Vertical offset',0,-100,100),slider(prefix+'.z','Distance offset',0,-100,100),slider(prefix+'.scale','Size (%)',100,25,150),slider(prefix+'.pitch','Pitch',0,-180,180),slider(prefix+'.yaw','Yaw',0,-180,180),slider(prefix+'.roll','Roll',0,-180,180)]
 add('viewmodel','Viewmodel','Visuals','First-person arm and item position, size and rotation.',transform('viewmodel.item'))
 out[-1]['tabs']['Empty hand']=[toggle('viewmodel.separateHand','Separate empty-hand settings',False)]+transform('viewmodel.empty')
 for m in out:
  for options in m['tabs'].values():
   for o in options:
    if o['key'].endswith('.background') and o['kind']=='toggle':o['fallback']='false'
  if m['id'] in {'keystrokes','scoreboard','chat'}:m['tabs']['General'].insert(0,toggle(m['id']+'.background','Background',False))
 return out
