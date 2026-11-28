import frida, subprocess, time
ADB='/usr/lib/android-sdk/platform-tools/adb'
dev=frida.get_device_manager().add_remote_device('127.0.0.1:27042')
r=subprocess.run(f'{ADB} shell pidof com.fongmi.android.tv',shell=True,capture_output=True,text=True).stdout.strip()
print('pid',r,flush=True)
s=dev.attach(int(r.split()[0])); out=[]
sc=s.create_script(open('/tmp/scan_guard_rw.js').read())
sc.on('message',lambda m,d:out.append(str(m.get('payload',''))))
sc.load(); time.sleep(6)
for x in out[:20]: print(x[:200],flush=True)
try:s.detach()
except:pass
