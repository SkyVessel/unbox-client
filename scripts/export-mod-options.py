#!/usr/bin/env python3
"""Export the game's actual catalogue; tiny config adapters avoid booting Minecraft."""
from pathlib import Path
import tempfile,subprocess,json,hashlib
root=Path(__file__).resolve().parents[1]
source=root/'client-mod/src/main/java/dev/unbox/client/ModOptions.java'
with tempfile.TemporaryDirectory() as temp:
 p=Path(temp);d=p/'dev/unbox/client';d.mkdir(parents=True)
 (d/'ModOptions.java').write_text(source.read_text())
 (d/'UnboxClient.java').write_text('package dev.unbox.client; public class UnboxClient {public static final java.util.Properties CONFIG=new java.util.Properties(); public static void save(){}}')
 (d/'ClientHud.java').write_text('package dev.unbox.client; public class ClientHud {public static final java.util.List<String> IDS=java.util.List.of("fps","cps","armor","coordinates","keystrokes","ping","minimap");public static String value(String k,String v){return UnboxClient.CONFIG.getProperty(k,v);}public static boolean flag(String k,boolean v){return Boolean.parseBoolean(value(k,""+v));}}')
 (d/'Export.java').write_text('''package dev.unbox.client;
 import java.util.*;import com.google.gson.Gson;
 public class Export {public static void main(String[] args){var result=new LinkedHashMap<String,Object>();
 for(var m:ModOptions.MODS){var tabs=new LinkedHashMap<String,Object>();for(var tab:ModOptions.tabs(m.id()))tabs.put(tab,ModOptions.options(m.id(),tab));
 if(m.id().equals("particles")){var variants=new LinkedHashMap<String,Object>();for(var name:ModOptions.options("particles","Types").getFirst().choices()){UnboxClient.CONFIG.setProperty("particles.family",name);variants.put(name,ModOptions.options("particles","Types"));}tabs.put("families",variants);}
 result.put(m.id(),tabs);}System.out.print(new Gson().toJson(result));}}''')
 cp=str(root/'.cache/libs/*')
 subprocess.run(['javac','-cp',cp,'-d',temp,*map(str,d.glob('*.java'))],check=True)
 result=json.loads(subprocess.check_output(['java','-cp',temp+':'+cp,'dev.unbox.client.Export']))
 (root/'app/src/mod-options.json').write_text(json.dumps({'sourceSha256':hashlib.sha256(source.read_bytes()).hexdigest(),'modules':result},indent=2)+'\n')
