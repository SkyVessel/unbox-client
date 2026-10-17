#!/usr/bin/env python3
"""Download one pinned official Wick artifact and verify both recorded digests."""
import argparse,hashlib,json,pathlib,shutil,urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[1]
def main():
 p=argparse.ArgumentParser();p.add_argument("loader",choices=("fabric","neoforge"));p.add_argument("version",choices=("26.1","26.1.2","26.3"));p.add_argument("output",type=pathlib.Path);a=p.parse_args()
 lock=json.loads((ROOT/"src-tauri/resources/wick-lock.json").read_text())
 item=next(x for x in lock if x["loader"]==a.loader and a.version in x["gameVersions"])
 cache=ROOT/".cache"/item["filename"]
 if not cache.exists():
  opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
  request=urllib.request.Request(item["url"],headers={"User-Agent":"UnboxClient/0.2 Wick dependency fetch"})
  with opener.open(request,timeout=90) as response,cache.open("wb") as out:shutil.copyfileobj(response,out)
 data=cache.read_bytes()
 for algorithm in ("sha1","sha512"):
  if hashlib.new(algorithm,data).hexdigest()!=item[algorithm]:raise SystemExit(f"Wick {algorithm} verification failed")
 a.output.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(cache,a.output);print(f"verified {item['filename']} for {a.loader} {a.version}")
if __name__=="__main__":main()
