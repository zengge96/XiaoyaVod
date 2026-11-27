setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var mods=Process.enumerateModules();
     for(var i=0;i<mods.length;i++){
       var m=mods[i];
       if(m.name.indexOf('ckguard')<0) continue;
       send('MOD '+m.name+' base='+m.base+' size='+m.size);
       var pats=[
         ['FONGMI','636f6d2e666f6e676d692e616e64726f69642e7476'],
         ['OFFICIAL','636f6d2e636374762e79616e6773686970696e2e6170702e616e64726f696470'],
         ['CMDLINE','2f70726f632f25752f636d646c696e65']
       ];
       for(var k=0;k<pats.length;k++){
         try{
           var r=Memory.scanSync(ptr(m.base),m.size,{pattern:pats[k][1]});
           for(var j=0;j<r.length;j++){
             send('FOUND '+pats[k][0]+' addr='+r[j].address+' rel=0x'+(parseInt(r[j].address.toString(),16)-parseInt(m.base.toString(),16)).toString(16));
           }
         }catch(e){send('SCANERR '+pats[k][0]+' '+String(e).substring(0,60));}
       }
     }
     send('DONE');
   }catch(e){send('ERR '+String(e));}
  });
 },800);
});
