setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
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
      send('FM_GUARD_HEAD='+g.substring(0,20)+' LEN='+g.length);
      send('FM_MODE='+CG.getMode.call(inst));
     }});
   }catch(e){send('ERR '+String(e));}
  });
 },1200);
});
