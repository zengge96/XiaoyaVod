setImmediate(function(){setTimeout(function(){Java.perform(function(){
 ['open','openat','read','__open','__read'].forEach(function(n){
   try{
     var a=Module.findExportByName('libc.so',n);
     if(a){send(n+' addr='+a+' thumb='+((parseInt(a.toString(),16)&1)?'YES':'NO')+' first='+a.readU32().toString(16));}
   }catch(e){send(n+' ERR '+String(e).substring(0,40));}
 });
 send('DONE');
});},400);});
