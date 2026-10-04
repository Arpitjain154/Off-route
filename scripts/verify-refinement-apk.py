from pathlib import Path
import zipfile,hashlib,subprocess,os,re,json
apk=Path('android/app/build/outputs/apk/debug/app-debug.apk').resolve();old=Path('recovery/installed-IDR-1.4.0.apk').resolve();sdk=Path.home()/'AppData/Local/Android/Sdk/build-tools/36.0.0';env={**os.environ,'JAVA_HOME':str(Path.home()/'.jdks/jbr-21.0.11')}
def verify(path):
 r=subprocess.run([str(sdk/'apksigner.bat'),'verify','--verbose','--print-certs',str(path)],env=env,capture_output=True,text=True,check=True);return re.search(r'Signer #1 certificate SHA-256 digest: (\w+)',r.stdout).group(1)
a,b=verify(apk),verify(old)
if a!=b:raise RuntimeError('Signing mismatch; installation blocked')
r=subprocess.run([str(sdk/'aapt.exe'),'dump','badging',str(apk)],capture_output=True,text=True,check=True).stdout.splitlines()[0]
assert "name='com.idr.navigator'" in r and "versionCode='10'" in r
with zipfile.ZipFile(apk) as z:
 assert z.read('assets/public/index.html')==Path('dist/index.html').read_bytes(),'APK lacks latest UI'
 tiles=[n for n in z.namelist() if n.endswith('.pbf') and '/public/maps/' in n]
 assert len(tiles)>39000
 for name in ('speed.tflite','motion.tflite','gps-health.tflite'):
  assert z.read('assets/models/'+name)==Path('android/app/src/main/assets/models',name).read_bytes()
report={'package':r,'same_signing_certificate':True,'certificate_sha256':a,'sha256':hashlib.sha256(apk.read_bytes()).hexdigest(),'bytes':apk.stat().st_size,'offline_vector_tiles':len(tiles),'latest_ui_verified':True,'existing_model_bytes_verified':True}
Path('artifacts/refinement/apk-verification.json').write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
