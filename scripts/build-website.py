"""Package only the approved public landing page and its referenced assets."""
from pathlib import Path
import re, shutil

root = Path(__file__).resolve().parents[1]
source = root / 'design/website'
out = root / '.cache/website-public'
out.mkdir(parents=True, exist_ok=True)
(out / 'assets').mkdir(exist_ok=True)

def asset(match):
    path = (source / match.group(1)).resolve()
    assert path.is_file(), path
    dest = out / 'assets' / path.name
    shutil.copy2(path, dest)
    return 'assets/' + path.name

for name in ['index.html', 'website.css', 'website.js', 'feedback.js', 'contours.glsl']:
    text = (source / name).read_text()
    if name == 'website.css':
        text = re.sub(r'\.pen-canvas \.contours\{[^}]*\}\.pen-canvas\[data-theme=light\] \.contours\{[^}]*\}', '', text)
    text = re.sub(r'((?:\.\./)+(?:[^\s\x22\x27<>\)]+))', asset, text)
    for relative in re.findall(r'(?:src|href)="(assets/[^\x22]+)"', text):
        original = source / relative
        if original.is_file():
            shutil.copy2(original, out / relative)
    (out / name).write_text(text)
shutil.copy2(source / 'assets/LUCIDE-LICENSE', out / 'assets/LUCIDE-LICENSE')
print(f'Packaged {len(list(out.rglob("*")))} public files in {out}')
