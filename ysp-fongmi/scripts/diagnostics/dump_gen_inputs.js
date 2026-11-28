setImmediate(function(){
 setTimeout(function(){
  Java.perform(function(){
   try{
     var CKey=Java.use('com.ysp.tv.ckey.CKey');
     CKey.generate.overload('long','java.lang.String').implementation=function(t,c){
       var ck=this.generate(t,c);
       send('TICK='+t);
       send('CKLEN='+(ck?ck.length:0));
       send('CK='+ck);
       send('GUID='+this.getGUID.call(this));
       send('DEVGUID='+this.getDEVICE_GUID.call(this));
       try{send('EST='+this.estId.call(this));}catch(e){}
       return ck;
     };
     send('READY');
   }catch(e){send('ERR '+String(e));}
  });
 },300);
});
