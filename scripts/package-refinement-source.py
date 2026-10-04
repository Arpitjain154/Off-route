from pathlib import Path
import os,zipfile,json,hashlib,shutil
root=Path(__file__).resolve().parent.parent
os.chdir(root)
target=root/'artifacts/OffRoute-1.5.1-refinement-source.zip'
partial=target.with_suffix('.zip.partial')
assert target.parent.resolve()==root/'artifacts'
roots=['src','public','android','server','ml','scripts','docs','edge','deploy']
skip={'node_modules','build','.gradle','.idea','.git','__pycache__','uploads','.externalNativeBuild','.cxx'}
files=[]
for folder in roots:
 for parent,dirs,names in os.walk(folder):
  dirs[:]=[d for d in dirs if d not in skip and (Path(parent)/d).as_posix()!='android/app/src/main/assets/public']
  for name in names:
   if (name.startswith('.env') and not name.endswith('.example')) or name=='local.properties':continue
   files.append(Path(parent)/name)
for p in root.iterdir():
 if p.is_file() and (p.name in ['package.json','package-lock.json','index.html','capacitor.config.ts','vite.config.ts','README.md','.gitignore','.env.example','.nvmrc','.prettierrc.json','.prettierignore','.dockerignore','docker-compose.yml'] or p.name.startswith('tsconfig') and p.suffix=='.json'):files.append(p.relative_to(root))
with zipfile.ZipFile(partial,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=1) as archive:
 for i,p in enumerate(files):
  archive.write(p,p.as_posix(),compress_type=zipfile.ZIP_STORED if p.suffix=='.pbf' and p.as_posix().startswith('public/maps/') else zipfile.ZIP_DEFLATED)
  if i%10000==0:print(f'Archived {i}/{len(files)} files',flush=True)
 archive.writestr('SOURCE-README.txt','Existing OffRoute project source, offline web/native maps and bundled models. Generated build outputs and installed dependencies are excluded. Use npm install and npm run android:sync before Android compilation. Set the Android SDK location locally. Keep the existing Android signing key to update the installed com.idr.navigator package. Research datasets, verification screenshots/logs and private API credentials are not included. The edge module and server deployment configuration are included. See docs/refinement-report.md for limits and recovery locations.\n')
with zipfile.ZipFile(partial) as archive:
 assert archive.testzip() is None
 names=archive.namelist();assert len(names)==len(set(names))
 for p in files:
  if p.suffix in {'.ts','.tsx','.java','.py','.md','.gradle','.mjs','.json'} and 'maps' not in p.parts:
   assert archive.read(p.as_posix())==p.read_bytes(),str(p)
os.replace(partial,target)
shutil.copy2('android/app/build/outputs/apk/debug/app-debug.apk','artifacts/OffRoute-1.5.1-refinement.apk')
report={'source_archive':str(target),'files':len(files),'bytes':target.stat().st_size,'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),'crc_verified':True,'source_code_matches':True}
Path('artifacts/refinement/source-archive.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
print(json.dumps(report,indent=2),flush=True)
