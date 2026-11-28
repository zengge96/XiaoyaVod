setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var CF=Java.use('com.tencent.qqlive.tvkplayer.vinfo.ckey.CKeyFacade');
     CF.GenCKey.implementation=function(){
       var a=arguments;
       send('ARGC='+a.length);
       // args: tick(J) platform(I) appVer cnlid ? ? VSKey guid est arr flags guard
       for(var i=0;i<a.length;i++){
         var v=a[i];
         if(typeof v==='string') send('A'+i+'_STR='+v);
         else if(typeof v==='number') send('A'+i+'_NUM='+v);
         else if(v && v.class) send('A'+i+'_OBJ='+v.class.getName());
         else send('A'+i+'='+String(v));
       }
       var r=CF.GenCKey.apply(this,a);
       var s='';for(var i=0;i<r.length;i++)s+=String.fromCharCode(r[i]&255);
       send('GENLEN='+r.length);
       return r;
     };
     send('HOOKED');
   }catch(e){send('ERR '+String(e));}
  });
 },300);
});
