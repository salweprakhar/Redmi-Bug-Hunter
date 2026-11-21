package dev.redmi.bughunter;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class TrafficProxyService extends Service {
    public static final int PORT=8080;
    private static final String CHANNEL="traffic_inspector";
    private static final ArrayDeque<String> EVENTS=new ArrayDeque<>();
    private static volatile boolean running=false;
    private volatile boolean stopping=false;
    private String authorizedHost="";
    private ServerSocket server;
    private ExecutorService clients;
    private final Set<Socket> activeSockets=ConcurrentHashMap.newKeySet();

    @Override public void onCreate(){super.onCreate();clients=Executors.newCachedThreadPool();}
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(intent==null)return START_NOT_STICKY;String scope=intent.getStringExtra("authorized_host");
        if(scope==null||!scope.matches("[A-Za-z0-9.-]+")||scope.startsWith(".")||scope.endsWith(".")){addEvent("Start rejected: invalid authorized host");stopSelf();return START_NOT_STICKY;}
        authorizedHost=scope.toLowerCase(Locale.ROOT);startForeground(73,notification());
        if(!running){running=true;stopping=false;clients.execute(this::listen);}return START_NOT_STICKY;
    }
    private Notification notification(){
        NotificationManager manager=(NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        if(Build.VERSION.SDK_INT>=26)manager.createNotificationChannel(new NotificationChannel(CHANNEL,"Traffic inspector",NotificationManager.IMPORTANCE_LOW));
        Intent open=new Intent(this,MainActivity.class);PendingIntent pending=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("Traffic inspector active").setContentText("Scope: "+authorizedHost+" · localhost:"+PORT).setContentIntent(pending).setOngoing(true).build();
    }
    private void listen(){
        try{server=new ServerSocket(PORT,20,InetAddress.getByName("127.0.0.1"));addEvent("Inspector started · scope "+authorizedHost+" · 127.0.0.1:"+PORT);while(!stopping){Socket client=server.accept();clients.execute(()->handle(client));}}
        catch(Exception e){if(!stopping)addEvent("Inspector stopped after error: "+safe(e));}
        finally{running=false;stopSelf();}
    }
    private void handle(Socket client){
        long started=System.currentTimeMillis();String target="unknown";AtomicLong up=new AtomicLong(),down=new AtomicLong();
        activeSockets.add(client);try(Socket local=client){local.setSoTimeout(15000);BufferedInputStream input=new BufferedInputStream(local.getInputStream());byte[] request=readHeaders(input,16384);String text=new String(request,StandardCharsets.ISO_8859_1);String first=text.split("\\r?\\n",2)[0];String[] parts=first.split(" ");
            if(parts.length<3||!"CONNECT".equalsIgnoreCase(parts[0])){reject(local,405,"CONNECT tunneling only");addEvent("Rejected non-CONNECT request");return;}
            target=parts[1];int colon=target.lastIndexOf(':');if(colon<=0){reject(local,400,"Host and port required");return;}String host=target.substring(0,colon).toLowerCase(Locale.ROOT);int port=Integer.parseInt(target.substring(colon+1));
            if(!host.equals(authorizedHost)){reject(local,403,"Host outside authorized scope");addEvent("Blocked out-of-scope host: "+host);return;}
            if(port!=443&&port!=8443){reject(local,403,"Only TLS ports 443 and 8443 are allowed");addEvent("Blocked port for "+host+": "+port);return;}
            try(Socket remote=new Socket()){activeSockets.add(remote);remote.connect(new InetSocketAddress(host,port),10000);remote.setSoTimeout(30000);OutputStream localOut=local.getOutputStream();localOut.write("HTTP/1.1 200 Connection Established\r\nProxy-Agent: RedmiBugHunter/0.6\r\n\r\n".getBytes(StandardCharsets.ISO_8859_1));localOut.flush();addEvent("TLS tunnel opened: "+host+":"+port);
                Thread outbound=new Thread(()->pump(input,remote,up),"proxy-up");Thread inbound=new Thread(()->pumpRemote(remote,localOut,down),"proxy-down");outbound.start();inbound.start();outbound.join();try{remote.shutdownOutput();}catch(Exception ignored){}inbound.join();}
        }catch(Exception e){addEvent("Tunnel error for "+target+": "+safe(e));}
        finally{activeSockets.removeIf(Socket::isClosed);addEvent("Tunnel closed: "+target+" · up "+up.get()+" B · down "+down.get()+" B · "+(System.currentTimeMillis()-started)+" ms");}
    }
    private byte[] readHeaders(InputStream input,int limit)throws Exception{ByteArrayOutputStream data=new ByteArrayOutputStream();int state=0,value;while(data.size()<limit&&(value=input.read())!=-1){data.write(value);if((state==0||state==2)&&value=='\r')state++;else if((state==1||state==3)&&value=='\n')state++;else state=0;if(state==4)return data.toByteArray();}throw new IllegalArgumentException("Proxy headers too large or incomplete");}
    private void pump(InputStream input,Socket remote,AtomicLong count){try{OutputStream output=remote.getOutputStream();copy(input,output,count);}catch(Exception ignored){}}
    private void pumpRemote(Socket remote,OutputStream output,AtomicLong count){try{copy(remote.getInputStream(),output,count);}catch(Exception ignored){}}
    private void copy(InputStream input,OutputStream output,AtomicLong count)throws Exception{byte[] buffer=new byte[16384];int read;while((read=input.read(buffer))!=-1){output.write(buffer,0,read);output.flush();count.addAndGet(read);}}
    private void reject(Socket socket,int status,String message)throws Exception{byte[] body=message.getBytes(StandardCharsets.UTF_8);String head="HTTP/1.1 "+status+" Error\r\nContent-Type: text/plain\r\nContent-Length: "+body.length+"\r\nConnection: close\r\n\r\n";OutputStream output=socket.getOutputStream();output.write(head.getBytes(StandardCharsets.ISO_8859_1));output.write(body);output.flush();}
    private static String safe(Exception e){String value=e.getMessage();return value==null?e.getClass().getSimpleName():value;}
    private static void addEvent(String value){SimpleDateFormat format=new SimpleDateFormat("HH:mm:ss",Locale.US);format.setTimeZone(TimeZone.getDefault());synchronized(EVENTS){EVENTS.addFirst(format.format(new Date())+"  "+value);while(EVENTS.size()>100)EVENTS.removeLast();}}
    public static boolean isRunning(){return running;}
    public static String eventText(){synchronized(EVENTS){if(EVENTS.isEmpty())return "No captured connection metadata yet.";StringBuilder text=new StringBuilder();for(String event:EVENTS)text.append(event).append('\n');return text.toString().trim();}}
    public static void clearEvents(){synchronized(EVENTS){EVENTS.clear();}}
    @Override public void onDestroy(){stopping=true;running=false;try{if(server!=null)server.close();}catch(Exception ignored){}for(Socket socket:activeSockets)try{socket.close();}catch(Exception ignored){}activeSockets.clear();if(clients!=null)clients.shutdownNow();addEvent("Inspector stopped");super.onDestroy();}
    @Override public IBinder onBind(Intent intent){return null;}
}
