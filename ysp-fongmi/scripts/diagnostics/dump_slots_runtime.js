setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
    var B=Java.use('com.ysp.tv.ckey.BssIdentity');
    var inst=B.INSTANCE.value;
    var f=B.class.getDeclaredField('SLOTS_ARMV7');
    f.setAccessible(true);
    var list=f.get(null);
    send('SIZE='+list.size());
    for(var i=0;i<list.size();i++){
      var p=list.get(i);
      try{
        var c1=p.component1();
        var c2=p.component2();
        send('SLOT'+i+' c1='+c1+' cls='+(c1&&c1.getClass?c1.getClass().getName():'?')+' c2='+String(c2).substring(0,40));
      }catch(e){send('SLOT'+i+' ERR='+String(e));}
    }
   }catch(e){send('ERR '+String(e));}
  });
 },1200);
});
