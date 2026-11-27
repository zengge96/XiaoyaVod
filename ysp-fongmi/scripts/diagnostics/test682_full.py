import frida,time,subprocess,urllib.request,urllib.error,urllib.parse,json
ADB='/usr/lib/android-sdk/platform-tools/adb'
dev=frida.get_device_manager().add_remote_device('127.0.0.1:27042')
subprocess.run(f'{ADB} shell am force-stop com.cctv.yangshipin.app.androidp',shell=True,capture_output=True)
time.sleep(2)
pid=dev.spawn(['com.cctv.yangshipin.app.androidp'])
s=dev.attach(pid);out=[]
sc=s.create_script(open('/tmp/export682.js').read())
sc.on('message',lambda m,d:out.append(str(m.get('payload',''))))
sc.load();dev.resume(pid)
time.sleep(16)
try:s.detach()
except:pass
d={}
for x in out:
    if '=' in x:
        k,v=x.split('=',1);d[k]=v
print('CKLEN',len(d.get('OFF_CK','')),flush=True)
if 'OFF_CK' not in d:
    print('out',out);raise SystemExit
u=[l[8:] for l in open('/tmp/liveurl_dump.txt') if l.startswith('LIVEURL ')][0]
p=urllib.parse.urlparse(u);q=urllib.parse.parse_qs(p.query,keep_blank_values=True)
q.update({'cKey':[d['OFF_CK']],'fntick':[d['OFF_TICK']],'guid':[d['OFF_GUID']],'estid':[d['OFF_EST']]})
live=urllib.parse.urlunparse((p.scheme,p.netloc,p.path,'',urllib.parse.urlencode(q,doseq=True),''))
try:
    info=json.loads(urllib.request.urlopen(urllib.request.Request(live,headers={'User-Agent':'qqlive'}),timeout=15).read())
    pu=info.get('playurl','');print('IRET',info.get('iretcode'),'PULEN',len(pu),flush=True)
    if pu:
        try:
            rr=urllib.request.urlopen(urllib.request.Request(pu,headers={'User-Agent':'qqlive'}),timeout=12)
            b=rr.read();print('PU_OK',rr.status,len(b),b[:80],flush=True)
        except urllib.error.HTTPError as e:print('PU_HTTP',e.code,flush=True)
except Exception as e:print('LIVEERR',str(e)[:100],flush=True)
