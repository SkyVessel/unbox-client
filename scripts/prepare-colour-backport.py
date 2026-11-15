#!/usr/bin/env python3
"""Native API adaptation for the existing MIT ColourScroller preset."""
from pathlib import Path
import sys
root=Path(__file__).resolve().parents[1];version=sys.argv[1]
if version not in ('1.20.1','1.21.11'):raise ValueError(version)
s=(root/'ports/colour-scroller/src/main/java/net/anware/tmc/colourscroller/ColourScroller.java').read_text()
if version=='1.20.1':
 s=s.replace('import net.minecraft.core.component.DataComponents;','').replace('Identifier','ResourceLocation').replace('ResourceLocation.withDefaultNamespace(', 'new ResourceLocation("minecraft",').replace('BuiltInRegistries.ITEM.getValue(', 'BuiltInRegistries.ITEM.get(')
 s=s.replace('result.copyFrom(DataComponents.CUSTOM_NAME,current);result.copyFrom(DataComponents.LORE,current);','if(current.hasCustomHoverName())result.setHoverName(current.getHoverName().copy());if(current.hasTag()&&current.getTag().contains("display",10)&&current.getTag().getCompound("display").contains("Lore",9))result.getOrCreateTagElement("display").put("Lore",current.getTag().getCompound("display").getList("Lore",8).copy());')
p=root/'.cache/ports'/('colour-scroller-fabric-'+version)/'sources/ColourScroller.java';p.parent.mkdir(parents=True,exist_ok=True);p.write_text(s)
