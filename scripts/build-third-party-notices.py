#!/usr/bin/env python3
"""Generate an offline third-party index from pinned artifacts and retained licenses."""
from pathlib import Path
import json,html,zipfile,hashlib
ROOT=Path(__file__).resolve().parents[1];RES=ROOT/'src-tauri/resources'
def main():
 lock=json.loads((RES/'survival-mods-lock.json').read_text());entries={}
 for target in lock['targets']:
  for mod in target['mods']:
   entry=entries.setdefault(mod['slug'],{'mods':[],'targets':[]})
   if not any(m['sha512']==mod['sha512'] for m in entry['mods']):entry['mods'].append(mod)
   entry['targets'].append(target['loader']+' '+target['minecraft'])
 out=['<!doctype html><html lang="en"><meta charset="utf-8"><title>Unbox — Third-party software</title><style>body{max-width:1000px;margin:48px auto;padding:0 24px;font:16px/1.6 system-ui;background:#202226;color:#eee}a{color:#a9c8ff}pre{white-space:pre-wrap;font-size:13px}article{margin:36px 0}small{color:#b9bbc2}</style><h1>Third-party software</h1><p>Unbox distributes native mods as separate JARs. Original authors retain their copyrights. Unofficial ports and their modifications are identified below; source archives include exact upstream revisions, licenses and build instructions. No upstream endorsement is implied.</p><h2>Corresponding source for Unbox ports</h2><ul>']
 for port in sorted((ROOT/'ports').iterdir()):
  if port.is_dir():
   archive=RES/(port.name+'-sources.zip')
   if not archive.is_file():raise SystemExit('Missing corresponding source: '+str(archive))
   out.append('<li><a href="'+port.name+'-sources.zip">'+html.escape(port.name)+' source archive</a> · SHA-256 '+hashlib.sha256(archive.read_bytes()).hexdigest()+'</li>')
 out.append('</ul>')
 for slug,entry in sorted(entries.items()):
  mod=entry['mods'][0];license=mod.get('license',{});license=', '.join(sorted({str(m['license'].get('id','See JAR license') if isinstance(m.get('license'),dict) else m.get('license','See JAR license')) for m in entry['mods']}))
  out.append('<article><h2>'+html.escape(slug)+'</h2><p>'+html.escape(license)+' · '+html.escape(', '.join(sorted({m['version'] for m in entry['mods']})))+'</p><p><a href="'+html.escape(mod.get('source') or mod['projectUrl'],quote=True)+'">Upstream source and authors</a></p><small>'+html.escape(', '.join(entry['targets']))+'</small>')
  seen=set()
  for m in entry['mods']:
   path=RES/m['resource'] if m.get('resource') else ROOT/'.cache/survival-mods'/m['filename']
   if not path.exists():continue
   with zipfile.ZipFile(path) as z:
    for name in z.namelist():
     base=Path(name).name.lower()
     if not (base.startswith(('license','copying','notice')) or base.endswith(('.license','.copying','.notice'))) or name.endswith('/') or z.getinfo(name).file_size>200000:continue
     text=z.read(name).decode('utf-8',errors='replace');digest=hashlib.sha256(text.encode()).hexdigest()
     if digest in seen:continue
     seen.add(digest);out.append('<details><summary>'+html.escape(name)+'</summary><pre>'+html.escape(text)+'</pre></details>')
  out.append('</article>')
 out.append('<h2>Port copyright, modifications and license texts</h2>')
 for port in sorted((ROOT/'ports').iterdir()):
  if not port.is_dir():continue
  for path in sorted(port.iterdir()):
   if path.is_file() and (path.name.lower().startswith(('license','copying','notice')) or path.name=='PORT.md'):
    out.append('<details><summary>'+html.escape(port.name+'/'+path.name)+'</summary><pre>'+html.escape(path.read_text(errors='replace'))+'</pre></details>')
 out.append('</html>');(RES/'third-party-notices.html').write_text('\n'.join(out))
 print('Generated offline license and source index for',len(entries),'projects')
if __name__=='__main__':main()
