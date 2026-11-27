import frida, subprocess, time, json
ADB='/usr/lib/android-sdk/platform-tools/adb'
dev=frida.get_device_manager().add_remote_device('127.0.0.1:27042')
js=open('/tmp/dump_gen_inputs.js').read()
for pkg,out in [('com.cctv.yangshipin.app.androidp','/tmp/gen_off.json'),('com.fongmi.android.tv','/tmp/gen_fm.json')]:
    r=subprocess.run(f'{ADB} shell pidof {pkg}',shell=True,capture_output=True,text=True).stdout.strip()
    print(pkg,'pids',r,flush=True)
    if not r: continue
    s=dev.attach(int(r.split()[0])); outl=[]
    sc=s.create_script(js)
    sc.on('message',lambda m,d:outl.append(str(m.get('payload',''))))
    sc.load(); time.sleep(8)
    d={}
    for x in outl:
        if '=' in x:
            k,v=x.split('=',1); d[k]=v
    json.dump(d,open(out,'w'))
    print(out,'CKLEN',d.get('CKLEN'),'GUID',d.get('GUID'),flush=True)
    try:s.detach()
    except:pass
