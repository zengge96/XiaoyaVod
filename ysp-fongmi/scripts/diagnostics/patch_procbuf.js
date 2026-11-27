setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var mods=Process.enumerateModules();var gm=null;
     for(var i=0;i<mods.length;i++)if(mods[i].name.indexOf('ckguard')>=0)gm=mods[i];
     if(!gm){send('NOSO');return;}
     var b=parseInt(gm.base.toString(),16);
     send('GBASE='+gm.base);
     // overwrite process-name buffer at rel 0xcb228 with official name
     var off=0xcb228;
     var s='com.cctv.yangshipin.app.androidp';
     var p=ptr(b+off);
     try{
       for(var i=0;i<s.length;i++)p.add(i).writeU8(s.charCodeAt(i)&255);
       p.add(s.length).writeU8(0);
       var back=p.readUtf8String();
       send('PATCHED_BUF='+back);
     }catch(e){send('PATCHERR '+String(e).substring(0,80));}
     // now trigger generate
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
       // re-patch after init (init may rewrite buffer)
       try{for(var i=0;i<s.length;i++)p.add(i).writeU8(s.charCodeAt(i)&255);p.add(s.length).writeU8(0);send('REPATCHED');}catch(e){}
       var t=Math.floor(Date.now()/1000);
       var ck=CKey.generate.call(inst,t,'2024078201');
       send('BUF_CKLEN='+(ck?ck.length:0)+' head='+(ck?ck.substring(0,20):''));
     }});
   }catch(e){send('ERR '+String(e));}
  });
 },800);
});
