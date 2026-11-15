#!/usr/bin/env python3
"""Split the approved SVG export into persistent backing and live content slots.
No Pen access or source-workspace mutations. Example people/server images are omitted.
"""
from pathlib import Path
import xml.etree.ElementTree as ET
import copy, re
ROOT=Path(__file__).resolve().parents[1]
DEST=ROOT/'client-mod/src/26.3/resources/assets/unbox/solid/scenes'
DEST.mkdir(parents=True,exist_ok=True)
ET.register_namespace('', 'http://www.w3.org/2000/svg')
def xy(node):
    numbers=re.findall(r'-?[\d.]+',node.get('transform',''))
    return tuple(map(float,(numbers+['0','0'])[:2]))
for source in (ROOT/'design/solid-ui/source').glob('*.svg'):
    name=source.stem
    if name in ('sent','crown'):continue
    tree=ET.parse(source);root=tree.getroot();body=list(root)[1];back=copy.deepcopy(root);bb=list(back)[1];bb.clear();bb.set('clip-path',body.get('clip-path'))
    page=name.removeprefix('red-').removeprefix('yellow-')
    body.set('data-viewport','true');bb.set('data-viewport','true')
    backdrop=list(body)[0].get('fill','#e8e8ec')
    for node in list(body):
        x,y=xy(node);tag=node.tag.rsplit('}',1)[-1]
        backing=(not node.get('transform') and tag=='rect') or (page=='title' and (x,y)==(106,22)) or (page=='appearance' and (x,y)==(88,72))
        remove=False
        if page=='title':remove=(x<100 and y<700) or (x,y)==(533,570)
        if page in ('quick','friend'):
            remove=(x,y) in ((100,195),(657,394)) or (x>=1090 and y>=250 and y<620)
        if page in ('mods','pvp'):
            remove=(tag=='g' and 280<=x<=920 and y in (258,480)) or (x,y) in [(196,356),(205,415),(1223,123)]
        if page=='appearance':
            remove=(x<550 and 170<=y<710) or (x>=1060 and y>=650)
            # The checkmark in the first card is a fixture; selected/equipped state is live.
            if tag=='g' and x==688 and y==246:
                children=list(node)
                if len(children)==5:node.remove(children[-1])
        if page in ('title','mods','pvp') and node.get('d')=='M11 0l11 11-11 11-11-11z':remove=True
        # Solid controls retain their design color even when the world is visible.
        solid=(page in ('quick','friend') and (x,y) in ((144,596),(598,372),(746,372),(598,446),(1010,0),(984,250),(984,396))) or (page=='title' and (x,y) in ((576,314),(576,403),(576,504),(704,504),(832,504)))
        if solid and node.get('fill-opacity'):
            alpha=float(node.attrib.pop('fill-opacity'));color=node.get('fill')
            rgb=[round(int(color[i:i+2],16)*alpha+int(backdrop[i:i+2],16)*(1-alpha)) for i in (1,3,5)]
            node.set('fill','#'+''.join(f'{v:02x}' for v in rgb))
        # Preserve overlap occlusion while cutting the pale logo faces out of the ink layer.
        if (x,y) in ((692,226),(714,168)) and node.get('fill')==backdrop:node.set('data-knockout','true')
        # All interactive rails and tabs are now drawn from shared geometry in Java.
        if page in ('mods','pvp'):
            if 280<=x<700 and 75<=y<160:remove=True
            if (x,y)==(1185,134):remove=True
        if page=='title' and (x,y)==(542,474):remove=True
        node.set('data-layout-x',str(x));node.set('data-layout-y',str(y))
        if not node.get('transform') and tag=='rect':node.set('data-stretch','viewport')
        elif page=='title' and (x,y)==(106,22):
            fill=list(node)[0].get('fill');node.clear();node.tag='{http://www.w3.org/2000/svg}rect';node.attrib.update({'fill':fill,'data-stretch':'title'})
        elif page in ('mods','pvp') and (x,y)==(244,166):node.set('data-stretch','mods')
        elif page=='appearance' and (x,y)==(88,72):node.set('data-stretch','appearance')
        elif page in ('quick','friend') and (x,y)==(1010,0):node.set('data-stretch','friends')
        # The opacity ruler grows with the list viewport and is drawn natively.
        if page in ('mods','pvp') and 185<=x<=220 and 230<=y<=637:remove=True
        if backing:bb.append(copy.deepcopy(node))
        if backing or remove:body.remove(node)
    for document in (root,back):
        defs=list(document)[0]
        by_id={n.get('id'):n for n in defs}
        used=set()
        def refs(node):
            for child in node.iter():
                for value in child.attrib.values():
                    if value.startswith('#') and value[1:] in by_id:used.add(value[1:])
                    used.update(re.findall(r'url\(#([^)]*)\)',value))
        refs(list(document)[1])
        previous=set()
        while previous!=used:
            previous=set(used)
            for key in list(used):
                if key in by_id:refs(by_id[key])
        for node in list(defs):
            if node.get('id') not in used:defs.remove(node)
    ET.ElementTree(root).write(DEST/(name+'.svg'),encoding='unicode')
    ET.ElementTree(back).write(DEST/(name+'-background.svg'),encoding='unicode')
# New pages had only a Light export. Apply the exact approved theme palette.
palettes={
 'red':{'#f5f3ed':'#e8e8ec','#e9e6df':'#dcdde2','#29262f':'#292b2e','#ac94f4':'#d0d1d6','#ffad8a':'#d0d1d6','#f6f780':'#c9203d','#534963':'#55575d','#79757f':'#666870'},
 'yellow':{'#f5f3ed':'#323437','#e9e6df':'#383b3e','#29262f':'#d1d1c8','#ac94f4':'#44474a','#ffad8a':'#44474a','#f6f780':'#e4b900','#534963':'#c3c3ba','#79757f':'#aaa99f'}}
for page in ('friend','appearance'):
 for theme,palette in palettes.items():
  for suffix in ('','-background'):
   text=(DEST/(page+suffix+'.svg')).read_text()
   text=re.sub('#[0-9a-fA-F]{6}',lambda m:palette.get(m[0],m[0]),text)
   # The Unbox card footer uses the accent fill; its label needs accent contrast.
   if page=='appearance' and not suffix:
    themed=ET.fromstring(text)
    for card in list(themed)[1]:
     if xy(card)==(688,246):
      for child in card:
       if xy(child)==(18,183.5):child.set('fill','#f5f3ed' if theme=='red' else '#292b2e')
    text=ET.tostring(themed,encoding='unicode')
   (DEST/(theme+'-'+page+suffix+'.svg')).write_text(text)
print('Prepared 18 live UI scene layers and matching backgrounds')
for theme in ('','red-','yellow-'):
    tree=ET.parse(DEST/(theme+'appearance.svg'));body=list(tree.getroot())[1]
    for node in list(body):body.remove(node)
    tree.write(DEST/(theme+'dialog.svg'),encoding='unicode')
    (DEST/(theme+'dialog-background.svg')).write_bytes((DEST/(theme+'appearance-background.svg')).read_bytes())

import json
cards=list(list(ET.parse(ROOT/'design/solid-ui/source/mods.svg').getroot())[1])[5:11]
icons={name:list(card)[1].get('d') for name,card in zip(['minimap','zoom','armor','fullbright','sprint','coordinates'],cards)}
(DEST.parent/'icons.json').write_text(json.dumps(icons))

# Keep only the two current themes in the runtime. Light exports remain design provenance.
for path in DEST.glob('*.svg'):
    if not path.name.startswith(('red-','yellow-')):path.unlink()

# Exact background silhouettes shared by hit testing and hover feedback.
geometry={}
for page,points in {'quick':[(598,372),(746,372),(598,446),(984,250),(984,396)],'title':[(576,314),(576,403),(576,504),(832,504)]}.items():
    for node in list(ET.parse(ROOT/'design/solid-ui/source'/('red-'+page+'.svg')).getroot())[1]:
        if xy(node) in points and node.get('d'):
            x,y=xy(node);geometry[f'{page}-{int(x)}-{int(y)}']=node.get('d')
for node in list(ET.parse(ROOT/'design/solid-ui/source/red-mods.svg').getroot())[1]:
    label={(359,127):'survival',(574,119):'pvp'}.get(xy(node))
    if label and node.get('d'):geometry['label-'+label]=node.get('d')
geometry['tab']='M0 82c32 0 37-21 43-48 5-24 19-34 43-34h24c24 0 38 10 43 34 6 27 11 48 43 48z'
(DEST.parent/'geometry.json').write_text(json.dumps(geometry,indent=2)+'\n')
