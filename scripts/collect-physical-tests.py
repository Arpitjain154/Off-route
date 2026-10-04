import subprocess,hashlib,json
from pathlib import Path
adb=r'C:\Users\surya\AppData\Local\Android\Sdk\platform-tools\adb.exe'
out=Path('artifacts/physical-tests');out.mkdir(exist_ok=True)
files=[]
for drive in ['af866a4b-f2a3-4d1b-90f7-30f74f921eaa','d62bf76e-e570-4555-8d65-c5ce7eb8870e']:
    for ext in ['drlog','fusion.csv']:
        name=f'{drive}.{ext}'
        data=subprocess.check_output([adb,'-s','RZCX80NLSNN','exec-out','run-as','com.idr.navigator','cat',f'files/drives/{name}'])
        (out/name).write_bytes(data)
        files.append(dict(name=name,bytes=len(data),sha256=hashlib.sha256(data).hexdigest()))
(out/'manifest.json').write_text(json.dumps(files,indent=2));print(json.dumps(files,indent=2))
