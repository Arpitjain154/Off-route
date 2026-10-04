"""Validate ELF PT_LOAD alignment of native libraries bundled in an APK (64-bit required)."""
import sys,struct,json,zipfile
from pathlib import Path
apk=Path(sys.argv[1]);results=[]
with zipfile.ZipFile(apk) as archive:
    for name in archive.namelist():
        if not name.startswith(('lib/arm64-v8a/','lib/x86_64/')) or not name.endswith('.so'):continue
        b=archive.read(name);assert b[:4]==b'\x7fELF' and b[4]==2 and b[5]==1
        offset=struct.unpack_from('<Q',b,32)[0];size,count=struct.unpack_from('<HH',b,54)
        alignments=[]
        for i in range(count):
            header=offset+i*size
            if struct.unpack_from('<I',b,header)[0]==1:alignments.append(struct.unpack_from('<Q',b,header+48)[0])
        results.append({'library':name,'load_alignments':alignments,'supports_16kb_alignment':bool(alignments) and min(alignments)>=16384})
assert results,'No 64-bit native libraries found'
report={'apk':str(apk),'libraries':results,'passed':all(r['supports_16kb_alignment'] for r in results)}
Path('artifacts/native-page-alignment.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))
sys.exit(0 if report['passed'] else 1)
