'use strict';
(function(){
var KEY='loj-iptv-apk-v1';
function readSaved(){try{return JSON.parse(localStorage.getItem(KEY))||{};}catch(e){return {};}}
var saved=readSaved();
var S={
 sources:Array.isArray(saved.sources)?saved.sources:[],
 profiles:Array.isArray(saved.profiles)&&saved.profiles.length?saved.profiles:[{id:'default',name:'الرئيسي',kids:false}],
 profileId:saved.profileId||'default',
 favorites:saved.favorites||{},
 history:saved.history||{},
 settings:Object.assign({accent:'#44d5e5',layout:'cinematic',font:16},saved.settings||{}),
 content:{},status:{},page:'home',filter:'all',category:'all',query:'',overlay:'',playing:null,views:2
};
var menus=[['home','⌂','الرئيسية'],['live','▣','البث المباشر'],['movie','▤','أفلام'],['series','▦','مسلسلات'],['sources','⊞','السيرفرات'],['search','⌕','بحث'],['favorites','☆','المفضلة'],['continue','◴','متابعة المشاهدة'],['multiview','⊞','متعدد الشاشات'],['settings','⚙','الإعدادات']];
function save(){try{localStorage.setItem(KEY,JSON.stringify({sources:S.sources,profiles:S.profiles,profileId:S.profileId,favorites:S.favorites,history:S.history,settings:S.settings}));}catch(e){}}
function esc(x){return String(x==null?'':x).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');}
function id(){return 's'+Date.now().toString(36)+Math.random().toString(36).slice(2,8);}
function profile(){return S.profiles.find(function(p){return p.id===S.profileId;})||S.profiles[0];}
function stored(map){return map[S.profileId]||{};}
function src(id){return S.sources.find(function(s){return s.id===id;});}
function pbase(s){return s.url.replace(/\/+$/,'');}
function slabel(id){var s=src(id);return s?s.name:'مصدر غير معروف';}
function err(m){S.overlay='<div class="notice">'+esc(m)+'</div>';document.getElementById('overlay').innerHTML=S.overlay;setTimeout(function(){if(S.overlay.indexOf('notice')>=0)closeOverlay();},3500);}
function closeOverlay(){S.overlay='';S.playing=null;document.getElementById('overlay').innerHTML='';}
window.lojBack=function(){if(S.overlay){closeOverlay();return true;}if(S.page!=='home'){S.page='home';S.category='all';render();return true;}return false;};
function aurl(s,action,tail){
 var u=pbase(s)+'/player_api.php?username='+encodeURIComponent(s.username||'')+'&password='+encodeURIComponent(s.password||'');
 if(action)u+='&action='+encodeURIComponent(action);
 if(tail)u+='&'+tail;
 return u;
}
async function getText(url){
 var res=await fetch('/api/catalog',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({url:url})});
 var j=await res.json();
 if(!res.ok||!j.ok)throw Error(j.error||'رفض السيرفر الاتصال');
 return j.content;
}
async function getJson(url){var t=await getText(url);try{return JSON.parse(t);}catch(e){throw Error('السيرفر لم يرجع بيانات JSON صحيحة');}}
function settle(p){return p.then(function(value){return {ok:true,value:value};},function(error){return {ok:false,error:error};});}
function normGroup(groups,id){return groups[String(id)]||'عام';}
function itemsXtream(s,kind,rows,groups){
 return (Array.isArray(rows)?rows:[]).map(function(v){
 var itemId=kind==='series'?(v.series_id||v.id):v.stream_id;
 return {key:s.id+'|'+kind+'|'+itemId,sourceId:s.id,kind:kind,id:String(itemId||''),
 name:String(v.name||v.title||'بدون اسم'),logo:String(v.stream_icon||v.cover||''),group:normGroup(groups,v.category_id),
 ext:String(v.container_extension||'mp4'),rating:String(v.rating||'')};
 }).filter(function(v){return !!v.id;});
}
function groupsOf(rows){var g={};(Array.isArray(rows)?rows:[]).forEach(function(x){g[String(x.category_id)]=String(x.category_name||'عام');});return g;}
function parseM3U(text,s){
 var lines=text.replace(/\r/g,'').split('\n'),items=[],meta=null;
 if(text.trim().startsWith('#EXTM3U')===false&&/\.m3u8(?:\?|$)/i.test(s.url)){return [{key:s.id+'|live|0',sourceId:s.id,kind:'live',id:'0',name:s.name,logo:'',group:'مباشر',url:s.url}];}
 for(var i=0;i<lines.length;i++){
  var line=lines[i].trim();
  if(line.indexOf('#EXTINF')===0){
   var title=(line.split(',').slice(1).join(',')||'قناة').trim();
   var m=line.match(/group-title="([^"]*)"/i),logo=line.match(/tvg-logo="([^"]*)"/i);
   meta={name:title,group:m?m[1]:'عام',logo:logo?logo[1]:''};
  }else if(line&&line[0]!=='#'&&meta){
   var kind=/\/movie\/|\.mp4(?:\?|$)|\.mkv(?:\?|$)/i.test(line)?'movie':(/\/series\//i.test(line)?'series':'live');
   var k=String(items.length);
   items.push({key:s.id+'|'+kind+'|'+k,sourceId:s.id,kind:kind,id:k,name:meta.name,group:meta.group,logo:meta.logo,url:line,ext:'m3u8'});
   meta=null;
  }
 }
 return items;
}
async function loadOne(s){
 S.status[s.id]={state:'loading'};render();
 var start=Date.now();
 try{
  var result=[];
  if(s.type==='m3u'){
   var text=await getText(s.url);
   result=parseM3U(text,s);
  }else{
   var auth=await getJson(aurl(s));
   if(auth.user_info&&(auth.user_info.auth===0||auth.user_info.status==='Disabled'))throw Error('الحساب غير مفعل');
   var types=[['live','get_live_streams','get_live_categories'],['movie','get_vod_streams','get_vod_categories'],['series','get_series','get_series_categories']];
   var jobs=types.map(async function(t){
    var all=await Promise.all([settle(getJson(aurl(s,t[1]))),settle(getJson(aurl(s,t[2])))]);
    if(!all[0].ok)return [];
    return itemsXtream(s,t[0],all[0].value,all[1].ok?groupsOf(all[1].value):{});
   });
   var allKinds=await Promise.all(jobs.map(settle));
   allKinds.forEach(function(x){if(x.ok)result=result.concat(x.value);});
   if(!allKinds.some(function(x){return x.ok;}))throw Error('تعذر جلب قوائم Xtream');
  }
  S.content[s.id]=result;
  S.status[s.id]={state:'ok',ms:Date.now()-start,count:result.length};
 }catch(e){
  delete S.content[s.id];
  S.status[s.id]={state:'error',error:String(e.message||e)};
 }
 render();
}
function loadAll(){S.sources.filter(function(s){return s.enabled;}).forEach(function(s){loadOne(s);});}
function all(kind){
 var enabled=S.sources.filter(function(s){return s.enabled;});
 var result=[];
 enabled.forEach(function(s){result=result.concat(S.content[s.id]||[]);});
 if(kind)result=result.filter(function(i){return i.kind===kind;});
 if(S.filter!=='all')result=result.filter(function(i){return i.sourceId===S.filter;});
 if(profile().kids)result=result.filter(function(i){return !/adult|xxx|18\+|للكبار|اباح|إباح/i.test(i.group+' '+i.name);});
 return result;
}
function card(x){
 var fav=!!stored(S.favorites)[x.key],logo=x.logo&&/^https?:/i.test(x.logo);
 return '<button class="card" data-action="play" data-key="'+esc(x.key)+'">'
  +(logo?'<img class="poster" loading="lazy" src="'+esc(x.logo)+'" onerror="this.replaceWith(Object.assign(document.createElement(\'div\'),{className:\'poster\',textContent:\'▶\'}))">':'<div class="poster">▶</div>')
  +'<div class="name">'+esc(x.name)+'</div><div class="meta">'+esc(slabel(x.sourceId))+(fav?'　★':'')+' · '+esc(x.group)+'</div></button>';
}
function grid(arr){
 if(!arr.length)return '<div class="empty"><strong>لا يوجد محتوى حالياً</strong>أضف سيرفراً نشطاً أو تحقّق من الاتصال. <div class="actions" style="justify-content:center"><button data-action="page" data-id="sources" class="btn primary">إضافة سيرفر</button></div></div>';
 return '<div class="cards">'+arr.slice(0,650).map(card).join('')+'</div>'+(arr.length>650?'<p class="small">تظهر أول 650 نتيجة. استخدم البحث أو تصفية السيرفر لنتائج أدق.</p>':'');
}
function browse(kind){
 var title=kind==='live'?'البث المباشر':kind==='movie'?'الأفلام':'المسلسلات';
 var arr=all(kind),cats=[...new Set(arr.map(function(x){return x.group;}))];
 if(S.category!=='all')arr=arr.filter(function(x){return x.group===S.category;});
 if(S.query)arr=arr.filter(function(x){return x.name.toLowerCase().indexOf(S.query.toLowerCase())>=0;});
 return '<div class="toolbar"><h1>'+title+'</h1><span class="badge">'+arr.length+' عنصر</span><input id="searchInput" class="field" placeholder="ابحث عن عنوان..." value="'+esc(S.query)+'"></div>'
 +'<div class="filters"><button class="btn '+(S.category==='all'?'primary':'')+'" data-action="category" data-id="all">الكل</button>'
 +cats.map(function(c){return '<button class="btn '+(S.category===c?'primary':'')+'" data-action="category" data-id="'+esc(c)+'">'+esc(c)+'</button>';}).join('')+'</div>'+grid(arr);
}
function home(){
 var n=S.sources.filter(function(x){return x.enabled;}).length;
 var arr=all(),last=Object.values(stored(S.history)).sort(function(a,b){return b.at-a.at;}).map(function(h){return arr.find(function(x){return x.key===h.key;});}).filter(Boolean);
 return '<section class="banner"><div><strong>LOJ IPTV</strong><h2>كل سيرفراتك في مكان واحد</h2><p>قنوات وأفلام ومسلسلات من عدة سيرفرات نشطة في وقت واحد، مع اختيار المصدر وتشغيل شاشتين أو أربع شاشات.</p>'
 +'<button class="btn primary" data-action="page" data-id="sources">إدارة السيرفرات</button></div><span class="pill">'+n+' سيرفرات نشطة</span></section>'
 +(last.length?'<h2 class="section">متابعة المشاهدة</h2>'+grid(last.slice(0,12)):'')
 +'<h2 class="section">القنوات المباشرة</h2>'+grid(arr.filter(function(x){return x.kind==='live';}).slice(0,18))
 +'<h2 class="section">أحدث الأفلام من سيرفراتك</h2>'+grid(arr.filter(function(x){return x.kind==='movie';}).slice(0,18));
}
function sourcesPage(){
 var items=S.sources.map(function(s){
  var status=S.status[s.id]||{state:'idle'};
  var label=status.state==='ok'?'✓ متصل · '+status.count+' عنصر':status.state==='loading'?'◴ جارٍ التحديث':status.state==='error'?'✕ '+status.error:'لم يُفحص';
  return '<div class="sourcecard"><div class="sourceTop"><div><h3>'+esc(s.name)+'</h3><span class="small">'+esc(s.type.toUpperCase())+' · '+esc(s.url)+'</span></div><span class="badge">'+(s.enabled?'مفعل':'معطل')+'</span></div>'
    +'<p class="small">'+esc(label)+'</p><div class="actions">'
    +'<button class="btn" data-action="refresh" data-id="'+esc(s.id)+'">⟳ تحديث</button>'
    +'<button class="btn" data-action="edit" data-id="'+esc(s.id)+'">تعديل</button>'
    +'<button class="btn" data-action="toggle" data-id="'+esc(s.id)+'">'+(s.enabled?'تعطيل':'تفعيل')+'</button>'
    +'<button class="btn danger" data-action="remove" data-id="'+esc(s.id)+'">حذف</button></div></div>';
 }).join('');
 return '<div class="toolbar"><h1>إدارة السيرفرات</h1><button class="btn primary" data-action="add">＋ إضافة سيرفر</button><button class="btn" data-action="refreshAll">⟳ تحديث الكل</button></div>'
 +'<div class="notice">كل السيرفرات المفعلة تعمل معاً؛ إذا تعطل سيرفر تستمر السيرفرات الأخرى. بيانات الاشتراك محفوظة محلياً على الجهاز وليست مشفرة تشفيراً مستقلاً.</div>'
 +(items||'<div class="empty"><strong>ابدأ بإضافة سيرفرك الأول</strong>يدعم Xtream Codes وM3U/M3U8.</div>');
}
function settingsPage(){
 var colors=['#44d5e5','#efb94f','#ab82f5','#e96878','#f5f5f5'];
 return '<h1>إعدادات LOJ</h1><div class="panel"><h2>لون الواجهة</h2><div class="filters">'
 +colors.map(function(c){return '<button class="btn" style="background:'+c+';color:#111" data-action="accent" data-id="'+c+'">'+(S.settings.accent===c?'✓ ':'')+c+'</button>';}).join('')
 +'</div><h2>وضع العرض</h2><div class="filters">'
 +[['cinematic','سينمائي'],['dashboard','بطاقات كبيرة'],['compact','مدمج']].map(function(x){return '<button class="btn '+(S.settings.layout===x[0]?'primary':'')+'" data-action="layout" data-id="'+x[0]+'">'+x[1]+'</button>';}).join('')
 +'</div><h2>حجم الخط</h2><div class="filters"><button class="btn" data-action="font" data-id="14">صغير</button><button class="btn" data-action="font" data-id="16">عادي</button><button class="btn" data-action="font" data-id="19">كبير</button></div></div>'
 +'<div class="panel"><h2>الملفات الشخصية</h2><div class="actions"><button class="btn primary" data-action="profile">تبديل الملف / إضافة ملف أطفال</button></div></div>'
 +'<p class="notice">تعمل قوائم جميع السيرفرات المفعلة بالتوازي. قد يمنع المزوّد بعض صيغ البث أو يحدد عدد الاتصالات، ولا يمكن للتطبيق تجاوز هذه الحدود.</p>';
}
function multiview(){
 var channels=all('live');var count=S.views,tiles='';
 for(var n=0;n<count;n++){
  tiles+='<div class="vidbox"><div class="small">الشاشة '+(n+1)+'</div><select class="field multisel" data-slot="'+n+'" style="width:100%;margin:8px 0"><option value="">اختر قناة</option>'
   +channels.slice(0,1000).map(function(v){return '<option value="'+esc(v.key)+'">'+esc(v.name)+' — '+esc(slabel(v.sourceId))+'</option>';}).join('')
   +'</select><video id="tile'+n+'" controls playsinline muted preload="none"></video><button class="btn" data-action="audio" data-id="'+n+'">تشغيل صوت هذه الشاشة</button></div>';
 }
 return '<div class="toolbar"><h1>شاشات متعددة</h1><button class="btn '+(count===2?'primary':'')+'" data-action="views" data-id="2">شاشتان</button><button class="btn '+(count===4?'primary':'')+'" data-action="views" data-id="4">4 شاشات</button></div>'
 +'<p class="notice">يمكن تشغيل قنوات من سيرفرات مختلفة معاً. النجاح يعتمد على عدد الاتصالات المسموح بها وقدرة الجهاز وصيغ الفيديو؛ ابدأ بشاشتين.</p>'
 +'<div class="vidgrid">'+tiles+'</div>';
}
function render(){
 var ac=S.settings.accent;document.documentElement.style.setProperty('--accent',ac);
 document.documentElement.style.fontSize=S.settings.font+'px';
 document.body.className='layout-'+S.settings.layout;
 var nav=menus.map(function(m){return '<button class="navbtn '+(S.page===m[0]?'selected':'')+'" data-action="page" data-id="'+m[0]+'"><span>'+m[1]+'</span>'+m[2]+'</button>';}).join('');
 var count=S.sources.filter(function(s){return s.enabled;}).length;
 var top='<div class="topbar"><strong style="color:var(--accent)">LOJ</strong><span class="badge">'+count+' سيرفرات مفعلة</span>'
  +'<select class="field" id="filterSource"><option value="all">كل السيرفرات</option>'+S.sources.filter(function(s){return s.enabled;}).map(function(s){return '<option value="'+esc(s.id)+'" '+(S.filter===s.id?'selected':'')+'>'+esc(s.name)+'</option>';}).join('')+'</select>'
  +'<button class="btn" data-action="profile">'+esc(profile().name)+'</button></div>';
 var content='';
 if(S.page==='home')content=home();
 if(S.page==='live'||S.page==='movie'||S.page==='series')content=browse(S.page);
 if(S.page==='sources')content=sourcesPage();
 if(S.page==='settings')content=settingsPage();
 if(S.page==='multiview')content=multiview();
 if(S.page==='search'){content='<h1>البحث في كل السيرفرات</h1><input id="searchInput" class="field" placeholder="اكتب اسم القناة أو الفيلم أو المسلسل" value="'+esc(S.query)+'" style="width:100%;margin:12px 0">'
 +grid(all().filter(function(i){return S.query&&i.name.toLowerCase().indexOf(S.query.toLowerCase())>=0;}));}
 if(S.page==='favorites'){var f=stored(S.favorites);content='<h1>المفضلة</h1>'+grid(all().filter(function(i){return f[i.key];}));}
 if(S.page==='continue'){var h=Object.values(stored(S.history)).sort(function(a,b){return b.at-a.at;});content='<h1>متابعة المشاهدة</h1>'+grid(h.map(function(v){return all().find(function(i){return i.key===v.key;});}).filter(Boolean));}
 document.getElementById('app').innerHTML='<aside><div class="brand">LOJ <small style="font-size:13px;color:#fff">IPTV</small></div>'+nav+'</aside><main>'+top+content+'</main><nav class="mobileNav">'+nav+'</nav>';
}
function sourceDialog(s){
 s=s||{id:'',name:'',type:'xtream',url:'',username:'',password:'',enabled:true,liveExt:'m3u8'};
 S.overlay='<div class="modalback"><div class="modal"><div class="toolbar"><h2>'+(s.id?'تعديل السيرفر':'إضافة سيرفر')+'</h2><button class="btn" data-action="close">✕</button></div>'
 +'<form id="sourceForm" class="formGrid">'
 +'<label class="fieldlabel">اسم السيرفر<input required name="name" class="field" value="'+esc(s.name)+'" placeholder="مثلاً AM"></label>'
 +'<label class="fieldlabel">نوع السيرفر<select name="type" class="field" id="sourceType"><option value="xtream" '+(s.type==='xtream'?'selected':'')+'>Xtream Codes</option><option value="m3u" '+(s.type==='m3u'?'selected':'')+'>M3U / M3U8</option></select></label>'
 +'<label class="fieldlabel" style="grid-column:1/-1">رابط السيرفر<input type="url" required name="url" class="field" value="'+esc(s.url)+'" placeholder="http://host:8080"></label>'
 +'<label class="fieldlabel xtfield">اسم المستخدم<input name="username" class="field" value="'+esc(s.username||'')+'"></label>'
 +'<label class="fieldlabel xtfield">كلمة المرور<input name="password" class="field" type="password" value="'+esc(s.password||'')+'"></label>'
 +'<label class="fieldlabel xtfield">صيغة البث<select name="liveExt" class="field"><option value="m3u8">m3u8</option><option value="ts" '+(s.liveExt==='ts'?'selected':'')+'>ts</option></select></label>'
 +'<label class="fieldlabel">تفعيل السيرفر<input type="checkbox" name="enabled" '+(s.enabled?'checked':'')+'></label>'
 +'<div style="grid-column:1/-1"><input type="hidden" name="id" value="'+esc(s.id)+'"><button type="submit" class="btn primary">حفظ السيرفر</button><button type="button" data-action="close" class="btn">إلغاء</button></div></form></div></div>';
 document.getElementById('overlay').innerHTML=S.overlay;toggleFields();document.querySelector('#sourceForm input[name=name]').focus();
}
function toggleFields(){var select=document.getElementById('sourceType');if(!select)return;document.querySelectorAll('.xtfield').forEach(function(e){e.style.display=select.value==='m3u'?'none':'';});}
function mediaUrl(i){
 if(i.url)return i.url;
 var s=src(i.sourceId);if(!s)return '';
 var u=encodeURIComponent(s.username||''),p=encodeURIComponent(s.password||'');
 if(i.kind==='live')return pbase(s)+'/live/'+u+'/'+p+'/'+i.id+'.'+(s.liveExt||'m3u8');
 return pbase(s)+'/'+(i.kind==='movie'?'movie':'series')+'/'+u+'/'+p+'/'+i.id+'.'+(i.ext||'mp4');
}
function locate(key){return all().find(function(x){return x.key===key;});}
function showVideo(i){
 S.playing=i; var url=mediaUrl(i);
 if(!url){err('لا يمكن استخراج رابط الفيديو');return;}
 var h=stored(S.history);h[i.key]={key:i.key,at:Date.now()};S.history[S.profileId]=h;save();
 var f=!!stored(S.favorites)[i.key];
 S.overlay='<div class="modalback"><div class="modal" style="width:min(1100px,100%)"><div class="toolbar"><h2>'+esc(i.name)+'</h2><button class="btn" data-action="close">إغلاق</button></div>'
 +'<video id="mainvideo" controls autoplay playsinline preload="metadata"></video>'
 +'<p class="small">السيرفر: '+esc(slabel(i.sourceId))+' · إذا تعذر التشغيل جرّب صيغة بث أخرى أو تحقّق من السماح بالاتصال في اشتراكك.</p>'
 +'<button class="btn" data-action="fav" data-key="'+esc(i.key)+'">'+(f?'★ إزالة من المفضلة':'☆ إضافة للمفضلة')+'</button></div></div>';
 document.getElementById('overlay').innerHTML=S.overlay;
 var v=document.getElementById('mainvideo');v.src=url;v.play().catch(function(){});
}
async function selectItem(i){
 if(i.kind!=='series'||i.url){showVideo(i);return;}
 var s=src(i.sourceId);if(!s)return;
 S.overlay='<div class="modalback"><div class="modal"><p>جارٍ تحميل حلقات '+esc(i.name)+' ...</p></div></div>';document.getElementById('overlay').innerHTML=S.overlay;
 try{
  var data=await getJson(aurl(s,'get_series_info','series_id='+encodeURIComponent(i.id)));
  var episodes=data.episodes||{},list=[];
  Object.keys(episodes).forEach(function(season){
   (episodes[season]||[]).forEach(function(ep){
    list.push({key:i.sourceId+'|episode|'+ep.id,sourceId:i.sourceId,kind:'episode',id:String(ep.id),
      name:String(ep.title||('الحلقة '+ep.episode_num)),group:'الموسم '+season,ext:(ep.info&&ep.info.container_extension)||'mp4'});
   });
  });
  if(!list.length)throw Error('لم يتم العثور على حلقات');
  S.content[i.sourceId]=(S.content[i.sourceId]||[]).concat(list);
  S.overlay='<div class="modalback"><div class="modal"><div class="toolbar"><h2>'+esc(i.name)+'</h2><button class="btn" data-action="close">✕</button></div><div class="actions">'
     +list.map(function(x){return '<button class="btn" data-action="play" data-key="'+esc(x.key)+'">'+esc(x.group)+' — '+esc(x.name)+'</button>';}).join('')+'</div></div></div>';
  document.getElementById('overlay').innerHTML=S.overlay;
 }catch(e){closeOverlay();err(e.message||'تعذر تحميل الحلقات');}
}
function showProfiles(){
 S.overlay='<div class="modalback"><div class="modal"><h2>من يشاهد الآن؟</h2><div class="actions">'
 +S.profiles.map(function(p){return '<button class="btn '+(p.id===S.profileId?'primary':'')+'" data-action="chooseProfile" data-id="'+esc(p.id)+'">'+esc(p.name)+(p.kids?' 🧒':'')+'</button>';}).join('')
 +'</div><form id="profileForm" style="margin-top:22px"><input class="field" name="name" placeholder="اسم الملف" required><label class="pill"><input type="checkbox" name="kids"> أطفال</label><button class="btn primary">إضافة</button></form><button class="btn" data-action="close">إغلاق</button></div></div>';
 document.getElementById('overlay').innerHTML=S.overlay;
}
document.addEventListener('submit',function(e){
 if(e.target.id==='sourceForm'){
  e.preventDefault();var f=new FormData(e.target);
  var old=src(String(f.get('id')||''));var s=old||{id:id()};
  s.name=String(f.get('name')).trim();s.type=String(f.get('type'));
  s.url=String(f.get('url')).trim().replace(/\/+$/,'');
  s.username=String(f.get('username')||'');s.password=String(f.get('password')||'');
  s.liveExt=String(f.get('liveExt')||'m3u8');s.enabled=!!f.get('enabled');
  if(!/^https?:\/\/[^/]+/i.test(s.url)){err('رابط السيرفر يجب أن يبدأ بـ http أو https');return;}
  if(s.type==='xtream'&&(!s.username||!s.password)){err('أدخل اسم المستخدم وكلمة المرور');return;}
  if(!old)S.sources.push(s);
  save();closeOverlay();S.page='sources';render();if(s.enabled)loadOne(s);return;
 }
 if(e.target.id==='profileForm'){
  e.preventDefault();var f=new FormData(e.target);
  var p={id:id(),name:String(f.get('name')).trim(),kids:!!f.get('kids')};
  if(p.name){S.profiles.push(p);S.profileId=p.id;save();closeOverlay();render();}
 }
});
document.addEventListener('change',function(e){
 var t=e.target;
 if(t.id==='filterSource'){S.filter=t.value;S.category='all';render();}
 if(t.id==='sourceType'){toggleFields();}
 if(t.classList.contains('multisel')){
  var slot=t.dataset.slot;var v=document.getElementById('tile'+slot);var item=locate(t.value);
  v.pause();v.removeAttribute('src');v.load();
  if(item){v.src=mediaUrl(item);v.muted=true;v.load();v.play().catch(function(){});}
 }
});
document.addEventListener('input',function(e){
 if(e.target.id==='searchInput'){
  var position=e.target.selectionStart;S.query=e.target.value;
  var old=e.target;render();var next=document.getElementById('searchInput');if(next){next.focus();next.setSelectionRange(position,position);}
 }
});
document.addEventListener('click',function(e){
 var b=e.target.closest('[data-action]');if(!b)return;
 var a=b.dataset.action,k=b.dataset.key,d=b.dataset.id;
 if(a==='close'){closeOverlay();return;}
 if(a==='page'){S.page=d;S.category='all';S.query='';closeOverlay();render();return;}
 if(a==='category'){S.category=d;render();return;}
 if(a==='add'){sourceDialog();return;}
 if(a==='edit'){sourceDialog(src(d));return;}
 if(a==='refresh'){var s=src(d);if(s&&s.enabled)loadOne(s);return;}
 if(a==='refreshAll'){loadAll();return;}
 if(a==='toggle'){var s=src(d);if(s){s.enabled=!s.enabled;if(!s.enabled){delete S.content[d];S.status[d]={state:'disabled'};}save();render();if(s.enabled)loadOne(s);}return;}
 if(a==='remove'){if(confirm('هل تريد حذف هذا السيرفر؟')){S.sources=S.sources.filter(function(s){return s.id!==d;});delete S.content[d];delete S.status[d];save();render();}return;}
 if(a==='play'){var item=locate(k);if(item)selectItem(item);return;}
 if(a==='fav'){var f=stored(S.favorites);f[k]=!f[k];S.favorites[S.profileId]=f;save();var item=locate(k);if(item)showVideo(item);return;}
 if(a==='views'){S.views=Number(d);render();return;}
 if(a==='audio'){document.querySelectorAll('.vidbox video').forEach(function(v,n){v.muted=String(n)!==d;});return;}
 if(a==='accent'){S.settings.accent=d;save();render();return;}
 if(a==='layout'){S.settings.layout=d;save();render();return;}
 if(a==='font'){S.settings.font=Number(d);save();render();return;}
 if(a==='profile'){showProfiles();return;}
 if(a==='chooseProfile'){S.profileId=d;S.category='all';save();closeOverlay();render();return;}
});
document.addEventListener('keydown',function(e){
 if(e.key==='Escape'||e.key==='Backspace'&&document.activeElement.tagName!=='INPUT'){if(window.lojBack())e.preventDefault();return;}
 if(['ArrowUp','ArrowDown','ArrowLeft','ArrowRight'].indexOf(e.key)<0)return;
 var a=document.activeElement;if(a&&['INPUT','SELECT','TEXTAREA','VIDEO'].indexOf(a.tagName)>=0)return;
 var root=S.overlay?document.getElementById('overlay'):document.getElementById('app');
 var elems=[...root.querySelectorAll('button,input,select,[tabindex="0"]')].filter(function(t){var r=t.getBoundingClientRect();return r.width&&r.height;});
 var r=a&&a.getBoundingClientRect();if(!r||a===document.body){if(elems[0])elems[0].focus();return;}
 var ax=(r.left+r.right)/2,ay=(r.top+r.bottom)/2,best=null,score=Infinity;
 elems.forEach(function(v){
  if(v===a)return;
  var b=v.getBoundingClientRect(),dx=(b.left+b.right)/2-ax,dy=(b.top+b.bottom)/2-ay;
  var correct=e.key==='ArrowRight'?dx>0:e.key==='ArrowLeft'?dx<0:e.key==='ArrowUp'?dy<0:dy>0;
  if(!correct)return;
  var main=(e.key==='ArrowRight'||e.key==='ArrowLeft')?Math.abs(dx):Math.abs(dy);
  var cross=(e.key==='ArrowRight'||e.key==='ArrowLeft')?Math.abs(dy):Math.abs(dx);
  var sc=main+cross*2;
  if(sc<score){score=sc;best=v;}
 });
 if(best){e.preventDefault();best.focus();best.scrollIntoView({block:'nearest',inline:'nearest'});}
});
render();loadAll();
})();