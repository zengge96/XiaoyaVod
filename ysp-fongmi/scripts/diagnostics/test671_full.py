import frida,time,subprocess,urllib.request,urllib.error,urllib.parse,json
ADB='/usr/lib/android-sdk/platform-tools/adb'
dev=frida.get_device_manager().add_remote_device('127.0.0.1:27042')
r=subprocess.run(f'{ADB} shell pidof com.fongmi.android.tv',shell=True,capture_output=True,text=True).stdout.strip()
print('pid',r,flush=True)
s=dev.attach(int(r.split()[0]));out=[]
sc=s.create_script(open('/tmp/emit671.js').read())
sc.on('message',lambda m,d:out.append(str(m.get('payload',''))))
sc.load();time.sleep(10);s.detach()
d={}
for x in out:
    if '=' in x:
        k,v=x.split('=',1);d[k]=v
print('CKLEN',d.get('CKLEN'),flush=True)
if 'CK' not in d:
    print('out',out);raise SystemExit
u=[l[8:] for l in open('/tmp/liveurl_dump.txt') if l.startswith('LIVEURL ')][0]
p=urllib.parse.urlparse(u);q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
q.update({'cKey':[d['CK']],'fntick':[d['TICK']],'guid':[d['GUID']],'estid':[d['EST']]})
live=urllib.parse.urlunparse((p.scheme,p.netloc,p.path,'',urllib.parse.urlencode(q,doseq=True),''))
try:
    info=json.loads(urllib.request.urlopen(urllib.request.Request(live,headers={'User-Agent':'qqlive'}),timeout=15).read())
    pu=info.get('playurl','');print('IRET',info.get('iretcode'),'PULEN',len(pu),flush=True)
    if pu:
        for ua in ['qqlive','okhttp/3.12.0']:
            try:
                rr=urllib.request.urlopen(urllib.request.Request(pu,headers={'User-Agent':ua}),timeout=12)
                b=rr.read();print('PU',ua,'OK',rr.status,len(b),b[:80],flush=True)
            except urllib.error.HTTPError as e:print('PU',ua,'HTTP',e.code,flush=True)
            except Exception as e:print('PU',ua,'ERR',str(e)[:60],flush=True)
except Exception as e:
    print('LIVEERR',str(e)[:100],flush=True)
