var fakeFd={};
setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
    var openat=Module.findExportByName('libc.so','openat');
    if(openat) Interceptor.attach(openat,{
      onEnter:function(a){this.p=a[1].readUtf8String();},
      onLeave:function(r){if(this.p&&this.p.indexOf('cmdline')>=0)fakeFd[parseInt(r.toString())]=true;}
    });
    var open=Module.findExportByName('libc.so','open');
    if(open) Interceptor.attach(open,{
      onEnter:function(a){this.p=a[0].readUtf8String();},
      onLeave:function(r){if(this.p&&this.p.indexOf('cmdline')>=0)fakeFd[parseInt(r.toString())]=true;}
    });
    var read=Module.findExportByName('libc.so','read');
    if(read) Interceptor.attach(read,{
      onEnter:function(a){this.fd=parseInt(a[0].toString());this.buf=a[1];this.len=parseInt(a[2].toString());},
      onLeave:function(r){
        if(fakeFd[this.fd]&&parseInt(r.toString())>0){
          var s='com.cctv.yangshipin.app.androidp';
          var n=Math.min(s.length+1,this.len);var arr=[];
          for(var i=0;i<n;i++)arr.push(i<s.length?s.charCodeAt(i):0);
          Memory.writeByteArray(this.buf,arr);r.replace(ptr(n));
        }
      }
    });
    send('HOOKS_READY');
    var found=null;
    Java.enumerateClassLoaders({onMatch:function(l){try{if(l.loadClass('com.ysp.tv.ckey.CKey'))found=l;}catch(e){}},onComplete:function(){
      if(!found){send('NOLOADER');return;}
      var CF=Java.ClassFactory.get(found);
      var CKey=CF.use('com.ysp.tv.ckey.CKey');
      var CG=CF.use('com.tencent.qqlive.tvkplayer.vinfo.ckey.CKeyGuard');
      CKey.getLoaded.implementation=function(){return true;};
      CG.getLoaded.implementation=function(){return true;};
      var ctx=Java.use('android.app.ActivityThread').currentApplication();
      var inst=CKey.INSTANCE.value;var cginst=CG.INSTANCE.value;
      CG.init.call(cginst,ctx,'V8.26.1034.0171');
      CKey.init.call(inst,ctx);
      var t=Math.floor(Date.now()/1000);
      var ck=CKey.generate.call(inst,t,'2024078201');
      send('CK='+ck);
      send('TICK='+t);
      send('GUID='+CKey.getGUID.call(inst));
      send('EST='+CKey.estId.call(inst));
      send('CKLEN='+(ck?ck.length:0));
     }});
   }catch(e){send('ERR '+String(e));}
  });
 },600);
});
