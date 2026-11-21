package dev.redmi.bughunter;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.KeyguardManager;
import android.os.Bundle;
import android.os.Build;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ArrayAdapter;
import android.util.Base64;
import android.text.Html;
import android.text.InputType;
import android.text.method.ScrollingMovementMethod;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
    private static final int NOTIFICATION_PERMISSION_REQUEST=730;
    private static final int BG=Color.rgb(9,16,29), PANEL=Color.rgb(22,34,53), WHITE=Color.rgb(232,240,250);
    private static final int MUTED=Color.rgb(158,177,203), CYAN=Color.rgb(76,203,222), RED=Color.rgb(255,143,143);
    private EditText urlInput, hostInput;
    private TextView statusView, reportView;
    private Button runButton, shareButton;
    private CheckBox permissionBox;
    private String reportJson="";
    private LinearLayout[] pages;
    private Button[] navigationButtons;
    private TextView deviceSummaryView;
    private EditText utilityInput;
    private TextView utilityOutput;
    private String utilityResult="";
    private EditText workUrlInput, workHostInput, workHeadersInput, workBodyInput;
    private Spinner workMethodInput;
    private CheckBox workPermissionBox, workRedirectBox;
    private TextView workStatusView, workResponseView;
    private Button workSendButton;
    private String workResponse="";
    private EditText trafficHostInput;
    private CheckBox trafficPermissionBox;
    private TextView trafficStatusView, trafficLogView;
    private EditText terminalInput;
    private TextView terminalOutput, terminalStatus;
    private ShellSession shellSession;
    private final StringBuilder terminalBuffer=new StringBuilder();
    private boolean terminalRenderScheduled=false;
    private FindingsStore findingsStore;
    private EditText findingTitleInput, findingTargetInput, findingEvidenceInput, findingRemediationInput;
    private Spinner findingSeverityInput;
    private TextView findingsStatusView, findingsListView;
    private EditText reconUrlInput, reconHostInput;
    private CheckBox reconPermissionBox;
    private TextView reconStatusView, reconReportView;
    private Button reconRunButton, reconShareButton;
    private ReconEngine reconEngine;
    private String reconReportJson="";
    private volatile boolean activityDestroyed=false;
    private int shellGeneration=0;
    private ScrollView appScroll;
    private static final int MAX_REQUEST_BYTES=65536, MAX_RESPONSE_BYTES=262144;

    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable background(int color, int radius) {
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private TextView label(String value,int size,int color,boolean strong) {
        TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color);
        if(strong) v.setTypeface(Typeface.DEFAULT,Typeface.BOLD); return v;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private void place(LinearLayout parent,View v,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);parent.addView(v,p);
    }
    private EditText field(String hint) {
        EditText e=new EditText(this); e.setSingleLine(true);e.setTextColor(WHITE);e.setHintTextColor(MUTED);
        e.setTextSize(14);e.setHint(hint);e.setPadding(dp(12),dp(12),dp(12),dp(12));e.setBackground(background(PANEL,10)); return e;
    }
    private Button button(String text,int color) {
        Button b=new Button(this);b.setAllCaps(false);b.setText(text);b.setTextColor(BG);b.setTextSize(14);b.setBackground(background(color,10));return b;
    }
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView sc=new ScrollView(this);appScroll=sc;sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(20),dp(30),dp(20),dp(28));sc.addView(root);setContentView(sc);
        findingsStore=new FindingsStore(this);
        place(root,label("REDMI BUG HUNTER",23,CYAN,true),4);
        place(root,label("Authorized security research toolkit",13,MUTED,false),8);
        LinearLayout navigation=column();
        String[] names={"Device","Audit","Recon","HTTP","Traffic","Terminal","Findings","Tools"};
        navigationButtons=new Button[names.length];
        for(int rowStart=0;rowStart<names.length;rowStart+=4){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            if(rowStart>0){LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=dp(6);navigation.addView(row,rowParams);}else navigation.addView(row);
            for(int i=rowStart;i<Math.min(rowStart+4,names.length);i++){
                final int page=i;Button item=button(names[i],i==0?CYAN:Color.rgb(195,216,241));
                item.setTextSize(11);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
                if(i>rowStart)params.leftMargin=dp(5);row.addView(item,params);navigationButtons[i]=item;
                item.setOnClickListener(v->showPage(page));
            }
        }
        place(root,navigation,20);
        pages=new LinearLayout[]{createDevicePage(),createAuditPage(),createReconPage(),createWorkbenchPage(),createTrafficPage(),createTerminalPage(),createFindingsPage(),createUtilityPage()};
        for(LinearLayout page:pages)place(root,page,18);
        showPage(0);
    }
    private LinearLayout createDevicePage(){
        LinearLayout page=column();
        place(page,label("Device security",19,WHITE,true),4);
        place(page,label("A local snapshot of basic security settings. This is guidance, not a certification.",12,MUTED,false),8);
        deviceSummaryView=label("",13,WHITE,false);deviceSummaryView.setPadding(dp(14),dp(15),dp(14),dp(15));deviceSummaryView.setBackground(background(PANEL,12));
        place(page,deviceSummaryView,14);refreshDeviceSummary();
        Button refresh=button("Refresh device status",CYAN);place(page,refresh,12);refresh.setOnClickListener(v->refreshDeviceSummary());
        Button security=button("Open Android security settings",Color.rgb(195,216,241));place(page,security,8);
        security.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_SECURITY_SETTINGS)));
        place(page,infoCard("Keep Android and apps updated. Use a strong screen lock, install apps only from sources you trust, and review app permissions regularly."),15);
        return page;
    }
    private void refreshDeviceSummary(){
        KeyguardManager keyguard=(KeyguardManager)getSystemService(KEYGUARD_SERVICE);
        boolean secureLock=keyguard!=null&&keyguard.isKeyguardSecure();
        int adb=Settings.Global.getInt(getContentResolver(),Settings.Global.ADB_ENABLED,0);
        String patch=Build.VERSION.SECURITY_PATCH;
        if(patch==null||patch.isEmpty())patch="Not reported by Android";
        deviceSummaryView.setText("Device: "+Build.MANUFACTURER+" "+Build.MODEL
                +"\nAndroid: "+Build.VERSION.RELEASE+" (API "+Build.VERSION.SDK_INT+")"
                +"\nSecurity patch: "+patch
                +"\nSecure screen lock: "+(secureLock?"Detected":"Not detected")
                +"\nUSB debugging: "+(adb==1?"Enabled":"Disabled")
                +"\n\nThis information is read locally and is not uploaded.");
    }
    private LinearLayout createAuditPage(){
        LinearLayout page=column();
        TextView tag=label("ONE HTTPS REQUEST  ·  NO REDIRECTS",12,CYAN,true);
        tag.setGravity(Gravity.CENTER_VERTICAL);tag.setPadding(dp(13),dp(12),dp(13),dp(12));tag.setBackground(background(PANEL,10));
        place(page,tag,4);
        place(page,label("Target URL",13,WHITE,true),20);
        urlInput=field("https://your-test-site.example/");place(page,urlInput,8);
        place(page,label("Authorized host (exact hostname)",13,WHITE,true),17);
        hostInput=field("your-test-site.example");place(page,hostInput,8);
        permissionBox=new CheckBox(this);permissionBox.setText("I own this host or have explicit permission to test it.");
        permissionBox.setTextColor(WHITE);permissionBox.setTextSize(13);permissionBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        place(page,permissionBox,18);
        runButton=button("Run security audit",CYAN);place(page,runButton,13);
        statusView=label("Ready. The scan makes one HTTPS GET request and never follows redirects.",12,MUTED,false);
        place(page,statusView,18);
        place(page,label("Security report",17,WHITE,true),25);
        reportView=label("No audit has been run yet.",12,MUTED,false);
        reportView.setTextIsSelectable(true);reportView.setPadding(dp(14),dp(15),dp(14),dp(15));reportView.setBackground(background(PANEL,12));
        place(page,reportView,10);
        shareButton=button("Share JSON report",Color.rgb(195,216,241));shareButton.setEnabled(false);place(page,shareButton,13);
        place(page,label("Informational checks only; verify findings manually. No brute force, exploit delivery, or credential collection.",12,MUTED,false),16);
        runButton.setOnClickListener(v->scan());shareButton.setOnClickListener(v->share());
        return page;
    }
    private LinearLayout createWorkbenchPage(){
        LinearLayout page=column();
        place(page,label("Authorized HTTP Workbench",19,WHITE,true),4);
        place(page,label("Send one controlled HTTPS request to an exact in-scope host and inspect the response.",12,MUTED,false),8);
        place(page,label("Method",13,WHITE,true),16);
        workMethodInput=new Spinner(this);
        String[] methods={"GET","POST","PUT","DELETE","HEAD","OPTIONS"};
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,methods){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){
                TextView view=(TextView)super.getView(position,convertView,parent);view.setTextColor(WHITE);view.setPadding(dp(12),dp(12),dp(12),dp(12));return view;
            }
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){
                TextView view=(TextView)super.getDropDownView(position,convertView,parent);view.setTextColor(Color.BLACK);view.setPadding(dp(12),dp(12),dp(12),dp(12));return view;
            }
        };
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);workMethodInput.setAdapter(adapter);workMethodInput.setBackground(background(PANEL,10));
        place(page,workMethodInput,7);
        place(page,label("HTTPS URL",13,WHITE,true),15);workUrlInput=field("https://api.example.com/v1/resource");place(page,workUrlInput,7);
        place(page,label("Authorized host (exact hostname)",13,WHITE,true),15);workHostInput=field("api.example.com");place(page,workHostInput,7);
        place(page,label("Request headers (one Name: value per line)",13,WHITE,true),15);
        workHeadersInput=field("Accept: application/json\nContent-Type: application/json");workHeadersInput.setSingleLine(false);workHeadersInput.setMinLines(3);workHeadersInput.setGravity(Gravity.TOP);place(page,workHeadersInput,7);
        place(page,label("Request body (maximum 64 KiB)",13,WHITE,true),15);
        workBodyInput=field("Optional body for POST, PUT, or DELETE");workBodyInput.setSingleLine(false);workBodyInput.setMinLines(4);workBodyInput.setGravity(Gravity.TOP);place(page,workBodyInput,7);
        workRedirectBox=new CheckBox(this);workRedirectBox.setText("Follow same-host HTTPS redirects (maximum 5)");workRedirectBox.setTextColor(WHITE);workRedirectBox.setTextSize(13);workRedirectBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));place(page,workRedirectBox,13);
        workPermissionBox=new CheckBox(this);workPermissionBox.setText("I own this host or have explicit permission to test it.");workPermissionBox.setTextColor(WHITE);workPermissionBox.setTextSize(13);workPermissionBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));place(page,workPermissionBox,7);
        workSendButton=button("Send one request",CYAN);place(page,workSendButton,12);workSendButton.setOnClickListener(v->sendWorkbenchRequest());
        workStatusView=label("Ready. No request history or secrets are stored.",12,MUTED,false);place(page,workStatusView,13);
        place(page,label("Response",17,WHITE,true),20);
        workResponseView=label("No request has been sent yet.",12,MUTED,false);workResponseView.setTextIsSelectable(true);workResponseView.setPadding(dp(14),dp(15),dp(14),dp(15));workResponseView.setBackground(background(PANEL,12));place(page,workResponseView,8);
        Button copy=button("Copy response",Color.rgb(195,216,241));place(page,copy,9);copy.setOnClickListener(v->copyWorkbenchResponse());
        place(page,label("Limits: HTTPS only, exact authorized host, standard port, one request at a time, 64 KiB request body, 256 KiB response preview.",12,MUTED,false),12);
        return page;
    }
    private LinearLayout createReconPage(){
        LinearLayout page=column();place(page,label("Authorized Recon and Site Map",19,WHITE,true),4);
        place(page,label("A bounded same-host HTTPS crawler for endpoint inventory and passive evidence collection.",12,MUTED,false),8);
        TextView limits=label("MAX 10 REQUESTS  ·  DEPTH 2  ·  ≥500 MS DELAY",12,CYAN,true);limits.setPadding(dp(13),dp(12),dp(13),dp(12));limits.setBackground(background(PANEL,10));place(page,limits,13);
        place(page,label("Starting HTTPS URL",13,WHITE,true),15);reconUrlInput=field("https://authorized.example/");place(page,reconUrlInput,7);
        place(page,label("Authorized host (exact hostname)",13,WHITE,true),13);reconHostInput=field("authorized.example");place(page,reconHostInput,7);
        reconPermissionBox=new CheckBox(this);reconPermissionBox.setText("I own this host or have explicit permission to crawl it.");reconPermissionBox.setTextColor(WHITE);reconPermissionBox.setTextSize(13);reconPermissionBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));place(page,reconPermissionBox,12);
        reconRunButton=button("Run bounded recon",CYAN);place(page,reconRunButton,10);reconRunButton.setOnClickListener(v->startRecon());Button cancel=button("Cancel recon",Color.rgb(195,216,241));place(page,cancel,7);cancel.setOnClickListener(v->cancelRecon());
        reconStatusView=label("Ready. Standard discovery checks include robots.txt, security.txt, and sitemap.xml within the same 10-request budget.",12,MUTED,false);place(page,reconStatusView,11);
        place(page,label("Site map report",17,WHITE,true),18);reconReportView=label("No recon has been run yet.",12,MUTED,false);reconReportView.setTextIsSelectable(true);reconReportView.setPadding(dp(14),dp(15),dp(14),dp(15));reconReportView.setBackground(background(PANEL,12));place(page,reconReportView,8);
        reconShareButton=button("Share recon JSON",Color.rgb(195,216,241));reconShareButton.setEnabled(false);place(page,reconShareButton,9);reconShareButton.setOnClickListener(v->shareRecon());place(page,label("Only exact-host HTTPS URLs are requested. Cross-host links are excluded and redirects are never followed automatically outside scope.",12,MUTED,false),11);return page;
    }
    private void startRecon(){
        String address=reconUrlInput.getText().toString().trim(),host=reconHostInput.getText().toString().trim();if(!reconPermissionBox.isChecked()){reconStatusView.setText("Confirm authorization before crawling.");return;}
        reconEngine=new ReconEngine();reconRunButton.setEnabled(false);reconShareButton.setEnabled(false);reconStatusView.setText("Starting bounded recon…");reconReportView.setText("Waiting for the first response…");
        new Thread(()->{try{JSONObject report=reconEngine.crawl(address,host,(completed,maximum,url)->runOnUiThread(()->{if(!activityDestroyed)reconStatusView.setText("Recon "+completed+"/"+maximum+" · "+url);}));String json=report.toString(2),pretty=prettyRecon(report);runOnUiThread(()->{if(activityDestroyed)return;reconReportJson=json;reconReportView.setText(pretty);reconReportView.setTextColor(WHITE);reconStatusView.setText(report.optBoolean("cancelled")?"Recon cancelled after "+report.optInt("requests_completed")+" request(s).":"Recon finished within configured limits.");reconRunButton.setEnabled(true);reconShareButton.setEnabled(true);});}
            catch(Exception e){runOnUiThread(()->{if(activityDestroyed)return;reconStatusView.setText("Recon failed: "+safeMessage(e));reconStatusView.setTextColor(RED);reconRunButton.setEnabled(true);});}},"bounded-recon").start();
    }
    private void cancelRecon(){if(reconEngine!=null)reconEngine.cancel();reconStatusView.setText("Cancelling recon…");}
    private String prettyRecon(JSONObject report)throws Exception{
        StringBuilder text=new StringBuilder();JSONArray pages=report.getJSONArray("pages"),urls=report.getJSONArray("discovered_urls");text.append("Scope: ").append(report.getString("scope_host")).append("\nRequests: ").append(report.getInt("requests_completed")).append('/').append(ReconEngine.MAX_REQUESTS).append("\nDiscovered same-host URLs: ").append(urls.length()).append("\nCancelled: ").append(report.getBoolean("cancelled"));
        for(int i=0;i<pages.length();i++){JSONObject page=pages.getJSONObject(i);text.append("\n\n• ").append(page.optInt("status",0)).append("  ").append(page.getString("url"));String title=page.optString("title");if(!title.isEmpty())text.append("\n  Title: ").append(title);text.append("\n  Type: ").append(page.optString("content_type","Not reported")).append("\n  Time: ").append(page.optLong("response_time_ms")).append(" ms");if(page.has("links"))text.append("\n  Links: ").append(page.getJSONArray("links").length()).append(" · Forms: ").append(page.getJSONArray("forms").length()).append(" · Scripts: ").append(page.getJSONArray("scripts").length());if(page.has("error"))text.append("\n  Error: ").append(page.getString("error"));}
        return text.toString();
    }
    private void shareRecon(){if(reconReportJson.isEmpty())return;Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TEXT,reconReportJson);startActivity(Intent.createChooser(intent,"Share recon report"));}
    private void sendWorkbenchRequest(){
        String address=workUrlInput.getText().toString().trim(),scope=workHostInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        String method=String.valueOf(workMethodInput.getSelectedItem()),body=workBodyInput.getText().toString(),headers=workHeadersInput.getText().toString();
        if(!workPermissionBox.isChecked()){workStatusView.setText("Confirm authorization before sending a request.");return;}
        try{validateScopedHttps(address,scope);}catch(Exception e){workStatusView.setText("Enter a valid HTTPS URL and its exact authorized hostname (no custom port).");return;}
        if(body.getBytes(StandardCharsets.UTF_8).length>MAX_REQUEST_BYTES){workStatusView.setText("Request body exceeds the 64 KiB limit.");return;}
        try{parseRequestHeaders(headers);}catch(Exception e){workStatusView.setText("Header error: "+safeMessage(e));return;}
        workSendButton.setEnabled(false);workStatusView.setText("Sending one authorized request…");workResponseView.setText("Waiting for response…");
        boolean follow=workRedirectBox.isChecked();
        new Thread(()->{
            try{
                String result=executeWorkbench(address,scope,method,headers,body,follow);
                runOnUiThread(()->{if(activityDestroyed)return;workResponse=result;workResponseView.setText(result);workResponseView.setTextColor(WHITE);workStatusView.setText("Request finished.");workSendButton.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{if(activityDestroyed)return;workResponse="";workResponseView.setText("Request failed: "+safeMessage(e));workResponseView.setTextColor(RED);workStatusView.setText("Request failed safely.");workSendButton.setEnabled(true);});}
        }).start();
    }
    private URI validateScopedHttps(String address,String scope){
        URI uri=URI.create(address);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawFragment()!=null)throw new IllegalArgumentException("Invalid HTTPS URL");
        if(!validHostname(scope)||!uri.getHost().equalsIgnoreCase(scope))throw new IllegalArgumentException("Host is outside scope");
        return uri;
    }
    private boolean validHostname(String host){if(host==null||host.length()>253||host.startsWith(".")||host.endsWith("."))return false;String[] labels=host.split("\\.",-1);for(String item:labels)if(item.isEmpty()||item.length()>63||item.startsWith("-")||item.endsWith("-")||!item.matches("[A-Za-z0-9-]+"))return false;return true;}
    private Map<String,String> parseRequestHeaders(String raw){
        Map<String,String> result=new java.util.LinkedHashMap<>();
        if(raw.trim().isEmpty())return result;
        for(String line:raw.split("\\r?\\n")){
            if(line.trim().isEmpty())continue;int colon=line.indexOf(':');if(colon<=0)throw new IllegalArgumentException("Each header needs Name: value");
            String name=line.substring(0,colon).trim(),value=line.substring(colon+1).trim();
            if(!name.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+")||value.contains("\r")||value.contains("\n"))throw new IllegalArgumentException("Invalid header syntax");
            String lower=name.toLowerCase(Locale.ROOT);if(lower.equals("host")||lower.equals("content-length")||lower.equals("connection"))throw new IllegalArgumentException(name+" is managed by Android");
            result.put(name,value);
        }
        return result;
    }
    private String executeWorkbench(String initialAddress,String scope,String initialMethod,String rawHeaders,String initialBody,boolean follow)throws Exception{
        String address=initialAddress,method=initialMethod,body=initialBody;Map<String,String> headers=parseRequestHeaders(rawHeaders);
        long started=System.nanoTime();int redirects=0;
        while(true){
            HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();connection.setRequestMethod(method);connection.setConnectTimeout(10000);connection.setReadTimeout(15000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","RedmiBugHunter/0.8 (authorized-http-workbench)");for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
            byte[] requestBytes=body.getBytes(StandardCharsets.UTF_8);boolean mayHaveBody=!method.equals("GET")&&!method.equals("HEAD")&&!method.equals("OPTIONS");
            if(mayHaveBody&&requestBytes.length>0){connection.setDoOutput(true);try(OutputStream output=connection.getOutputStream()){output.write(requestBytes);}}
            int status;Map<String,List<String>> responseHeaders;byte[] responseBytes;
            try{status=connection.getResponseCode();responseHeaders=connection.getHeaderFields();InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();responseBytes=readLimited(stream,MAX_RESPONSE_BYTES);}
            finally{connection.disconnect();}
            String location=headerValue(responseHeaders,"Location");
            if(follow&&isRedirect(status)&&location!=null&&redirects<5){
                URI next=validateScopedHttps(new URI(address).resolve(location).toString(),scope);address=next.toString();redirects++;
                if(status==303||((status==301||status==302)&&method.equals("POST"))){method="GET";body="";}
                continue;
            }
            long elapsed=(System.nanoTime()-started)/1000000L;StringBuilder result=new StringBuilder();
            result.append(method).append(' ').append(address).append("\nStatus: ").append(status).append("\nTime: ").append(elapsed).append(" ms\nRedirects followed: ").append(redirects).append("\nResponse preview: ").append(responseBytes.length).append(" bytes");
            if(responseBytes.length==MAX_RESPONSE_BYTES)result.append(" (possibly truncated)");result.append("\n\nRESPONSE HEADERS\n");
            for(Map.Entry<String,List<String>> header:responseHeaders.entrySet()){String name=header.getKey()==null?"Status-Line":header.getKey();for(String value:header.getValue())result.append(name).append(": ").append(redactHeaderValue(name,value)).append('\n');}
            String responseType=headerValue(responseHeaders,"Content-Type");boolean textual=responseType==null||responseType.toLowerCase(Locale.ROOT).matches(".*(text|json|xml|javascript|x-www-form-urlencoded).*" );
            result.append("\nRESPONSE BODY\n");if(textual)result.append(new String(responseBytes,StandardCharsets.UTF_8));else result.append("[Binary body omitted from text view]\nSHA-256: ").append(digestBytes(responseBytes,"SHA-256"));return result.toString();
        }
    }
    private byte[] readLimited(InputStream stream,int maximum)throws Exception{
        if(stream==null)return new byte[0];try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int total=0,read;while(total<maximum&&(read=input.read(buffer,0,Math.min(buffer.length,maximum-total)))!=-1){output.write(buffer,0,read);total+=read;}return output.toByteArray();}
    }
    private boolean isRedirect(int status){return status==301||status==302||status==303||status==307||status==308;}
    private String headerValue(Map<String,List<String>> headers,String wanted){for(Map.Entry<String,List<String>> item:headers.entrySet())if(item.getKey()!=null&&item.getKey().equalsIgnoreCase(wanted)&&item.getValue()!=null&&!item.getValue().isEmpty())return item.getValue().get(0);return null;}
    private String redactHeaderValue(String name,String value){
        if(value==null)return "";String lower=name==null?"":name.toLowerCase(Locale.ROOT);
        if(lower.equals("set-cookie")){int equals=value.indexOf('='),semicolon=value.indexOf(';');String cookieName=equals>0?value.substring(0,equals).trim():"cookie";return cookieName+"=<redacted>"+(semicolon>=0?value.substring(semicolon):"");}
        if(lower.contains("authorization")||lower.contains("api-key")||lower.contains("auth-token")||lower.equals("authentication-info"))return "<redacted>";
        return value;
    }
    private void copyWorkbenchResponse(){
        if(workResponse.isEmpty()){Toast.makeText(this,"There is no response to copy.",Toast.LENGTH_SHORT).show();return;}ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("HTTP response",workResponse));Toast.makeText(this,"Response copied.",Toast.LENGTH_SHORT).show();}
    }
    private LinearLayout createUtilityPage(){
        LinearLayout page=column();
        place(page,label("Offline utilities",19,WHITE,true),4);
        place(page,label("Transform and inspect text locally. Nothing entered here is sent over the network.",12,MUTED,false),8);
        utilityInput=field("Paste text, encoded data, JWT, or Unix timestamp");
        utilityInput.setSingleLine(false);utilityInput.setMinLines(4);utilityInput.setGravity(Gravity.TOP);
        place(page,utilityInput,14);

        String[][] actions={
                {"Base64 encode","b64e"},{"Base64 decode","b64d"},
                {"URL encode","urle"},{"URL decode","urld"},
                {"HTML escape","htmle"},{"HTML unescape","htmld"},
                {"SHA-256","sha256"},{"SHA-512","sha512"},
                {"Inspect JWT","jwt"},{"Unix → UTC","timestamp"},
                {"Generate UUID","uuid"},{"Clear","clear"}
        };
        for(int i=0;i<actions.length;i+=2){
            LinearLayout row=new LinearLayout(this);row.setOrientation(LinearLayout.HORIZONTAL);
            for(int j=i;j<Math.min(i+2,actions.length);j++){
                String title=actions[j][0],operation=actions[j][1];
                Button action=button(title,j==actions.length-1?Color.rgb(195,216,241):CYAN);action.setTextSize(12);
                LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
                if(j>i)params.leftMargin=dp(7);row.addView(action,params);
                action.setOnClickListener(v->runUtility(operation));
            }
            place(page,row,8);
        }
        place(page,label("Output",17,WHITE,true),20);
        utilityOutput=label("Choose an operation to see its result.",12,MUTED,false);
        utilityOutput.setTextIsSelectable(true);utilityOutput.setPadding(dp(14),dp(15),dp(14),dp(15));utilityOutput.setBackground(background(PANEL,12));
        place(page,utilityOutput,10);
        Button copy=button("Copy output",Color.rgb(195,216,241));place(page,copy,10);
        copy.setOnClickListener(v->copyUtilityResult());
        place(page,label("JWT inspection only decodes the header and payload. It does not verify the signature or establish that any claim is trustworthy.",12,MUTED,false),13);
        return page;
    }
    private LinearLayout createFindingsPage(){
        LinearLayout page=column();place(page,label("Findings and evidence",19,WHITE,true),4);
        place(page,label("Store manually verified findings locally and export a structured report. Nothing is uploaded automatically.",12,MUTED,false),8);
        place(page,label("Title",13,WHITE,true),14);findingTitleInput=field("Example: Missing authorization check");place(page,findingTitleInput,7);
        place(page,label("Severity",13,WHITE,true),13);findingSeverityInput=new Spinner(this);String[] severities={"info","low","medium","high","critical"};ArrayAdapter<String> severityAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,severities){
            @Override public View getView(int position,View convertView,android.view.ViewGroup parent){TextView view=(TextView)super.getView(position,convertView,parent);view.setTextColor(WHITE);view.setPadding(dp(12),dp(12),dp(12),dp(12));return view;}
            @Override public View getDropDownView(int position,View convertView,android.view.ViewGroup parent){TextView view=(TextView)super.getDropDownView(position,convertView,parent);view.setTextColor(Color.BLACK);view.setPadding(dp(12),dp(12),dp(12),dp(12));return view;}
        };severityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);findingSeverityInput.setAdapter(severityAdapter);findingSeverityInput.setSelection(2);findingSeverityInput.setBackground(background(PANEL,10));place(page,findingSeverityInput,7);
        place(page,label("Target",13,WHITE,true),13);findingTargetInput=field("https://authorized.example/path");place(page,findingTargetInput,7);
        place(page,label("Evidence",13,WHITE,true),13);findingEvidenceInput=field("Steps, request/response excerpts, and impact");findingEvidenceInput.setSingleLine(false);findingEvidenceInput.setMinLines(4);findingEvidenceInput.setGravity(Gravity.TOP);place(page,findingEvidenceInput,7);
        place(page,label("Suggested remediation",13,WHITE,true),13);findingRemediationInput=field("Recommended server-side fix");findingRemediationInput.setSingleLine(false);findingRemediationInput.setMinLines(3);findingRemediationInput.setGravity(Gravity.TOP);place(page,findingRemediationInput,7);
        Button save=button("Save verified finding",CYAN);place(page,save,10);save.setOnClickListener(v->saveFinding());findingsStatusView=label("Up to 100 findings are stored in this app's private data.",12,MUTED,false);place(page,findingsStatusView,10);
        place(page,label("Saved findings",17,WHITE,true),18);findingsListView=label("No findings saved.",12,MUTED,false);findingsListView.setTextIsSelectable(true);findingsListView.setPadding(dp(14),dp(15),dp(14),dp(15));findingsListView.setBackground(background(PANEL,12));place(page,findingsListView,8);
        Button refresh=button("Refresh findings",CYAN);place(page,refresh,9);refresh.setOnClickListener(v->refreshFindings());Button export=button("Share JSON report",Color.rgb(195,216,241));place(page,export,7);export.setOnClickListener(v->shareFindings());Button deleteNewest=button("Delete newest finding",Color.rgb(195,216,241));place(page,deleteNewest,7);deleteNewest.setOnClickListener(v->deleteNewestFinding());Button clear=button("Clear all findings",RED);place(page,clear,7);clear.setTextColor(BG);clear.setOnClickListener(v->confirmClearFindings());refreshFindings();return page;
    }
    private void saveFinding(){
        try{findingsStore.add(findingTitleInput.getText().toString(),String.valueOf(findingSeverityInput.getSelectedItem()),findingTargetInput.getText().toString(),findingEvidenceInput.getText().toString(),findingRemediationInput.getText().toString(),"open");findingTitleInput.setText("");findingEvidenceInput.setText("");findingRemediationInput.setText("");findingsStatusView.setText("Finding saved locally.");findingsStatusView.setTextColor(CYAN);refreshFindings();}
        catch(Exception e){findingsStatusView.setText("Could not save: "+safeMessage(e));findingsStatusView.setTextColor(RED);}
    }
    private void refreshFindings(){
        try{JSONArray items=findingsStore.list();if(items.length()==0){findingsListView.setText("No findings saved.");findingsListView.setTextColor(MUTED);return;}StringBuilder text=new StringBuilder();for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(i>0)text.append("\n\n────────────────\n\n");text.append(i+1).append(". [").append(item.optString("severity").toUpperCase(Locale.ROOT)).append("] ").append(item.optString("title")).append("\nTarget: ").append(item.optString("target")).append("\nStatus: ").append(item.optString("status")).append("\nCreated: ").append(item.optString("created_at_utc"));String evidence=item.optString("evidence");if(!evidence.isEmpty())text.append("\nEvidence: ").append(evidence.length()>600?evidence.substring(0,600)+"…":evidence);}findingsListView.setText(text.toString());findingsListView.setTextColor(WHITE);findingsStatusView.setText(items.length()+" finding(s) stored locally.");}
        catch(Exception e){findingsStatusView.setText("Could not load findings: "+safeMessage(e));findingsStatusView.setTextColor(RED);}
    }
    private void shareFindings(){try{String json=findingsStore.exportJson();Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TEXT,json);startActivity(Intent.createChooser(intent,"Share findings report"));}catch(Exception e){findingsStatusView.setText("Could not export: "+safeMessage(e));}}
    private void deleteNewestFinding(){try{JSONArray items=findingsStore.list();if(items.length()==0){findingsStatusView.setText("There is no finding to delete.");return;}findingsStore.delete(items.getJSONObject(0).getString("id"));findingsStatusView.setText("Newest finding deleted.");refreshFindings();}catch(Exception e){findingsStatusView.setText("Could not delete: "+safeMessage(e));}}
    private void confirmClearFindings(){new AlertDialog.Builder(this).setTitle("Clear all findings?").setMessage("This permanently removes every locally saved finding.").setNegativeButton("Cancel",null).setPositiveButton("Clear",(dialog,which)->{findingsStore.clear();refreshFindings();findingsStatusView.setText("All findings cleared.");}).show();}
    private LinearLayout createTerminalPage(){
        LinearLayout page=column();place(page,label("Embedded terminal",19,WHITE,true),4);
        place(page,label("Large in-app Android shell · no external Termux · no root",12,MUTED,false),7);
        int screenDp=(int)(getResources().getDisplayMetrics().heightPixels/getResources().getDisplayMetrics().density),consoleDp=Math.max(420,Math.min(620,screenDp-250));
        LinearLayout console=column();console.setPadding(dp(12),dp(10),dp(12),dp(10));console.setBackground(background(Color.rgb(2,8,8),12));
        terminalStatus=label("● STOPPED  ·  /system/bin/sh",11,Color.rgb(255,180,120),true);console.addView(terminalStatus,new LinearLayout.LayoutParams(-1,-2));
        terminalOutput=label("Redmi Bug Hunter embedded shell\nPress START, accept the safety notice, then enter a command below.\n",13,Color.rgb(158,255,168),false);terminalOutput.setTypeface(Typeface.MONOSPACE);terminalOutput.setTextIsSelectable(true);terminalOutput.setPadding(0,dp(10),0,dp(10));
        terminalOutput.setGravity(Gravity.TOP|Gravity.START);terminalOutput.setMovementMethod(new ScrollingMovementMethod());
        LinearLayout.LayoutParams outputParams=new LinearLayout.LayoutParams(-1,0,1);outputParams.topMargin=dp(4);console.addView(terminalOutput,outputParams);
        terminalInput=field("$ enter command");terminalInput.setTypeface(Typeface.MONOSPACE);terminalInput.setTextColor(Color.rgb(158,255,168));terminalInput.setHintTextColor(Color.rgb(89,145,104));terminalInput.setBackground(background(Color.rgb(9,24,18),6));terminalInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS|InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);terminalInput.setImeOptions(EditorInfo.IME_ACTION_SEND);terminalInput.setContentDescription("Terminal command input");terminalInput.setOnEditorActionListener((view,action,event)->{boolean enter=action==EditorInfo.IME_ACTION_SEND||(event!=null&&event.getKeyCode()==KeyEvent.KEYCODE_ENTER&&event.getAction()==KeyEvent.ACTION_DOWN);if(enter){runTerminalCommand();return true;}return false;});
        LinearLayout.LayoutParams inputParams=new LinearLayout.LayoutParams(-1,-2);inputParams.topMargin=dp(7);console.addView(terminalInput,inputParams);
        LinearLayout.LayoutParams consoleParams=new LinearLayout.LayoutParams(-1,dp(consoleDp));consoleParams.topMargin=dp(12);page.addView(console,consoleParams);
        LinearLayout actions=new LinearLayout(this);actions.setOrientation(LinearLayout.HORIZONTAL);
        Button run=button("Run command",CYAN),start=button("Start / restart",Color.rgb(195,216,241));LinearLayout.LayoutParams half=new LinearLayout.LayoutParams(0,-2,1);actions.addView(run,half);LinearLayout.LayoutParams halfGap=new LinearLayout.LayoutParams(0,-2,1);halfGap.leftMargin=dp(7);actions.addView(start,halfGap);place(page,actions,8);
        run.setOnClickListener(v->runTerminalCommand());start.setOnClickListener(v->confirmStartShellSession());
        LinearLayout controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);Button stop=button("Stop",Color.rgb(195,216,241)),copy=button("Copy all",Color.rgb(195,216,241)),clear=button("Clear",Color.rgb(195,216,241));controls.addView(stop,new LinearLayout.LayoutParams(0,-2,1));LinearLayout.LayoutParams copyParams=new LinearLayout.LayoutParams(0,-2,1);copyParams.leftMargin=dp(6);controls.addView(copy,copyParams);LinearLayout.LayoutParams clearParams=new LinearLayout.LayoutParams(0,-2,1);clearParams.leftMargin=dp(6);controls.addView(clear,clearParams);place(page,controls,7);
        stop.setOnClickListener(v->stopShellSession());copy.setOnClickListener(v->copyTerminalOutput());clear.setOnClickListener(v->{terminalBuffer.setLength(0);appendTerminal("[screen cleared]\n");});
        place(page,label("Android system commands only in this phase; full-screen PTY programs and Termux packages are not bundled.",11,MUTED,false),10);return page;
    }
    private void confirmStartShellSession(){new AlertDialog.Builder(this).setTitle("Start embedded shell?").setMessage("Shell commands run with this app's identity and can read its private findings or use its network permission. Run only commands you understand and trust.").setNegativeButton("Cancel",null).setPositiveButton("Start",(dialog,which)->startShellSession()).show();}
    private void startShellSession(){
        stopShellSession();int generation=++shellGeneration;terminalBuffer.setLength(0);terminalOutput.setText("");
        shellSession=new ShellSession(getFilesDir(),new ShellSession.Listener(){
            @Override public void onOutput(String text){runOnUiThread(()->{if(!activityDestroyed&&generation==shellGeneration)appendTerminal(text);});}
            @Override public void onStopped(String reason){runOnUiThread(()->{if(activityDestroyed||generation!=shellGeneration)return;appendTerminal("\n["+reason+"]\n");terminalStatus.setText("● STOPPED  ·  /system/bin/sh");terminalStatus.setTextColor(Color.rgb(255,180,120));});}
        });
        try{shellSession.start();terminalStatus.setText("● RUNNING  ·  /system/bin/sh");terminalStatus.setTextColor(Color.rgb(158,255,168));appendTerminal("Redmi Bug Hunter embedded shell\nWorking directory: "+getFilesDir().getAbsolutePath()+"\n\n");terminalInput.requestFocus();}
        catch(Exception e){appendTerminal("[could not start shell: "+safeMessage(e)+"]\n");terminalStatus.setText("● START FAILED");terminalStatus.setTextColor(RED);}
    }
    private void runTerminalCommand(){
        String command=terminalInput.getText().toString();if(command.trim().isEmpty())return;if(shellSession==null||!shellSession.isRunning()){appendTerminal("[start the shell and accept the safety notice first]\n");return;}
        try{appendTerminal("$ "+command+"\n");shellSession.execute(command);terminalInput.setText("");}
        catch(Exception e){appendTerminal("[command failed: "+safeMessage(e)+"]\n");}
    }
    private void appendTerminal(String text){terminalBuffer.append(text);if(terminalBuffer.length()>50000)terminalBuffer.delete(0,terminalBuffer.length()-40000);if(!terminalRenderScheduled){terminalRenderScheduled=true;terminalOutput.postDelayed(()->{terminalRenderScheduled=false;terminalOutput.setText(terminalBuffer.toString());android.text.Layout layout=terminalOutput.getLayout();if(layout!=null){int amount=layout.getLineTop(terminalOutput.getLineCount())-terminalOutput.getHeight();terminalOutput.scrollTo(0,Math.max(0,amount));}},50);}}
    private void copyTerminalOutput(){if(terminalBuffer.length()==0){Toast.makeText(this,"Terminal output is empty.",Toast.LENGTH_SHORT).show();return;}ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Terminal output",terminalBuffer.toString()));Toast.makeText(this,"Terminal output copied.",Toast.LENGTH_SHORT).show();}}
    private void stopShellSession(){shellGeneration++;boolean wasRunning=shellSession!=null&&shellSession.isRunning();if(shellSession!=null)shellSession.stop();shellSession=null;if(terminalStatus!=null){terminalStatus.setText("● STOPPED  ·  /system/bin/sh");terminalStatus.setTextColor(Color.rgb(255,180,120));if(wasRunning&&terminalOutput!=null)appendTerminal("\n[shell stopped]\n");}}
    @Override protected void onDestroy(){activityDestroyed=true;if(reconEngine!=null)reconEngine.cancel();stopShellSession();super.onDestroy();}
    private LinearLayout createTrafficPage(){
        LinearLayout page=column();place(page,label("Scoped traffic inspector",19,WHITE,true),4);
        place(page,label("A user-started local CONNECT proxy for authorized HTTPS connection metadata. TLS contents remain encrypted.",12,MUTED,false),8);
        TextView endpoint=label("PROXY ENDPOINT  ·  127.0.0.1:8080",12,CYAN,true);endpoint.setPadding(dp(13),dp(12),dp(13),dp(12));endpoint.setBackground(background(PANEL,10));place(page,endpoint,14);
        place(page,label("Authorized host (one exact hostname)",13,WHITE,true),16);trafficHostInput=field("test.example.com");place(page,trafficHostInput,7);
        trafficPermissionBox=new CheckBox(this);trafficPermissionBox.setText("I own this host or have explicit permission to inspect its traffic.");trafficPermissionBox.setTextColor(WHITE);trafficPermissionBox.setTextSize(13);trafficPermissionBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));place(page,trafficPermissionBox,12);
        Button start=button("Start foreground inspector",CYAN);place(page,start,10);start.setOnClickListener(v->startTrafficInspector());
        Button stop=button("Stop inspector",Color.rgb(195,216,241));place(page,stop,7);stop.setOnClickListener(v->stopTrafficInspector());
        trafficStatusView=label("Stopped. Configure a test client only after starting the inspector.",12,MUTED,false);place(page,trafficStatusView,12);
        place(page,infoCard("How to use\n1. Start the inspector with the exact authorized host.\n2. Configure your test browser or app proxy as 127.0.0.1 port 8080.\n3. Generate HTTPS traffic to that host.\n4. Refresh the log below.\n5. Remove the proxy setting and stop the inspector when finished."),13);
        place(page,label("Connection metadata",17,WHITE,true),18);trafficLogView=label("No captured connection metadata yet.",12,MUTED,false);trafficLogView.setTextIsSelectable(true);trafficLogView.setPadding(dp(14),dp(15),dp(14),dp(15));trafficLogView.setBackground(background(PANEL,12));place(page,trafficLogView,8);
        Button refresh=button("Refresh connection log",CYAN);place(page,refresh,9);refresh.setOnClickListener(v->refreshTrafficLog());
        Button clear=button("Clear connection log",Color.rgb(195,216,241));place(page,clear,7);clear.setOnClickListener(v->{TrafficProxyService.clearEvents();refreshTrafficLog();});
        place(page,label("This stage records hostname, port, byte counts, duration, blocks out-of-scope hosts, and never decrypts TLS. It is not a certificate-pinning bypass.",12,MUTED,false),12);return page;
    }
    private void startTrafficInspector(){
        String host=trafficHostInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if(!trafficPermissionBox.isChecked()){trafficStatusView.setText("Confirm authorization before starting capture.");return;}
        if(!validHostname(host)){trafficStatusView.setText("Enter one valid exact hostname without https://, a path, wildcard, or trailing dot.");return;}
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},NOTIFICATION_PERMISSION_REQUEST);trafficStatusView.setText("Allow notifications, then press Start again. Android requires a visible indicator for traffic inspection.");return;}
        Intent intent=new Intent(this,TrafficProxyService.class);intent.putExtra("authorized_host",host);
        startForegroundService(intent);trafficStatusView.setText("Starting inspector for "+host+" on 127.0.0.1:8080…");
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] grantResults){super.onRequestPermissionsResult(requestCode,permissions,grantResults);if(requestCode==NOTIFICATION_PERMISSION_REQUEST&&trafficStatusView!=null)trafficStatusView.setText(grantResults.length>0&&grantResults[0]==PackageManager.PERMISSION_GRANTED?"Notification allowed. Press Start to begin inspection.":"Notification permission is required for the visible traffic-inspection service.");}
    private void stopTrafficInspector(){stopService(new Intent(this,TrafficProxyService.class));trafficStatusView.setText("Inspector stopped. Remove the proxy setting from the test client.");refreshTrafficLog();}
    private void refreshTrafficLog(){trafficLogView.setText(TrafficProxyService.eventText());trafficLogView.setTextColor(WHITE);trafficStatusView.setText(TrafficProxyService.isRunning()?"Inspector active on 127.0.0.1:8080.":"Inspector is stopped.");}
    private void runUtility(String operation){
        String input=utilityInput.getText().toString();
        if("clear".equals(operation)){
            utilityInput.setText("");utilityResult="";utilityOutput.setText("Choose an operation to see its result.");return;
        }
        try{
            switch(operation){
                case "b64e": utilityResult=Base64.encodeToString(input.getBytes(StandardCharsets.UTF_8),Base64.NO_WRAP);break;
                case "b64d": utilityResult=new String(Base64.decode(input.trim(),Base64.DEFAULT),StandardCharsets.UTF_8);break;
                case "urle": utilityResult=URLEncoder.encode(input,StandardCharsets.UTF_8.name()).replace("+","%20");break;
                case "urld": utilityResult=URLDecoder.decode(input,StandardCharsets.UTF_8.name());break;
                case "htmle": utilityResult=Html.escapeHtml(input);break;
                case "htmld": utilityResult=Html.fromHtml(input,Html.FROM_HTML_MODE_LEGACY).toString();break;
                case "sha256": utilityResult=digest(input,"SHA-256");break;
                case "sha512": utilityResult=digest(input,"SHA-512");break;
                case "jwt": utilityResult=inspectJwt(input);break;
                case "timestamp": utilityResult=formatUnixTimestamp(input);break;
                case "uuid": utilityResult=UUID.randomUUID().toString();break;
                default: throw new IllegalArgumentException("Unknown utility");
            }
            utilityOutput.setText(utilityResult);utilityOutput.setTextColor(WHITE);
        }catch(Exception e){
            utilityResult="";utilityOutput.setText("Could not process this input: "+safeMessage(e));utilityOutput.setTextColor(RED);
        }
    }
    private String digest(String input,String algorithm)throws Exception{
        byte[] bytes=MessageDigest.getInstance(algorithm).digest(input.getBytes(StandardCharsets.UTF_8));
        StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.US,"%02x",value&0xff));return result.toString();
    }
    private String inspectJwt(String input)throws Exception{
        String[] parts=input.trim().split("\\.",-1);if(parts.length!=3)throw new IllegalArgumentException("A JWT must contain three dot-separated parts");
        String header=new String(Base64.decode(parts[0],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8);
        String payload=new String(Base64.decode(parts[1],Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING),StandardCharsets.UTF_8);
        String prettyHeader=new JSONObject(header).toString(2),prettyPayload=new JSONObject(payload).toString(2);
        return "UNVERIFIED HEADER\n"+prettyHeader+"\n\nUNVERIFIED PAYLOAD\n"+prettyPayload+"\n\nSignature present: "+(!parts[2].isEmpty())+"\nSignature verified: NO";
    }
    private String formatUnixTimestamp(String input){
        long value=Long.parseLong(input.trim());
        if(Math.abs(value)<100000000000L)value*=1000L;
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss 'UTC'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(value))+"\nEpoch milliseconds: "+value;
    }
    private String safeMessage(Exception e){
        String message=e.getMessage();return message==null||message.trim().isEmpty()?e.getClass().getSimpleName():message;
    }
    private void copyUtilityResult(){
        if(utilityResult.isEmpty()){Toast.makeText(this,"There is no output to copy.",Toast.LENGTH_SHORT).show();return;}
        ClipboardManager clipboard=(ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if(clipboard!=null){clipboard.setPrimaryClip(ClipData.newPlainText("Redmi Bug Hunter output",utilityResult));Toast.makeText(this,"Output copied.",Toast.LENGTH_SHORT).show();}
    }
    private TextView infoCard(String text){
        TextView card=label(text,13,WHITE,false);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(background(PANEL,12));return card;
    }
    private void showPage(int selected){
        for(int i=0;i<pages.length;i++){
            pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
            navigationButtons[i].setBackground(background(i==selected?CYAN:Color.rgb(195,216,241),10));
        }
        if(appScroll!=null)appScroll.post(()->appScroll.smoothScrollTo(0,0));
    }
    private void openSettings(Intent intent){
        try{startActivity(intent);}
        catch(ActivityNotFoundException e){Toast.makeText(this,"This settings page is unavailable on this device.",Toast.LENGTH_LONG).show();}
    }
    private void scan() {
        String url=urlInput.getText().toString().trim(),scope=hostInput.getText().toString().trim();
        if(!permissionBox.isChecked()){statusView.setText("Confirm authorization before scanning.");return;}
        URI uri;
        try {
            uri=URI.create(url);
            if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawFragment()!=null)throw new IllegalArgumentException();
            if(!uri.getHost().equalsIgnoreCase(scope)||!validHostname(scope))throw new IllegalArgumentException();
        }catch(Exception e){statusView.setText("Enter a valid HTTPS URL and its exact authorized hostname (no custom port).");return;}
        runButton.setEnabled(false);shareButton.setEnabled(false);statusView.setText("Running a single HTTPS request…");
        new Thread(()->{
            try {
                JSONObject result=audit(url,scope);
                String json=result.toString(2);
                String formatted=pretty(result);
                runOnUiThread(()->{if(activityDestroyed)return;reportJson=json;reportView.setText(formatted);statusView.setText("Finished. No redirect was followed.");runButton.setEnabled(true);shareButton.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{if(activityDestroyed)return;statusView.setText("Audit failed: "+e.getClass().getSimpleName()+" — "+e.getMessage());runButton.setEnabled(true);});}
        }).start();
    }
    private JSONObject audit(String address,String scope)throws Exception {
        JSONArray findings=new JSONArray(),addresses=new JSONArray();
        long dnsStart=System.nanoTime();for(InetAddress item:InetAddress.getAllByName(scope))addresses.put(item.getHostAddress());long dnsMs=(System.nanoTime()-dnsStart)/1000000L;
        URL url=new URL(address);HttpsURLConnection c=(HttpsURLConnection)url.openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","RedmiBugHunter/0.8 (authorized-security-audit)");
        int status;long requestStart=System.nanoTime();Map<String,List<String>> headers;JSONObject tls=new JSONObject(),headerJson=new JSONObject();
        try {
            status=c.getResponseCode();long responseMs=(System.nanoTime()-requestStart)/1000000L;headers=c.getHeaderFields();
            for(Map.Entry<String,List<String>> entry:headers.entrySet())if(entry.getKey()!=null&&entry.getValue()!=null){JSONArray values=new JSONArray();for(String value:entry.getValue())values.put(redactHeaderValue(entry.getKey(),value));headerJson.put(entry.getKey(),values);}
            tls.put("cipher_suite",c.getCipherSuite());Certificate[] certificates=c.getServerCertificates();
            if(certificates.length>0&&certificates[0] instanceof X509Certificate){
                X509Certificate cert=(X509Certificate)certificates[0];tls.put("subject",cert.getSubjectX500Principal().getName());tls.put("issuer",cert.getIssuerX500Principal().getName());tls.put("serial_hex",cert.getSerialNumber().toString(16));tls.put("signature_algorithm",cert.getSigAlgName());tls.put("public_key_algorithm",cert.getPublicKey().getAlgorithm());tls.put("not_before",isoDate(cert.getNotBefore()));tls.put("not_after",isoDate(cert.getNotAfter()));
            }
            String[][] checks={{"Strict-Transport-Security","medium","HSTS header not observed","HSTS helps browsers enforce HTTPS on future visits."},{"Content-Security-Policy","medium","CSP header not observed","CSP can reduce the impact of some content-injection vulnerabilities."},{"X-Content-Type-Options","low","X-Content-Type-Options not observed","nosniff reduces MIME-type confusion."},{"Referrer-Policy","low","Referrer-Policy not observed","A policy can reduce unintended URL data disclosure."},{"Permissions-Policy","info","Permissions-Policy not observed","This header can restrict access to browser capabilities."},{"Cross-Origin-Opener-Policy","info","COOP not observed","COOP can provide browsing-context isolation where appropriate."}};
            for(String[] check:checks)if(c.getHeaderField(check[0])==null)issue(findings,check[1],check[2],check[3]);
            String xcto=c.getHeaderField("X-Content-Type-Options");if(xcto!=null&&!"nosniff".equalsIgnoreCase(xcto.trim()))issue(findings,"low","Unexpected X-Content-Type-Options value","Observed: "+xcto+". The commonly expected value is nosniff.");
            String cors=c.getHeaderField("Access-Control-Allow-Origin");String credentials=c.getHeaderField("Access-Control-Allow-Credentials");
            if("*".equals(cors)&&"true".equalsIgnoreCase(credentials))issue(findings,"medium","Broad CORS response needs review","Wildcard origin was returned with credential support. Browser enforcement and endpoint behavior must be verified manually.");
            else if("*".equals(cors))issue(findings,"info","Wildcard CORS origin observed","Public cross-origin access may be intentional; review whether the response contains sensitive data.");
            String cache=c.getHeaderField("Cache-Control");if(cache==null)issue(findings,"info","Cache-Control not observed","Review whether this response can contain sensitive or user-specific content.");
            int cookieNumber=0;for(Map.Entry<String,List<String>> entry:headers.entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie")&&entry.getValue()!=null)for(String cookie:entry.getValue()){
                cookieNumber++;String lower=cookie.toLowerCase(Locale.ROOT),name=cookie.split("=",2)[0].trim();
                if(!lower.contains("; secure"))issue(findings,"medium","Cookie missing Secure","Cookie #"+cookieNumber+" ("+name+") may be sent over an unencrypted connection if one becomes available.");
                if(!lower.contains("; httponly"))issue(findings,"low","Cookie missing HttpOnly","Cookie #"+cookieNumber+" ("+name+") may intentionally require script access; verify its purpose.");
                if(!lower.contains("; samesite"))issue(findings,"low","Cookie missing SameSite","Cookie #"+cookieNumber+" ("+name+") needs CSRF-context review.");
            }
            analyzeAdvancedHeaders(c,headers,findings);
            InputStream responseStream=status>=400?c.getErrorStream():c.getInputStream();byte[] bodyBytes=readLimited(responseStream,MAX_RESPONSE_BYTES);String bodyText=new String(bodyBytes,StandardCharsets.UTF_8);
            JSONObject bodyAnalysis=analyzeResponseBody(c.getContentType(),bodyText,findings);bodyAnalysis.put("preview_bytes",bodyBytes.length);bodyAnalysis.put("possibly_truncated",bodyBytes.length==MAX_RESPONSE_BYTES);bodyAnalysis.put("sha256",digestBytes(bodyBytes,"SHA-256"));
            JSONObject output=new JSONObject();output.put("tool","Redmi Bug Hunter");output.put("version","0.8.0");output.put("timestamp_utc",isoDate(new Date()));output.put("scope_host",scope);output.put("url",address);output.put("resolved_ips",addresses);output.put("dns_time_ms",dnsMs);output.put("response_time_ms",responseMs);output.put("http_status",status);output.put("response_message",c.getResponseMessage());output.put("content_type",c.getContentType());output.put("content_length",c.getContentLengthLong());output.put("redirect_location",c.getHeaderField("Location"));output.put("redirect_followed",false);output.put("tls",tls);output.put("response_headers",headerJson);output.put("body_analysis",bodyAnalysis);output.put("security_posture_score",postureScore(findings));output.put("findings",findings);output.put("notice","The posture score and heuristic findings require manual verification and are not proof of exploitability.");return output;
        }finally{c.disconnect();}
    }
    private String isoDate(Date date){SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(date);}
    private void analyzeAdvancedHeaders(HttpsURLConnection c,Map<String,List<String>> headers,JSONArray findings)throws Exception{
        String hsts=c.getHeaderField("Strict-Transport-Security");if(hsts!=null){String lower=hsts.toLowerCase(Locale.ROOT);long maxAge=-1;for(String part:lower.split(";")){String item=part.trim();if(item.startsWith("max-age="))try{maxAge=Long.parseLong(item.substring(8).trim());}catch(Exception ignored){}}
            if(maxAge<15552000L)issue(findings,"low","HSTS max-age is short or invalid","A long-lived policy is commonly at least 180 days; observed max-age: "+maxAge+" seconds.");if(!lower.contains("includesubdomains"))issue(findings,"info","HSTS excludes subdomains","Review whether all subdomains are HTTPS-ready before enabling includeSubDomains.");}
        String csp=c.getHeaderField("Content-Security-Policy");if(csp!=null){String lower=csp.toLowerCase(Locale.ROOT);if(lower.contains("'unsafe-inline'"))issue(findings,"low","CSP allows unsafe-inline","Inline script or style allowances can weaken injection defenses; review nonces or hashes.");if(lower.contains("'unsafe-eval'"))issue(findings,"medium","CSP allows unsafe-eval","Dynamic code evaluation can increase the impact of script injection.");if(!lower.contains("object-src"))issue(findings,"info","CSP has no object-src directive","Consider object-src 'none' when plugins are not required.");if(!lower.contains("base-uri"))issue(findings,"info","CSP has no base-uri directive","base-uri can restrict injected base elements.");}
        String xfo=c.getHeaderField("X-Frame-Options");if(xfo==null&&(csp==null||!csp.toLowerCase(Locale.ROOT).contains("frame-ancestors")))issue(findings,"medium","Framing protection not observed","Neither X-Frame-Options nor CSP frame-ancestors was found; verify clickjacking exposure in context.");
        String server=c.getHeaderField("Server");if(server!=null&&server.matches(".*[0-9].*"))issue(findings,"info","Server version may be disclosed","Observed Server header: "+server+". Confirm whether this detail is necessary.");
        String powered=c.getHeaderField("X-Powered-By");if(powered!=null)issue(findings,"info","Technology header disclosed","Observed X-Powered-By: "+powered+".");
        String location=c.getHeaderField("Location");if(location!=null&&location.trim().toLowerCase(Locale.ROOT).startsWith("http://"))issue(findings,"medium","Redirect downgrades to HTTP","The response redirects from HTTPS to an unencrypted URL.");
        boolean setsCookie=headerValue(headers,"Set-Cookie")!=null;String cache=c.getHeaderField("Cache-Control");if(setsCookie&&(cache==null||(!cache.toLowerCase(Locale.ROOT).contains("no-store")&&!cache.toLowerCase(Locale.ROOT).contains("private"))))issue(findings,"low","Cookie response may be cacheable","A response setting cookies does not clearly declare no-store or private; assess actual sensitive content and intermediary behavior.");
        List<String> cookies=null;for(Map.Entry<String,List<String>> entry:headers.entrySet())if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie"))cookies=entry.getValue();
        if(cookies!=null)for(String cookie:cookies){String lower=cookie.toLowerCase(Locale.ROOT),name=cookie.split("=",2)[0].trim();if(lower.contains("samesite=none")&&!lower.contains("; secure"))issue(findings,"medium","SameSite=None cookie lacks Secure",name+" may be rejected by modern browsers and lacks transport protection.");if(name.startsWith("__Host-")&&(lower.contains("; domain=")||!lower.contains("; path=/")||!lower.contains("; secure")))issue(findings,"medium","Invalid __Host- cookie constraints",name+" does not meet the Secure, Path=/, and no-Domain requirements.");if(name.startsWith("__Secure-")&&!lower.contains("; secure"))issue(findings,"medium","Invalid __Secure- cookie constraint",name+" does not include Secure.");}
    }
    private JSONObject analyzeResponseBody(String contentType,String body,JSONArray findings)throws Exception{
        JSONObject analysis=new JSONObject();String type=contentType==null?"":contentType.toLowerCase(Locale.ROOT),lower=body.toLowerCase(Locale.ROOT);boolean html=type.contains("text/html")||lower.contains("<html");analysis.put("html_detected",html);
        if(html){boolean password=lower.matches("(?s).*<input[^>]+type\\s*=\\s*['\"]?password.*");boolean mixed=lower.matches("(?s).*(src|href|action)\\s*=\\s*['\"]http://.*");boolean insecureForm=lower.matches("(?s).*<form[^>]+action\\s*=\\s*['\"]http://.*");analysis.put("password_field_detected",password);analysis.put("mixed_http_reference_detected",mixed);analysis.put("insecure_form_action_detected",insecureForm);if(mixed)issue(findings,"medium","HTTP resource referenced by HTTPS page","Passive HTML review found a src, href, or action using http://. Verify whether active mixed content is loaded.");if(insecureForm)issue(findings,"high","Form may submit over HTTP","Passive HTML review found an HTTP form action. Confirm the rendered form and data sensitivity manually.");}
        return analysis;
    }
    private String digestBytes(byte[] input,String algorithm)throws Exception{byte[] bytes=MessageDigest.getInstance(algorithm).digest(input);StringBuilder result=new StringBuilder();for(byte value:bytes)result.append(String.format(Locale.US,"%02x",value&0xff));return result.toString();}
    private int postureScore(JSONArray findings)throws Exception{int score=100;for(int i=0;i<findings.length();i++){String severity=findings.getJSONObject(i).getString("severity");if("high".equals(severity))score-=20;else if("medium".equals(severity))score-=10;else if("low".equals(severity))score-=4;}return Math.max(0,score);}
    private void issue(JSONArray items,String severity,String title,String detail)throws Exception{
        JSONObject f=new JSONObject();f.put("severity",severity);f.put("title",title);f.put("detail",detail);items.put(f);
    }
    private String pretty(JSONObject result)throws Exception {
        StringBuilder b=new StringBuilder();b.append("Host: ").append(result.getString("scope_host"));
        b.append("\nURL: ").append(result.getString("url")).append("\nResolved IPs: ").append(result.getJSONArray("resolved_ips").join(", "));
        b.append("\nHTTP status: ").append(result.getInt("http_status")).append(' ').append(result.optString("response_message",""));
        b.append("\nDNS time: ").append(result.getLong("dns_time_ms")).append(" ms\nResponse time: ").append(result.getLong("response_time_ms")).append(" ms");
        b.append("\nContent-Type: ").append(result.optString("content_type","Not reported")).append("\nContent-Length: ").append(result.getLong("content_length")).append("\nHeuristic posture score: ").append(result.getInt("security_posture_score")).append("/100");
        if(!result.isNull("redirect_location"))b.append("\nRedirect destination: ").append(result.getString("redirect_location")).append("\nRedirect followed: No");
        JSONObject tls=result.getJSONObject("tls");b.append("\n\nTLS\nCipher suite: ").append(tls.optString("cipher_suite","Not reported"));
        b.append("\nCertificate subject: ").append(tls.optString("subject","Not reported")).append("\nCertificate issuer: ").append(tls.optString("issuer","Not reported"));
        b.append("\nValid from: ").append(tls.optString("not_before","Not reported")).append("\nValid until: ").append(tls.optString("not_after","Not reported"));
        JSONArray items=result.getJSONArray("findings");b.append("\nFindings: ").append(items.length());
        for(int i=0;i<items.length();i++){
            JSONObject f=items.getJSONObject(i);b.append("\n\n• [").append(f.getString("severity").toUpperCase(Locale.ROOT)).append("] ").append(f.getString("title"));b.append("\n  ").append(f.getString("detail"));
        }
        if(items.length()==0)b.append("\n\nNo issues identified by this limited baseline check.");JSONObject body=result.getJSONObject("body_analysis");b.append("\n\nBODY ANALYSIS\nPreview bytes: ").append(body.getInt("preview_bytes")).append("\nSHA-256: ").append(body.getString("sha256")).append("\nHTML detected: ").append(body.getBoolean("html_detected"));if(body.has("password_field_detected"))b.append("\nPassword field detected: ").append(body.getBoolean("password_field_detected")).append("\nMixed HTTP reference detected: ").append(body.getBoolean("mixed_http_reference_detected"));
        b.append("\n\nRESPONSE HEADERS");JSONObject headers=result.getJSONObject("response_headers");java.util.Iterator<String> names=headers.keys();while(names.hasNext()){String name=names.next();JSONArray values=headers.getJSONArray(name);for(int i=0;i<values.length();i++)b.append('\n').append(name).append(": ").append(values.getString(i));}return b.toString();
    }
    private void share(){
        if(reportJson.isEmpty())return;
        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TEXT,reportJson);
        startActivity(Intent.createChooser(intent,"Share security report"));
    }
}
