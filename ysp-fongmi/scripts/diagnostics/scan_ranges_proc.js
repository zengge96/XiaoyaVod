setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var mods=Process.enumerateModules();var gm=null;
     for(var i=0;i<mods.length;i++)if(mods[i].name.indexOf('ckguard')>=0)gm=mods[i];
     if(!gm){send('NOSO');return;}
     var base=parseInt(gm.base.toString(),16);
     send('GBASE='+gm.base+' SIZE='+gm.size);
     var pat='636f6d2e666f6e676d692e616e64726f69642e7476';
     var ranges=Process.enumerateRanges('r--') ;
     var hit=0;
     for(var i=0;i<ranges.length && hit<20;i++){
       var r=ranges[i];
       try{
         var res=Memory.scanSync(r.base,r.size,pat);
         for(var j=0;j<res.length;j++){
           send('FOUND addr='+res[j].address+' rel_guard=0x'+(parseInt(res[j].address.toString(),16)-base).toString(16));
           hit++;
         }
       }catch(e){}
     }
     send('DONE hits='+hit);
   }catch(e){send('ERR '+String(e));}
  });
 },800);
});
