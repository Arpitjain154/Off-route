"""Read exact DRLOG v1 into a NumPy memmap / optional CSV for offline analysis."""
import argparse,struct,pathlib,numpy as np
def load(path):
    with open(path,'rb') as f: header=f.read(200)
    if header[:5]!=b'DRLOG' or header[5]!=1: raise ValueError('Not DRLOG v1')
    if (pathlib.Path(path).stat().st_size-200)%70: raise ValueError('Truncated recording: incomplete record')
    fields=[('t_ns','<i8')]+[(n,'<f4') for n in ['ax','ay','az','gx','gy','gz','mx','my','mz','lat','lon','alt','speed_mps','horiz_acc']]+[('sat_count','u1'),('mean_cn0','<f4'),('flags','u1')]
    dtype=np.dtype(fields,align=False);assert dtype.itemsize==70
    meta={'sample_rate_hz':struct.unpack_from('<H',header,6)[0]}
    for name,offset in [('phone_model',8),('mount',72),('session_id',136)]: meta[name]=header[offset:offset+64].split(b'\0')[0].decode('utf8')
    return meta,np.memmap(path,dtype=dtype,offset=200,mode='r')
if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('file');args=parser.parse_args();meta,records=load(args.file);print(meta);print('records',len(records));print(records[:3])
