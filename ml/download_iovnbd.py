"""Fetch the synchronized categorized IO-VNBD pairs from an immutable GitHub revision.
Resolves Git LFS pointers, streams downloads, and verifies each SHA-256 before accepting it.
Raw recordings stay in ignored data/iovnbd; no recordings are shipped in the app.
"""
import argparse, concurrent.futures, hashlib, json, pathlib, time, urllib.parse, urllib.request

ROOT=pathlib.Path(__file__).resolve().parents[1]
REVISION='118939602e3422d47b8ab0807b623751c3ac135b'
REPO='onyekpeu/IO-VNBD'
PREFIX='Synchronised V abd S datasets/Categorised IOVNB Dataset/'

def get(url):
    return urllib.request.urlopen(urllib.request.Request(url,headers={'User-Agent':'IDR-Navigator-research/1.0'}),timeout=90)

def fetch(entry):
    relative=entry['path'][len(PREFIX):]
    path=ROOT/'data/iovnbd'/relative
    encoded=urllib.parse.quote(entry['path'],safe='/')
    with get(f'https://raw.githubusercontent.com/{REPO}/{REVISION}/{encoded}') as r: pointer=r.read()
    info={'path':relative,'repository_path':entry['path']}
    if pointer.startswith(b'version https://git-lfs.github.com/spec/v1'):
        fields=dict(line.split(' ',1) for line in pointer.decode().splitlines())
        info.update(sha256=fields['oid'].split(':')[1],size_bytes=int(fields['size']))
        if path.exists() and path.stat().st_size==info['size_bytes']:
            with path.open('rb') as f: digest=hashlib.file_digest(f,'sha256').hexdigest()
            if digest==info['sha256']:return info
        path.parent.mkdir(parents=True,exist_ok=True)
        stage=path.with_suffix('.download')
        for attempt in range(3):
            try:
                h=hashlib.sha256();size=0
                with get(f'https://media.githubusercontent.com/media/{REPO}/{REVISION}/{encoded}') as r,stage.open('wb') as out:
                    while chunk:=r.read(1024*1024):out.write(chunk);h.update(chunk);size+=len(chunk)
                if size!=info['size_bytes'] or h.hexdigest()!=info['sha256']:raise ValueError('Git LFS size/checksum mismatch')
                stage.replace(path);break
            except Exception:
                if attempt==2:raise
                time.sleep(2*(attempt+1))
    else:
        path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(pointer)
        info.update(sha256=hashlib.sha256(pointer).hexdigest(),size_bytes=len(pointer))
    return info

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('--workers',type=int,default=6);args=parser.parse_args()
    with get(f'https://api.github.com/repos/{REPO}/git/trees/{REVISION}?recursive=1') as r:tree=json.load(r)
    entries=[e for e in tree['tree'] if e['path'].startswith(PREFIX) and e['path'].lower().endswith('.csv')]
    results=[]
    with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
        jobs={pool.submit(fetch,e):e for e in entries}
        for future in concurrent.futures.as_completed(jobs):
            info=future.result();results.append(info)
            print(f"[{len(results)}/{len(entries)}] {info['path']} ({info['size_bytes']/1e6:.1f} MB)",flush=True)
    manifest={'repository':f'https://github.com/{REPO}','revision':REVISION,'selection':'all synchronized categorized CSV files','files':sorted(results,key=lambda e:e['path'])}
    (ROOT/'data/iovnbd/manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf8')
    print('Complete:',len(results),'files,',round(sum(r['size_bytes'] for r in results)/1e6,1),'MB',flush=True)
