"""Synthetic contracts: original receiver labels and independently timestamped IMU."""
import csv
import hashlib
import json
import tempfile
from pathlib import Path
import unittest
import numpy as np
import test_phone_learning
from test_laptop_agent import MemoryStorage, package, SID, UID


class RawTrainingTests(unittest.TestCase):
    setUp=test_phone_learning.PhoneLearningTest.setUp
    recording=test_phone_learning.PhoneLearningTest.recording
    module=test_phone_learning.PhoneLearningTest.module
    def raw_recording(self):
        path = self.recording()
        meta = path.with_suffix('.training.json')
        value = json.loads(meta.read_text()); value['rawSidecarsSchemaVersion'] = 1
        meta.write_text(json.dumps(value))
        start = 12_345_678_901_230_000
        with path.with_suffix('.sensors.csv').open('w', newline='') as f:
            w = csv.writer(f); w.writerow(['t_ns','sensor_type','accuracy','value_count','v0','v1','v2','v3','v4'])
            for i in range(1600):
                t = start + i * 5_000_000
                for kind, values in ((1,[2,0,9.80665]),(4,[0,0,.25])):
                    w.writerow([t,kind,3,3,*values,'',''])
        with path.with_suffix('.gnss.csv').open('w', newline='') as f:
            w=csv.writer(f);w.writerow(['fix_t_ns','arrival_t_ns','utc_ms','latitude_deg','longitude_deg','altitude_m','horizontal_accuracy_m','vertical_accuracy_m','speed_mps','speed_accuracy_mps','bearing_deg','bearing_accuracy_deg','mock','accepted'])
            for i in range(8):
                t=start+i*1_000_000_000
                w.writerow([t,t+50_000_000,1700000000000+i*1000,23.1234567890123,77.1234567890123,500,4,6,12.345678901+i,.3,45,3,0,1])
        return path

    def test_precise_receiver_speed_and_raw_gyro_override_lossy_drlog(self):
        path=self.raw_recording(); w,y,t,a=self.module().read_session(path)
        np.testing.assert_allclose(y,[15.345678901,16.345678901,17.345678901,18.345678901,19.345678901],rtol=1e-7)
        self.assertEqual(y[0],15.345678901)
        np.testing.assert_allclose(w[:,:,0],2);np.testing.assert_allclose(w[:,:,5],.25)
        self.assertEqual(a['label_source'],'receiver_gnss')

    def test_independent_gyro_gap_rejects_training_despite_dense_accelerometer(self):
        path=self.raw_recording();p=path.with_suffix('.sensors.csv')
        with p.open() as f:rows=list(csv.DictReader(f))
        start=12_345_678_901_230_000
        rows=[r for r in rows if not (r['sensor_type']=='4' and start+3_200_000_000<int(r['t_ns'])<start+6_200_000_000)]
        with p.open('w',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
        w,y,t,a=self.module().read_session(path)
        np.testing.assert_allclose(y,[15.345678901]);self.assertGreater(a['rejections']['imu_gap'],0)

    def test_stale_receiver_callback_is_rejected(self):
        path=self.raw_recording();p=path.with_suffix('.gnss.csv')
        with p.open() as f:rows=list(csv.DictReader(f))
        for r in rows:r['arrival_t_ns']=str(int(r['fix_t_ns'])+2_000_000_000)
        with p.open('w',newline='') as f:
            writer=csv.DictWriter(f,fieldnames=list(rows[0]));writer.writeheader();writer.writerows(rows)
        self.assertEqual(len(self.module().read_session(path)[0]),0)

    def test_invalid_receiver_labels_never_fall_back_to_drlog_or_fusion(self):
        for column,value in [('mock','1'),('accepted','0'),('speed_accuracy_mps','2'),('horizontal_accuracy_m','30'),('arrival_t_ns','1')]:
            path=self.raw_recording(); p=path.with_suffix('.gnss.csv')
            with p.open() as f:rows=list(csv.DictReader(f))
            fields=list(rows[0])
            for row in rows:row[column]=value
            with p.open('w',newline='') as f:
                writer=csv.DictWriter(f,fieldnames=fields);writer.writeheader();writer.writerows(rows)
            path.with_suffix('.fusion.csv').write_text('speed_mps\n42\n')
            with self.subTest(column=column):self.assertEqual(len(self.module().read_session(path)[0]),0)

    def test_missing_declared_sidecar_fails_closed(self):
        path=self.raw_recording();path.with_suffix('.sensors.csv').unlink()
        self.assertEqual(len(self.module().read_session(path)[0]),0)

    def test_capture_error_blocks_even_complete_sidecars(self):
        path=self.raw_recording();path.with_suffix('.capture-error').write_text('flush failed')
        self.assertEqual(len(self.module().read_session(path)[0]),0)

    def test_replay_uses_receiver_coordinates_after_arrival(self):
        from laptop_agent.replay import write_replay
        path=self.raw_recording();w,y,t,a=self.module().read_session(path)
        data={'session':np.repeat(path.stem,len(y)),'t_ns':t}
        output=self.root/'replay.csv';write_replay(path,data,y,y,output,False)
        with output.open() as f:rows=[r for r in csv.DictReader(f) if r['reference']=='1']
        self.assertEqual(len(rows),5)
        self.assertEqual(float(rows[0]['lat']),23.1234567890123)
        self.assertEqual(float(rows[0]['speed']),15.345678901)
        self.assertGreaterEqual(int(rows[0]['t']),int(t[0]))


class SidecarDownloadTests(unittest.TestCase):
    def test_manifest_requires_declared_sidecars(self):
        from laptop_agent.ingest import validate_manifest
        store=MemoryStorage();manifest=package(store);manifest['rawSidecarsSchemaVersion']=1
        with self.assertRaises(ValueError):validate_manifest(manifest,SID)

    def test_training_metadata_cannot_hide_missing_sidecars(self):
        from laptop_agent.ingest import download_trip
        store=MemoryStorage();manifest=package(store)
        blob=json.dumps(dict(schemaVersion=1,source='device',sessionId=SID,rawSidecarsSchemaVersion=1)).encode()
        name=SID+'.training.json';obj=name+'.part00000';digest=hashlib.sha256(blob).hexdigest()
        store.objects['journeys',f'{UID}/{SID}/{obj}']=blob
        manifest['files'][2]=dict(originalName=name,sizeBytes=len(blob),sha256=digest,chunks=[dict(objectName=obj,sizeBytes=len(blob),sha256=digest)])
        store.objects['journeys',f'{UID}/{SID}/complete.json']=json.dumps(manifest).encode()
        with tempfile.TemporaryDirectory() as tmp:
            with self.assertRaises(ValueError):download_trip(store,UID,SID,Path(tmp))
            self.assertFalse((Path(tmp)/UID/SID).exists())

    def test_precise_sidecars_roundtrip(self):
        from laptop_agent.ingest import download_trip
        store=MemoryStorage();manifest=package(store)
        for suffix in ('.sensors.csv','.gnss.csv'):
            blob=b'12345678901234567,23.1234567890123,77.1234567890123\n'
            name=SID+suffix;obj=name+'.part00000';digest=hashlib.sha256(blob).hexdigest()
            store.objects['journeys',f'{UID}/{SID}/{obj}']=blob
            manifest['files'].append(dict(originalName=name,sizeBytes=len(blob),sha256=digest,chunks=[dict(objectName=obj,sizeBytes=len(blob),sha256=digest)]))
        store.objects['journeys',f'{UID}/{SID}/complete.json']=json.dumps(manifest).encode()
        with tempfile.TemporaryDirectory() as tmp:
            path=download_trip(store,UID,SID,Path(tmp))
            self.assertEqual((path/(SID+'.gnss.csv')).read_bytes(),blob)

if __name__=='__main__':unittest.main()
