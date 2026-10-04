"""Deterministic, valid bootstrap models without a TensorFlow installation.
Input tensors exist with the full contract; constant outputs make placeholder status explicit.
Use generate_placeholders.py to train synthetic smoke models instead. Never use either for accuracy.
"""
import sys,pathlib
root=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'.tools/python'))
import flatbuffers, tflite, numpy as np
# TODO(real-data): these constant-output binaries validate the contract only; replace with trained models.
dest=root/'android/app/src/main/assets/models';dest.mkdir(parents=True,exist_ok=True)
def intvec(b,start,values):
    start(b,len(values))
    for v in reversed(values): b.PrependInt32(v)
    return b.EndVector()
def offsets(b,start,values):
    start(b,len(values))
    for v in reversed(values): b.PrependUOffsetTRelative(v)
    return b.EndVector()
for name,shape,values in [('motion',[1,42],[1,0,0,0,0]),('gps-health',[1,10],[0,1,0]),('speed',[1,300,6],[0])]:
    b=flatbuffers.Builder(2048)
    input_name=b.CreateString('features');output_name=b.CreateString('placeholder_output');desc=b.CreateString('PLACEHOLDER MODEL - synthetic bootstrap, replace with real drive training');sgname=b.CreateString(name)
    ishape=intvec(b,tflite.TensorStartShapeVector,shape)
    tflite.TensorStart(b);tflite.TensorAddShape(b,ishape);tflite.TensorAddType(b,tflite.TensorType.FLOAT32);tflite.TensorAddBuffer(b,0);tflite.TensorAddName(b,input_name);inp=tflite.TensorEnd(b)
    oshape=intvec(b,tflite.TensorStartShapeVector,[1,len(values)])
    tflite.TensorStart(b);tflite.TensorAddShape(b,oshape);tflite.TensorAddType(b,tflite.TensorType.FLOAT32);tflite.TensorAddBuffer(b,1);tflite.TensorAddName(b,output_name);out=tflite.TensorEnd(b)
    tensors=offsets(b,tflite.SubGraphStartTensorsVector,[inp,out]);inputs=intvec(b,tflite.SubGraphStartInputsVector,[0]);outputs=intvec(b,tflite.SubGraphStartOutputsVector,[1])
    tflite.SubGraphStart(b);tflite.SubGraphAddTensors(b,tensors);tflite.SubGraphAddInputs(b,inputs);tflite.SubGraphAddOutputs(b,outputs);tflite.SubGraphAddName(b,sgname);sg=tflite.SubGraphEnd(b)
    tflite.BufferStart(b);empty=tflite.BufferEnd(b)
    data=b.CreateByteVector(np.array(values,dtype='<f4').tobytes());tflite.BufferStart(b);tflite.BufferAddData(b,data);data_buffer=tflite.BufferEnd(b)
    buffers=offsets(b,tflite.ModelStartBuffersVector,[empty,data_buffer]);subgraphs=offsets(b,tflite.ModelStartSubgraphsVector,[sg])
    tflite.ModelStart(b);tflite.ModelAddVersion(b,3);tflite.ModelAddSubgraphs(b,subgraphs);tflite.ModelAddBuffers(b,buffers);tflite.ModelAddDescription(b,desc);model=tflite.ModelEnd(b);b.Finish(model,file_identifier=b'TFL3')
    (dest/(name+'.tflite')).write_bytes(bytes(b.Output()));print(name,shape,'->',[1,len(values)])
