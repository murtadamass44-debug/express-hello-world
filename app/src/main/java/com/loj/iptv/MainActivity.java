package com.loj.iptv;
import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.view.Gravity;
public class MainActivity extends Activity {
    WebView web;
    LojServer server;
    FrameLayout frame;
    View full;
    WebChromeClient.CustomViewCallback fullCb;
    void hideFull(){
        if(full==null)return;
        frame.removeView(full); full=null;
        web.setVisibility(View.VISIBLE);
        if(fullCb!=null)fullCb.onCustomViewHidden();
        fullCb=null;
    }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        frame = new FrameLayout(this);
        frame.setBackgroundColor(Color.rgb(5,11,25));
        setContentView(frame);
        if(getPackageName().endsWith(".tv"))getWindow().getDecorView().setSystemUiVisibility(5894);
        try {
            server = new LojServer(this);
            final int port=server.start();
            web = new WebView(this);
            web.setBackgroundColor(Color.rgb(5,11,25));
            web.setFocusable(true);
            web.setFocusableInTouchMode(true);
            WebSettings set=web.getSettings();
            set.setJavaScriptEnabled(true);
            set.setDomStorageEnabled(true);
            set.setMediaPlaybackRequiresUserGesture(false);
            set.setAllowFileAccess(false);
            set.setAllowContentAccess(false);
            set.setBuiltInZoomControls(false);
            set.setJavaScriptCanOpenWindowsAutomatically(false);
            set.setDefaultTextEncodingName("UTF-8");
            web.setWebViewClient(new WebViewClient(){
                @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return !url.startsWith("http://127.0.0.1:"+port+"/");}
            });
            web.setWebChromeClient(new WebChromeClient(){
                @Override public void onShowCustomView(View v,CustomViewCallback cb){
                    if(full!=null){cb.onCustomViewHidden();return;}
                    full=v; fullCb=cb; web.setVisibility(View.GONE);
                    frame.addView(v,new FrameLayout.LayoutParams(-1,-1));
                }
                @Override public void onHideCustomView(){hideFull();}
            });
            frame.addView(web,new FrameLayout.LayoutParams(-1,-1));
            web.loadUrl("http://127.0.0.1:"+port+"/");
            web.requestFocus();
        } catch(Exception e) {
            TextView v=new TextView(this);v.setGravity(Gravity.CENTER);v.setTextSize(20);
            v.setText("LOJ IPTV\nتعذر فتح التطبيق. أعد تشغيله.");frame.addView(v);
        }
    }
    @Override public void onBackPressed(){
        if(full!=null){hideFull();return;}
        if(web==null){super.onBackPressed();return;}
        web.evaluateJavascript("window.lojBack ? window.lojBack() : false", value->{
            if(!"true".equals(value))runOnUiThread(()->finish());
        });
    }
    @Override public void onPause(){super.onPause();if(web!=null)web.onPause();}
    @Override public void onResume(){super.onResume();if(web!=null)web.onResume();}
    @Override protected void onDestroy(){
        if(web!=null){frame.removeView(web);web.stopLoading();web.destroy();web=null;}
        if(server!=null)server.close();
        super.onDestroy();
    }
}
