#!/usr/bin/env python3
"""APK v2 digest transport. Final releases require independent SDK apksigner verification."""
import argparse, base64, hashlib, json, struct
from pathlib import Path

def lp(b): return struct.pack('<I',len(b))+b

def sections(data):
    end=data.rfind(b'PK\x05\x06', max(0,len(data)-65557))
    if end<0 or end+22+struct.unpack_from('<H',data,end+20)[0]!=len(data):
        raise ValueError('Invalid ZIP end')
    central=struct.unpack_from('<I',data,end+16)[0]
    size=struct.unpack_from('<I',data,end+12)[0]
    if central+size!=end or data[central:central+4]!=b'PK\x01\x02':
        raise ValueError('ZIP64/malformed ZIP not supported')
    if data[central-16:central]==b'APK Sig Block 42': raise ValueError('APK already signed')
    return central,end

def digest(data):
    central,end=sections(data)
    chunks=[]
    for section in (data[:central],data[central:end],data[end:]):
        for start in range(0,len(section),1048576):
            b=section[start:start+1048576]
            chunks.append(hashlib.sha256(b'\xa5'+struct.pack('<I',len(b))+b).digest())
    return hashlib.sha256(b'\x5a'+struct.pack('<I',len(chunks))+b''.join(chunks)).hexdigest()

def request(apk):
    data=Path(apk).read_bytes()
    return {'schema':'teclado-apk-v2-request-v1','unsignedSha256':hashlib.sha256(data).hexdigest(),'contentDigest':digest(data)}

def assemble(apk,manifest,output):
    m=json.loads(Path(manifest).read_text()); data=Path(apk).read_bytes()
    for key,value in request(apk).items():
        if m.get(key)!=value: raise ValueError('Signing request mismatch: '+key)
    block=base64.b64decode(m['signingBlock'],validate=True)
    if len(block)>65536 or len(block)<32 or block[-16:]!=b'APK Sig Block 42':
        raise ValueError('Invalid signing block')
    size=len(block)-8
    if struct.unpack_from('<Q',block)[0]!=size or struct.unpack_from('<Q',block,len(block)-24)[0]!=size:
        raise ValueError('Invalid signing block size')
    central,end=sections(data)
    out=bytearray(data[:central]+block+data[central:])
    struct.pack_into('<I',out,end+len(block)+16,central+len(block))
    Path(output).write_bytes(out)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['request','assemble']);p.add_argument('apk');p.add_argument('manifest',nargs='?');p.add_argument('output',nargs='?');a=p.parse_args()
    if a.action=='request': print(json.dumps(request(a.apk),separators=(',',':')))
    else: assemble(a.apk,a.manifest,a.output)
