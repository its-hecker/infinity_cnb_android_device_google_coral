#!/usr/bin/env python3
"""Check portable, self-contained illustrations and deterministic asset generation."""
import json
import subprocess
from pathlib import Path

root=Path(__file__).resolve().parents[1]
files=sorted((root/'res/raw').glob('motion_lab_*.json'))
assert len(files)==9, 'All feature illustrations must be present'
before={p:p.read_bytes() for p in files}
subprocess.run(['python3',str(root/'tools/generate-motion-lab-illustrations.py')],check=True)
assert all(p.read_bytes()==value for p,value in before.items()), 'Generated assets are out of date'
for p in files:
    d=json.loads(p.read_text())
    assert (d['w'],d['h'],d['fr'])==(480,300,30),p.name
    assert not d['assets'] and not d.get('fonts') and not d.get('chars'),p.name
    assert len({x['ind'] for x in d['layers']})==len(d['layers']),p.name
    assert all(x['ty']==4 and x['ip']==0 and x['op']==d['op'] for x in d['layers']),p.name
    def visit(value):
        if isinstance(value,dict):
            assert 'x' not in value or set(value)=={'x','y'}, 'Expressions must not be used'
            if value.get('a')==1:
                times=[frame['t'] for frame in value['k']]
                assert times==sorted(times) and times[0]>=0 and times[-1]<=d['op'],p.name
            for v in value.values(): visit(v)
        elif isinstance(value,list):
            for v in value: visit(v)
    visit(d)
    if p.stem=='motion_lab_album':
        assert '.albumGlow' in p.read_text() and '.motionGlow' not in p.read_text()
print('Lottie: nine deterministic vector illustrations; no external images, fonts or expressions')
