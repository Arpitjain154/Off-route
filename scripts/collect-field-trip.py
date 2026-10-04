"""Copy one completed phone journey, preserving originals and verifying stable hashes."""
import sys,subprocess,json,hashlib,uuid
from pathlib import Path
serial,session,output=sys.argv[1:]
if serial!='RZCX71S8G8L' or str(uuid.UUID(session))!=session:raise ValueError('Use confirmed wired device and exact session UUID')
out=Path(output)/session
out.mkdir(parents=True,exist_ok=False)
adb='C:/Users/surya/AppData/Local/Android/Sdk/platform-tools/adb.exe'
manifest=[]
names=subprocess.check_output([adb,'-s',serial,'shell','run-as','com.idr.navigator','ls','files/drives'],timeout=15).decode().split()
suffixes=['.drlog','.quality.csv','.fusion.csv','.training.json','.summary.json']
if session+'.capture-error' in names:suffixes.append('.capture-error')
for suffix in suffixes:
 name=session+suffix;remote='files/drives/'+name
 data=subprocess.check_output([adb,'-s',serial,'exec-out','run-as','com.idr.navigator','cat',remote],timeout=30)
 if not data:raise ValueError('Empty file: '+name)
 (out/name).write_bytes(data)
 actual=hashlib.sha256(data).hexdigest()
 device=subprocess.check_output([adb,'-s',serial,'shell','run-as','com.idr.navigator','sha256sum',remote],timeout=15).decode().split()[0]
 if actual!=device:raise ValueError('File changed during transfer: '+name)
 manifest.append(dict(name=name,bytes=len(data),sha256=actual,deviceHashVerified=True))
(out/'manifest.json').write_text(json.dumps(manifest,indent=2),encoding='utf-8')
print(json.dumps({'session':session,'savedFiles':len(manifest),'allHashesVerified':True,'folder':str(out)}))
