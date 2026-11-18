package com.instatools.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;

/** Captures URLs of visible and browser-loaded media, then saves selected media to Gallery. */
public class MediaGrabber {
    private static final int MAX_MEDIA = 200;
    private static final long MAX_BYTES = 600L * 1024L * 1024L;
    private static final String HISTORY = "download_history_v2";
    private final Activity activity;
    private final LinkedHashMap<String,Item> detected = new LinkedHashMap<>();
    private String page = "https://www.instagram.com/";
    private String ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/130.0 Mobile Safari/537.36";
    private String pendingUrl = null;
    private String pendingKind = null;

    static class Item {
        final String url;
        final String kind;
        final String desc;
        Item(String url,String kind,String desc){this.url=url;this.kind=kind;this.desc=desc;}
    }

    MediaGrabber(Activity activity) {this.activity=activity;}

    void startPage(String url) {
        synchronized(detected) {detected.clear();}
        if(url!=null && url.startsWith("https://")) page=url;
    }

    private boolean validHttps(String raw) {
        try {
            URL u = new URL(raw);
            String host=u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getProtocol()) && host.length()>3 &&
                   !host.equals("localhost") && !host.endsWith(".local") &&
                   !host.startsWith("127.") && !host.startsWith("192.168.") &&
                   !host.startsWith("10.") && !host.startsWith("172.16.");
        }catch(Exception e){return false;}
    }

    private boolean isImage(String url) {
        String u=url.toLowerCase(Locale.ROOT);
        return u.matches(".*\\.(jpe?g|webp|png|heic|gif)([?&#].*)?$") ||
               (u.contains("fbcdn.net/") && (u.contains("/v/t") || u.contains("/v/")));
    }
    private boolean isVideo(String url){
        return url.toLowerCase(Locale.ROOT).matches(".*\\.(mp4|m4v|mov)([?&#].*)?$");
    }
    private void add(String url, String kind, String detail) {
        if(url==null || !validHttps(url) || url.length()>6500)return;
        if(!kind.equals("image") && !kind.equals("video"))return;
        synchronized(detected) {
            if(detected.containsKey(url)) return;
            if(detected.size()>=MAX_MEDIA) detected.remove(detected.keySet().iterator().next());
            detected.put(url,new Item(url,kind,detail));
        }
    }
    void intercept(WebResourceRequest request) {
        if(request==null)return;
        String raw=request.getUrl().toString(), host=request.getUrl().getHost();
        if(host==null) return;
        host=host.toLowerCase(Locale.ROOT);
        if(!(host.endsWith("fbcdn.net")||host.endsWith("cdninstagram.com")||host.endsWith("instagram.com")))return;
        if(isVideo(raw)) add(raw,"video","فيديو تم اكتشافه أثناء التشغيل");
        else if(isImage(raw)) add(raw,"image","صورة من صفحة إنستغرام");
    }

    void captureDom(WebView web) {
        if(web==null){toast("افتح المنشور أو الريلز أولًا");return;}
        ua=web.getSettings().getUserAgentString();
        String current=web.getUrl();
        if(current!=null && current.startsWith("https://"))page=current;
        // WebView's intercept callback doesn't receive blob: URLs, hence also examine actual DOM.
        String js="(function(){let a=[],seen=new Set();function put(url,kind,description){if(!url||!url.startsWith('https://')||seen.has(url))return;seen.add(url);a.push({url:url,kind:kind,desc:description||''});}"+
           "document.querySelectorAll('video').forEach((v,i)=>{put(v.currentSrc,'video','الفيديو '+(i+1));put(v.src,'video','الفيديو '+(i+1));v.querySelectorAll('source').forEach(s=>put(s.src,'video','مصدر الفيديو'));});"+
           "document.querySelectorAll('img').forEach((v,i)=>{if(v.naturalWidth>=280&&v.naturalHeight>=280)put(v.currentSrc||v.src,'image','صورة '+v.naturalWidth+'×'+v.naturalHeight);});"+
           "document.querySelectorAll('meta[property=\\\"og:video\\\"],meta[property=\\\"og:video:url\\\"]').forEach(v=>put(v.content,'video','فيديو الصفحة'));"+
           "document.querySelectorAll('meta[property=\\\"og:image\\\"]').forEach(v=>put(v.content,'image','صورة المنشور'));"+
           "return JSON.stringify(a.slice(0,150));})()";
        web.evaluateJavascript(js, response -> {
            try{
                JSONArray arr=new JSONArray(new JSONArray("["+response+"]").getString(0));
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.getJSONObject(i);
                    add(o.optString("url"),o.optString("kind"),o.optString("desc"));
                }
            }catch(Exception ignored){}
            showPicker();
        });
    }

    private void toast(String msg){activity.runOnUiThread(()->Toast.makeText(activity,msg,Toast.LENGTH_LONG).show());}

    private void showPicker() {
        final ArrayList<Item> list;
        synchronized(detected){list=new ArrayList<>(detected.values());}
        // Prefer video and visible page media; omit ultra-small tracker-like URLs.
        Collections.reverse(list);
        if(list.isEmpty()) {
            new AlertDialog.Builder(activity)
               .setTitle("لا توجد ملفات قابلة للحفظ")
               .setMessage("شغّل الريلز أو افتح المنشور وانتظر ظهور الصورة أو الفيديو، ثم اضغط استخراج مرة أخرى. بعض وسائط إنستغرام تكون بصيغة blob أو محمية ولا يمكن استخراج رابط ملفها.")
               .setPositiveButton("حسنًا",null).setNeutralButton("التنزيلات", (d,w)->showHistory()).show();
            return;
        }
        final String[] titles=new String[list.size()];
        for(int i=0;i<list.size();i++){
            Item x=list.get(i);
            titles[i]=(x.kind.equals("video")?"🎬 فيديو ":"🖼 صورة ")+(i+1)+" · "+(x.desc.length()>42?x.desc.substring(0,42):x.desc);
        }
        new AlertDialog.Builder(activity).setTitle("اختر ملفًا لتنزيله ("+list.size()+")")
           .setItems(titles,(dialog,idx)->{
               Item x=list.get(idx);
               new AlertDialog.Builder(activity).setTitle(x.kind.equals("video")?"حفظ الفيديو":"حفظ الصورة")
                  .setMessage("هل تريد حفظ الملف في معرض الصور بالجهاز؟")
                  .setPositiveButton("⬇ حفظ في الجهاز",(d,w)->downloadDirect(x.url,x.kind))
                  .setNeutralButton("نسخ الرابط",(d,w)->{
                      android.content.ClipboardManager cm=(android.content.ClipboardManager)activity.getSystemService(Context.CLIPBOARD_SERVICE);
                      cm.setPrimaryClip(android.content.ClipData.newPlainText("media",x.url));toast("تم نسخ رابط الملف");
                  }).setNegativeButton("رجوع",(d,w)->showPicker()).show();
           }).setNeutralButton("تنزيلاتي",(d,w)->showHistory())
           .setNegativeButton("إلغاء",null).show();
    }

    void downloadDirect(String url){
        String kind=isVideo(url)?"video":"image";
        downloadDirect(url,kind);
    }
    private void downloadDirect(String url,String kind){
        if(!validHttps(url)){toast("الرابط المباشر غير صالح");return;}
        if(Build.VERSION.SDK_INT<29 && activity.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            pendingUrl=url;pendingKind=kind;
            activity.requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 481);
            return;
        }
        startSave(url,kind);
    }
    void permissionResult(int requestCode,int[] grants){
        if(requestCode!=481)return;
        if(grants.length>0 && grants[0]==PackageManager.PERMISSION_GRANTED && pendingUrl!=null)startSave(pendingUrl,pendingKind);
        else toast("لا يمكن حفظ الملفات بدون إذن التخزين");
        pendingUrl=null;pendingKind=null;
    }

    private void startSave(String raw,String kind){
        final boolean video="video".equals(kind);
        final AtomicBoolean cancelled=new AtomicBoolean(false);
        ProgressDialog dialog=new ProgressDialog(activity);
        dialog.setTitle(video?"تنزيل الفيديو":"تنزيل الصورة");
        dialog.setMessage("جار تنزيل الملف وحفظه في المعرض...");
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        dialog.setOnCancelListener(d->cancelled.set(true));
        dialog.show();
        final String referer=page;
        final String userAgent=ua;
        new Thread(()->{
            Uri inserted=null;
            File oldFile=null;
            HttpURLConnection connection=null;
            try {
                URL next=new URL(raw);
                for(int redirect=0; redirect<6;redirect++){
                    connection=(HttpURLConnection)next.openConnection();
                    connection.setConnectTimeout(16000);
                    connection.setReadTimeout(25000);
                    connection.setInstanceFollowRedirects(false);
                    connection.setRequestProperty("User-Agent",userAgent);
                    connection.setRequestProperty("Referer",referer);
                    connection.setRequestProperty("Accept","image/avif,image/webp,image/*,video/*,*/*;q=0.8");
                    String cookies=CookieManager.getInstance().getCookie(next.toString());
                    if(cookies!=null&&!cookies.isEmpty())connection.setRequestProperty("Cookie",cookies);
                    int status=connection.getResponseCode();
                    if(status==301||status==302||status==303||status==307||status==308){
                        String loc=connection.getHeaderField("Location");
                        if(loc==null)throw new Exception("رابط تحويل غير صالح");
                        URL candidate=new URL(next,loc);
                        if(!validHttps(candidate.toString()))throw new Exception("تحويل غير آمن");
                        connection.disconnect();connection=null;
                        next=candidate;
                        continue;
                    }
                    if(status>=400)throw new Exception("السيرفر رفض تنزيل الملف (HTTP "+status+")");
                    break;
                }
                if(connection==null)throw new Exception("تعذر الاتصال");
                String mime=connection.getContentType();
                if(mime!=null)mime=mime.toLowerCase(Locale.ROOT).split(";")[0].trim();
                if(mime==null||mime.isEmpty()||mime.equals("application/octet-stream")){
                    mime=video?"video/mp4":"image/jpeg";
                }
                if(mime.startsWith("text/")||mime.contains("json")||mime.contains("html"))throw new Exception("الرابط أعاد صفحة وليس ملف صورة أو فيديو");
                if(!mime.startsWith("image/")&&!mime.startsWith("video/"))throw new Exception("نوع الملف غير مدعوم: "+mime);
                boolean actualVideo=mime.startsWith("video/");
                long expected=connection.getContentLengthLong();
                if(expected>MAX_BYTES)throw new Exception("الملف كبير جدًا");
                final String ext=mime.equals("image/png")?"png":mime.equals("image/webp")?"webp":mime.equals("image/gif")?"gif":mime.equals("video/quicktime")?"mov":actualVideo?"mp4":"jpg";
                String date=new SimpleDateFormat("yyyyMMdd_HHmmss",Locale.US).format(new Date());
                String filename="InstaTools_"+date+"_"+(System.currentTimeMillis()%1000)+"."+ext;
                final String folder=actualVideo?Environment.DIRECTORY_MOVIES:Environment.DIRECTORY_PICTURES;
                OutputStream output;
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues v=new ContentValues();
                    v.put(MediaStore.MediaColumns.DISPLAY_NAME,filename);
                    v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
                    v.put(MediaStore.MediaColumns.RELATIVE_PATH,folder+"/InstaTools");
                    v.put(MediaStore.MediaColumns.IS_PENDING,1);
                    Uri collection=actualVideo?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                    inserted=activity.getContentResolver().insert(collection,v);
                    if(inserted==null)throw new Exception("تعذر إنشاء ملف في المعرض");
                    output=activity.getContentResolver().openOutputStream(inserted);
                }else {
                    File target=new File(Environment.getExternalStoragePublicDirectory(folder),"InstaTools");
                    if(!target.exists()&&!target.mkdirs())throw new Exception("تعذر إنشاء مجلد InstaTools");
                    oldFile=new File(target,filename);
                    output=new FileOutputStream(oldFile);
                }
                if(output==null)throw new Exception("تعذر فتح الملف");
                try(InputStream input=connection.getInputStream();OutputStream out=output){
                    byte[] buffer=new byte[65536];
                    long total=0;
                    while(true){
                        if(cancelled.get())throw new Exception("ألغيت العملية");
                        int count=input.read(buffer);
                        if(count<0)break;
                        total+=count;
                        if(total>MAX_BYTES)throw new Exception("الملف يتجاوز الحد المسموح");
                        out.write(buffer,0,count);
                        final long current=total;
                        if(expected>0){
                            activity.runOnUiThread(()->{if(dialog.isShowing()){dialog.setIndeterminate(false);dialog.setMax(100);dialog.setProgress((int)(current*100/expected));}});
                        }
                    }
                    if(total==0)throw new Exception("الملف فارغ");
                }
                final Uri savedUri;
                if(Build.VERSION.SDK_INT>=29){
                    ContentValues done=new ContentValues();done.put(MediaStore.MediaColumns.IS_PENDING,0);
                    activity.getContentResolver().update(inserted,done,null,null);
                    savedUri=inserted;
                }else{
                    MediaScannerConnection.scanFile(activity,new String[]{oldFile.getAbsolutePath()},new String[]{mime},null);
                    savedUri=Uri.fromFile(oldFile);
                }
                record(filename,kind,savedUri.toString());
                activity.runOnUiThread(()->{
                    dialog.dismiss();
                    new AlertDialog.Builder(activity).setTitle("✅ تم الحفظ بنجاح")
                        .setMessage("تم حفظ "+filename+" في المعرض داخل مجلد InstaTools.")
                        .setPositiveButton("فتح الملف",(d,w)->openMedia(savedUri,actualVideo))
                        .setNegativeButton("تم",null).show();
                });
            }catch(Exception e){
                if(inserted!=null)activity.getContentResolver().delete(inserted,null,null);
                if(oldFile!=null&&oldFile.exists())oldFile.delete();
                String message=e.getMessage()==null?"خطأ اتصال":e.getMessage();
                activity.runOnUiThread(()->{dialog.dismiss();toast("فشل التنزيل: "+message);});
            }finally {if(connection!=null)connection.disconnect();}
        },"instatools-save").start();
    }
    private void openMedia(Uri uri,boolean video){
        try{
            Intent i=new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri,video?"video/*":"image/*");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(i);
        }catch(Exception e){toast("افتح الملف من معرض الصور");}
    }
    private void record(String name,String kind,String uri){
        SharedPreferences sp=activity.getSharedPreferences("instatools",Context.MODE_PRIVATE);
        try{
            JSONArray array=new JSONArray(sp.getString(HISTORY,"[]"));
            JSONObject item=new JSONObject();item.put("name",name);item.put("kind",kind);item.put("uri",uri);item.put("time",System.currentTimeMillis());
            JSONArray newer=new JSONArray();newer.put(item);
            for(int n=0;n<Math.min(array.length(),100);n++)newer.put(array.get(n));
            sp.edit().putString(HISTORY,newer.toString()).apply();
        }catch(Exception ignored){}
    }
    void showHistory(){
        SharedPreferences sp=activity.getSharedPreferences("instatools",Context.MODE_PRIVATE);
        final JSONArray arr;
        try {arr=new JSONArray(sp.getString(HISTORY,"[]"));}catch(Exception e){toast("تعذر فتح التنزيلات");return;}
        if(arr.length()==0){
            new AlertDialog.Builder(activity).setTitle("تنزيلاتي 📥").setMessage("لا توجد ملفات محفوظة بعد. افتح منشورًا أو ريلز واضغط «استخراج وتنزيل» ثم اختر الصورة أو الفيديو.")
                .setPositiveButton("حسنًا",null).show();
            return;
        }
        String[] names=new String[arr.length()];
        for(int i=0;i<arr.length();i++){
            JSONObject o=arr.optJSONObject(i);
            names[i]=(o!=null && o.optString("kind").equals("video")?"🎬 ":"🖼 ")+(o!=null?o.optString("name"):"ملف");
        }
        new AlertDialog.Builder(activity).setTitle("ملفاتي المحفوظة ("+names.length+")")
            .setItems(names,(d,index)->{
                JSONObject o=arr.optJSONObject(index);
                if(o!=null)openMedia(Uri.parse(o.optString("uri")),o.optString("kind").equals("video"));
            }).setNegativeButton("إغلاق",null).show();
    }
}