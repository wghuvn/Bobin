"""Remove float32 matrix-decomposition noise from explicitly rigid authoring.
This is format hygiene shared by exporters, not shared animation transforms.
"""
import struct

def normalize_rigid_scales(document, binary):
    for node in document['nodes']:
        if 'scale' in node:
            assert all(abs(v-1)<1e-5 for v in node['scale']), 'non-rigid authored node'
            node['scale']=[1,1,1]
    for animation in document.get('animations',[]):
        for channel in animation['channels']:
            if channel['target']['path']!='scale':continue
            sampler=animation['samplers'][channel['sampler']]
            assert sampler.get('interpolation','LINEAR')!='CUBICSPLINE'
            accessor=document['accessors'][sampler['output']]
            assert accessor['componentType']==5126 and accessor['type']=='VEC3'
            view=document['bufferViews'][accessor['bufferView']]
            start=view.get('byteOffset',0)+accessor.get('byteOffset',0);stride=view.get('byteStride',12)
            for i in range(accessor['count']):
                offset=start+i*stride
                assert all(abs(v-1)<1e-5 for v in struct.unpack_from('<3f',binary,offset)), 'non-rigid animation'
                struct.pack_into('<3f',binary,offset,1,1,1)
            for key in ('min','max'):
                if key in accessor:accessor[key]=[1,1,1]
