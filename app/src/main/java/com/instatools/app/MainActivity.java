package com.instatools.app;

import android.app.Activity;
import android.app.DownloadManager;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.webkit.*;
import android.widget.*;
import org.json.JSONArray;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    private FrameLayout root;
    private WebView home;
    private WebView remote;
    private LinearLayout toolbar;
    private ValueCallback<Uri[]> filePicker;
    private static final int FILE_CODE = 704;
    private static final String INSTAGRAM = "instagram.com";

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(15,16,26));
        getWindow().setNavigationBarColor(Color.rgb(15,16,26));
        root = new FrameLayout(this);
        home = new WebView(this);
        home.setBackgroundColor(Color.rgb(15,16,26));
        home.getSettings().setJavaScriptEnabled(true);
        home.getSettings().setDomStorageEnabled(true);
        home.getSettings().setAllowFileAccess(true);
        home.addJavascriptInterface(new Bridge(), "App");
        home.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePicker != null) filePicker.onReceiveValue(null);
                filePicker = cb;
                try {
                    Intent i = params.createIntent();
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    startActivityForResult(i, FILE_CODE);
                    return true;
                } catch (Exception e) { filePicker = null; toast("تعذر فتح الملفات"); return false; }
            }
        });
        root.addView(home,new FrameLayout.LayoutParams(-1,-1));
        setContentView(root);
        home.loadUrl("file:///android_asset/index.html");
        Intent intent=getIntent();
        if(intent!=null && Intent.ACTION_SEND.equals(intent.getAction())) {
            String shared=intent.getStringExtra(Intent.EXTRA_TEXT);
            if(shared!=null) home.setWebViewClient(new WebViewClient(){
                @Override public void onPageFinished(WebView v,String u){
                    home.evaluateJavascript("window.receiveShared("+org.json.JSONObject.quote(shared)+")",null);
                    home.setWebViewClient(new WebViewClient());
                }
            });
        }
    }
    @Override protected void onActivityResult(int code,int result,Intent data){
        super.onActivityResult(code,result,data);
        if(code == FILE_CODE && filePicker!=null) {
            filePicker.onReceiveValue(result==RESULT_OK && data!=null ?
                WebChromeClient.FileChooserParams.parseResult(result,data):null);
            filePicker=null;
        }
    }
    private void toast(String s){ runOnUiThread(()->Toast.makeText(this,s,Toast.LENGTH_SHORT).show()); }
    private boolean safeInstagram(String url) {
        try { URL u = new URL(url); String h = u.getHost().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getProtocol()) && (h.equals(INSTAGRAM) || h.endsWith("."+INSTAGRAM));
        }catch(Exception e){return false;}
    }
    private boolean safeMedia(String url){
        try { URL u=new URL(url); String p=u.getPath().toLowerCase(Locale.ROOT);
            return "https".equalsIgnoreCase(u.getProtocol()) && (
                p.endsWith(".jpg")||p.endsWith(".jpeg")||p.endsWith(".png")||p.endsWith(".webp")||
                p.endsWith(".mp4")||p.endsWith(".mov")||p.endsWith(".m4v")||p.endsWith(".gif")||
                p.endsWith(".heic"));
        }catch(Exception e){return false;}
    }
    private void download(String url) {
        if(!safeMedia(url)){toast("يلزم رابط ملف صورة أو فيديو مباشر، وليس رابط منشور");return;}
        try{
            String fname=Uri.parse(url).getLastPathSegment();
            if(fname==null || fname.isEmpty())fname="instatools-media";
            DownloadManager.Request rq=new DownloadManager.Request(Uri.parse(url));
            rq.setTitle(fname);
            rq.setDescription("InstaTools");
            rq.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            rq.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS,"InstaTools/"+fname);
            DownloadManager dm=(DownloadManager)getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(rq);toast("بدأ التنزيل إلى Downloads/InstaTools");
        }catch(Exception e){toast("تعذر بدء التنزيل: "+e.getMessage());}
    }
    private void openInstagram(String url){
        if(!safeInstagram(url)){toast("هذا الرابط ليس من إنستغرام");return;}
        runOnUiThread(()->{
            if(remote!=null){root.removeView(remote);remote.destroy();remote=null;}
            if(toolbar!=null){root.removeView(toolbar);toolbar=null;}
            remote=new WebView(this);
            remote.setBackgroundColor(Color.rgb(15,16,26));
            WebSettings s=remote.getSettings();
            s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setBuiltInZoomControls(true);s.setDisplayZoomControls(false);
            s.setAllowFileAccess(false);s.setAllowContentAccess(false);s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(remote,true);
            remote.setWebViewClient(new WebViewClient(){
              @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest request){
                Uri u=request.getUrl();String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);
                if(!"https".equalsIgnoreCase(u.getScheme()))return true;
                if(host.equals("instagram.com")||host.endsWith(".instagram.com")||
                   host.equals("facebook.com")||host.endsWith(".facebook.com"))return false;
                try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception e){}
                return true;
              }
              @Override public void onReceivedError(WebView v,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame())toast("تحقق من الإنترنت أو افتح الصفحة في المتصفح الخارجي");
              }
            });
            root.addView(remote,new FrameLayout.LayoutParams(-1,-1));
            toolbar = new LinearLayout(this);
            toolbar.setOrientation(LinearLayout.HORIZONTAL);toolbar.setGravity(Gravity.CENTER_VERTICAL);
            toolbar.setBackgroundColor(Color.rgb(27,27,43));toolbar.setPadding(dp(7),dp(3),dp(7),dp(3));
            addButton("⌂ الأدوات",()->closeRemote());
            addButton("↶",()->{if(remote!=null&&remote.canGoBack())remote.goBack();});
            addButton("⧉ نسخ التعليقات",()->copyVisibleComments());
            addButton("↗",()->{if(remote!=null){ try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(remote.getUrl())));}catch(Exception e){} }});
            FrameLayout.LayoutParams barp=new FrameLayout.LayoutParams(-1,dp(58),Gravity.BOTTOM);
            root.addView(toolbar,barp);
            FrameLayout.LayoutParams rlp=(FrameLayout.LayoutParams)remote.getLayoutParams();rlp.bottomMargin=dp(58);remote.setLayoutParams(rlp);
            remote.loadUrl(url);
        });
    }
    private void addButton(String title,Runnable click){
        TextView b=new TextView(this);b.setText(title);b.setTextColor(Color.WHITE);b.setGravity(Gravity.CENTER);
        b.setTextSize(12);b.setPadding(dp(4),dp(8),dp(4),dp(8));
        toolbar.addView(b,new LinearLayout.LayoutParams(0,-1,1));
        b.setOnClickListener(v->click.run());
    }
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+0.5f);}
    private void closeRemote(){
        if(remote!=null){root.removeView(remote);remote.destroy();remote=null;}
        if(toolbar!=null){root.removeView(toolbar);toolbar=null;}
    }
    private void copyVisibleComments(){
        if(remote==null)return;
        String js="(function(){let a=Array.from(document.querySelectorAll('article ul li, article [role=listitem], ul li')).map(x=>x.innerText.trim()).filter(x=>x.length>2&&x.length<2000);return Array.from(new Set(a)).slice(0,150).join('\\n\\n')})()";
        remote.evaluateJavascript(js, result->{
            try{
                String text=new JSONArray("["+result+"]").getString(0);
                if(text==null||text.trim().isEmpty()){toast("لم أجد تعليقات ظاهرة. افتح التعليقات أولاً");return;}
                ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("comments",text));
                toast("تم نسخ النصوص الظاهرة. راجعها قبل المشاركة");
            }catch(Exception e){toast("تعذر نسخ التعليقات من هذه الصفحة");}
        });
    }
    @Override public void onBackPressed(){
        if(remote!=null){if(remote.canGoBack())remote.goBack();else closeRemote();}
        else if(home.canGoBack())home.goBack();else super.onBackPressed();
    }
    @Override protected void onDestroy(){closeRemote();if(home!=null)home.destroy();super.onDestroy();}
    public class Bridge {
        @JavascriptInterface public void openInstagram(String url){MainActivity.this.openInstagram(url);}
        @JavascriptInterface public void downloadDirect(String url){runOnUiThread(()->download(url));}
        @JavascriptInterface public void copy(String text){
            ((android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("InstaTools",text));toast("تم النسخ");
        }
        @JavascriptInterface public String clipboard(){
            android.content.ClipboardManager c=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
            if(c.hasPrimaryClip()&&c.getPrimaryClip().getItemCount()>0){
                CharSequence x=c.getPrimaryClip().getItemAt(0).coerceToText(MainActivity.this);
                return x==null?"":x.toString();
            }return "";
        }
        @JavascriptInterface public void share(String text){
            runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);
                startActivity(Intent.createChooser(i,"مشاركة عبر"));});
        }
        @JavascriptInterface public void toast(String text){MainActivity.this.toast(text);}
    }
}
