var fakeFd={};
setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
    var openat=Module.findExportByName('libc.so','openat');
    if(openat){
      Interceptor.attach(openat,{
        onEnter:function(args){ this.p=args[1].readUtf8String(); },
        onLeave:function(ret){
          if(this.p && this.p.indexOf('cmdline')>=0){ fakeFd[parseInt(ret.toString())]=true; send('FAKE_FD '+ret+' '+this.p); }
        }
      });
    }
    var open=Module.findExportByName('libc.so','open');
    if(open){
      Interceptor.attach(open,{
        onEnter:function(args){ this.p=args[0].readUtf8String(); },
        onLeave:function(ret){
          if(this.p && this.p.indexOf('cmdline')>=0){ fakeFd[parseInt(ret.toString())]=true; send('FAKE_FD2 '+ret); }
        }
      });
    }
    var read=Module.findExportByName('libc.so','read');
    if(read){
      Interceptor.attach(read,{
        onEnter:function(args){ this.fd=parseInt(args[0].toString()); this.buf=args[1]; this.len=parseInt(args[2].toString()); },
        onLeave:function(ret){
          if(fakeFd[this.fd] && parseInt(ret.toString())>0){
            var s='com.cctv.yangshipin.app.androidp';
            var n=Math.min(s.length+1,this.len);
            var arr=[];for(var i=0;i<n;i++)arr.push(i<s.length?s.charCodeAt(i):0);
            Memory.writeByteArray(this.buf,arr);
            ret.replace(ptr(n));
            send('CMDLINE_FAKED');
          }
        }
      });
    }
    send('HOOKS_DONE');
    // now trigger genGuard
    var found=null;
    Java.enumerateClassLoaders({onMatch:function(l){try{if(l.loadClass('com.ysp.tv.ckey.CKey'))found=l;}catch(e){}},onComplete:function(){
      if(!found){send('NOLOADER');return;}
      var CF=Java.ClassFactory.get(found);
      var CG=CF.use('com.tencent.qqlive.tvkplayer.vinfo.ckey.CKeyGuard');
      CG.getLoaded.implementation=function(){return true;};
      var ctx=Java.use('android.app.ActivityThread').currentApplication();
      var inst=CG.INSTANCE.value;
      CG.init.call(inst,ctx,'V8.26.1034.0171');
      var g=CG.genGuard.call(inst,ctx,'2024078201');
      send('FAKED_GUARD_HEAD='+g.substring(0,20)+' LEN='+g.length);
    }});
   }catch(e){send('ERR '+String(e));}
  });
 },600);
});
