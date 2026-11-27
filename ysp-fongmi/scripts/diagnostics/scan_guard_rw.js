setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var mods=Process.enumerateModules();var gm=null;
     for(var i=0;i<mods.length;i++)if(mods[i].name.indexOf('ckguard')>=0)gm=mods[i];
     if(!gm){send('NOSO');return;}
     var base=parseInt(gm.base.toString(),16), end=base+gm.size;
     send('GBASE='+gm.base+' GEND=0x'+end.toString(16));
     var pat='636f6d2e666f6e676d692e616e64726f69642e7476';
     var all=Process.enumerateRanges('rw-').concat(Process.enumerateRanges('r--'));
     var hit=0;
     for(var i=0;i<all.length;i++){
       var r=all[i];
       var ra=parseInt(r.base.toString(),16);
       if(ra+ r.size < base || ra > end) continue; // only inside guard so
       try{
         var res=Memory.scanSync(r.base,r.size,pat);
         for(var j=0;j<res.length;j++){
           send('FOUND_IN_SO addr='+res[j].address+' rel=0x'+(parseInt(res[j].address.toString(),16)-base).toString(16)+' prot='+r.protection);
           hit++;
         }
       }catch(e){}
     }
     // also scan heap near 0x12c40000 for context
     send('DONE hits='+hit);
   }catch(e){send('ERR '+String(e));}
  });
 },800);
});
