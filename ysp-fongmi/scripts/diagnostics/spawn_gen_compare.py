import frida, subprocess, time, json
ADB='/usr/lib/android-sdk/platform-tools/adb'
dev=frida.get_device_manager().add_remote_device('127.0.0.1:27042')
js=open('/tmp/dump_gen_inputs.js').read()
for pkg,out in [('com.cctv.yangshipin.app.androidp','/tmp/gen_off.json'),('com.myownapp0000.tvtest000000000','/tmp/gen_mypkg.json'),('com.fongmi.android.tv','/tmp/gen_fm.json')]:
    subprocess.run(f'{ADB} shell am force-stop {pkg}',shell=True,capture_output=True)
    time.sleep(2)
    try:
        pid=dev.spawn([pkg]); s=dev.attach(pid); outl=[]
        sc=s.create_script(js)
        sc.on('message',lambda m,d:outl.append(str(m.get('payload',''))))
        sc.load(); dev.resume(pid); time.sleep(13)
        d={}
        for x in outl:
            if '=' in x:
                k,v=x.split('=',1); d[k]=v
        json.dump(d,open(out,'w'))
        print(pkg,'->',out,'CKLEN',d.get('CKLEN'),'GUID',d.get('GUID'),'DEVGUID',d.get('DEVGUID'),flush=True)
        try:s.detach()
        except:pass
    except Exception as e:
        print(pkg,'ERR',str(e)[:100],flush=True)
