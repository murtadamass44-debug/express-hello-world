package com.loj.iptv;
import android.content.Context;
import org.json.JSONObject;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
public class LojServer {
    final Context ctx;
    ServerSocket sock;
    ExecutorService pool=Executors.newFixedThreadPool(8);
    volatile boolean running=false;
    LojServer(Context ctx){this.ctx=ctx;}
    int start() throws IOException{
        sock=new ServerSocket(0,16,InetAddress.getByName("127.0.0.1"));running=true;
        Thread t=new Thread(()->{
            while(running){
                try{
                    Socket s=sock.accept();s.setSoTimeout(25000);
                    pool.execute(()->serve(s));
                }catch(IOException e){if(running)android.util.Log.e("LOJ","Local server stopped");}
            }
        },"loj-local-server");
        t.setDaemon(true);t.start();return sock.getLocalPort();
    }
    void close(){running=false;try{sock.close();}catch(Exception ignored){}pool.shutdownNow();}
    void reply(OutputStream out,int status,String type,byte[] data)throws IOException{
        String header="HTTP/1.1 "+status+" "+(status==200?"OK":"Error")+"\r\n"
             +"Content-Type: "+type+"\r\nContent-Length: "+data.length+"\r\n"
             +"Cache-Control: no-store\r\nConnection: close\r\nX-Content-Type-Options: nosniff\r\n\r\n";
        out.write(header.getBytes(StandardCharsets.US_ASCII));out.write(data);out.flush();
    }
    byte[] readMax(InputStream in,int limit)throws IOException{
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;
        while((n=in.read(b))>=0){if(out.size()+n>limit)throw new IOException("قائمة كبيرة جداً");out.write(b,0,n);}
        return out.toByteArray();
    }
    void serve(Socket socket){
        try(Socket s=socket;InputStream raw=s.getInputStream();OutputStream out=s.getOutputStream()){
            BufferedInputStream in=new BufferedInputStream(raw);
            ByteArrayOutputStream head=new ByteArrayOutputStream();
            int cur,tail=0;
            while(head.size()<16384&&(cur=in.read())>=0){
                head.write(cur);tail=((tail<<8)|cur);
                if(tail==0x0d0a0d0a)break;
            }
            String headers=head.toString("UTF-8");String[] lines=headers.split("\r\n");
            if(lines.length<1)return;
            String[] first=lines[0].split(" ");
            if(first.length<2)return;
            String method=first[0],path=first[1].split("\\?")[0];
            int len=0;
            for(String line:lines){
                if(line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")){
                    len=Integer.parseInt(line.substring(line.indexOf(':')+1).trim());
                }
            }
            if(method.equals("POST")&&path.equals("/api/catalog")){
                if(len<1||len>8192){reply(out,400,"application/json",json(false,"","طلب غير صالح"));return;}
                byte[] buf=new byte[len];int p=0;while(p<len){int n=in.read(buf,p,len-p);if(n<0)break;p+=n;}
                if(p!=len)throw new IOException("Unfinished request");
                try{
                    String target=new JSONObject(new String(buf,StandardCharsets.UTF_8)).getString("url");
                    String result=fetchRemote(target);
                    reply(out,200,"application/json; charset=utf-8",json(true,result,""));
                }catch(Exception ex){
                    reply(out,422,"application/json; charset=utf-8",json(false,"",ex.getMessage()==null?"فشل الاتصال":ex.getMessage()));
                }
                return;
            }
            if(!method.equals("GET")){reply(out,405,"text/plain",new byte[0]);return;}
            if(path.equals("/"))path="/index.html";
            if(!path.matches("/[a-zA-Z0-9._-]+")){reply(out,404,"text/plain",new byte[0]);return;}
            String ext=path.endsWith(".css")?"text/css":path.endsWith(".js")?"application/javascript":path.endsWith(".svg")?"image/svg+xml":"text/html";
            try(InputStream src=ctx.getAssets().open("web"+path)){
                reply(out,200,ext+"; charset=utf-8",readMax(src,1200000));
            }catch(FileNotFoundException e){reply(out,404,"text/plain",new byte[0]);}
        }catch(Exception ignored){}
    }
    byte[] json(boolean ok,String content,String error){
        try{
            JSONObject obj=new JSONObject();obj.put("ok",ok);obj.put("content",content);obj.put("error",error);
            return obj.toString().getBytes(StandardCharsets.UTF_8);
        }catch(Exception e){return "{\"ok\":false}".getBytes(StandardCharsets.UTF_8);}
    }
    String fetchRemote(String target)throws Exception {
        URL url=new URL(target);
        if(!url.getProtocol().equals("http")&&!url.getProtocol().equals("https"))throw new IOException("بروتوكول غير مدعوم");
        if(url.getUserInfo()!=null)throw new IOException("الرابط يحتوي بيانات غير مسموحة");
        String host=url.getHost().toLowerCase(java.util.Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".localhost")||host.endsWith(".local"))throw new IOException("سيرفر محلي غير مسموح");
        for(InetAddress ip:InetAddress.getAllByName(host)){
            if(ip.isAnyLocalAddress()||ip.isLoopbackAddress()||ip.isSiteLocalAddress()||ip.isLinkLocalAddress()||ip.isMulticastAddress())throw new IOException("عنوان داخلي غير مسموح");
            byte[] addr=ip.getAddress();
            if(addr.length==4){int a=addr[0]&255,b=addr[1]&255;if(a==100&&b>=64&&b<128)throw new IOException("عنوان داخلي غير مسموح");}
        }
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setConnectTimeout(13000);c.setReadTimeout(16000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","LOJ-IPTV/1.0 Android");
        c.setRequestProperty("Accept","application/json, text/plain, */*");
        try{
            int status=c.getResponseCode();
            if(status<200||status>=300)throw new IOException("HTTP "+status);
            try(InputStream in=c.getInputStream()){return new String(readMax(in,20000000),StandardCharsets.UTF_8);}
        }finally{c.disconnect();}
    }
}
