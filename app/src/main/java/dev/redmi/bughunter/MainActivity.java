package dev.redmi.bughunter;

import android.app.Activity;
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
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {
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
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setBackgroundColor(BG);
        LinearLayout root=column();root.setPadding(dp(20),dp(30),dp(20),dp(28));sc.addView(root);setContentView(sc);
        place(root,label("REDMI BUG HUNTER",23,CYAN,true),4);
        place(root,label("Authorized security research toolkit",13,MUTED,false),8);
        LinearLayout navigation=new LinearLayout(this);navigation.setOrientation(LinearLayout.HORIZONTAL);
        String[] names={"Device","Audit","HTTP","Tools","Learn","Privacy"};
        navigationButtons=new Button[names.length];
        for(int i=0;i<names.length;i++){
            final int page=i;Button item=button(names[i],i==0?CYAN:Color.rgb(195,216,241));
            item.setTextSize(11);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);
            if(i>0)params.leftMargin=dp(5);navigation.addView(item,params);navigationButtons[i]=item;
            item.setOnClickListener(v->showPage(page));
        }
        place(root,navigation,20);
        pages=new LinearLayout[]{createDevicePage(),createAuditPage(),createWorkbenchPage(),createUtilityPage(),createLearningPage(),createPrivacyPage()};
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
    private LinearLayout createLearningPage(){
        LinearLayout page=column();
        place(page,label("Security basics",19,WHITE,true),4);
        place(page,label("Offline learning cards. Use these to improve systems you own or are authorized to manage.",12,MUTED,false),8);
        place(page,infoCard("HTTPS and certificates\nHTTPS encrypts a connection in transit and helps authenticate the server. A valid certificate does not prove that a site is safe or free of vulnerabilities."),14);
        place(page,infoCard("Security headers\nHeaders such as Content-Security-Policy and X-Content-Type-Options can reduce certain browser risks. Their absence is a prompt for review, not proof of an exploitable flaw."),10);
        place(page,infoCard("Cookies\nSecure, HttpOnly, and SameSite attributes can reduce exposure in common situations. Review each cookie in its application context; some cookies have different intended uses."),10);
        place(page,infoCard("Responsible testing\nGet written permission, agree on scope and timing, use test accounts where possible, minimize traffic, stop if unexpected impact occurs, and report findings privately to the owner."),10);
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
                runOnUiThread(()->{workResponse=result;workResponseView.setText(result);workResponseView.setTextColor(WHITE);workStatusView.setText("Request finished.");workSendButton.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{workResponse="";workResponseView.setText("Request failed: "+safeMessage(e));workResponseView.setTextColor(RED);workStatusView.setText("Request failed safely.");workSendButton.setEnabled(true);});}
        }).start();
    }
    private URI validateScopedHttps(String address,String scope){
        URI uri=URI.create(address);
        if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getPort()!=-1||uri.getRawFragment()!=null)throw new IllegalArgumentException("Invalid HTTPS URL");
        if(scope.isEmpty()||scope.endsWith(".")||!uri.getHost().equalsIgnoreCase(scope))throw new IllegalArgumentException("Host is outside scope");
        return uri;
    }
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
            connection.setRequestProperty("User-Agent","RedmiBugHunter/0.3 (authorized-http-workbench)");for(Map.Entry<String,String> header:headers.entrySet())connection.setRequestProperty(header.getKey(),header.getValue());
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
            for(Map.Entry<String,List<String>> header:responseHeaders.entrySet()){String name=header.getKey()==null?"Status-Line":header.getKey();for(String value:header.getValue())result.append(name).append(": ").append(value).append('\n');}
            result.append("\nRESPONSE BODY\n").append(new String(responseBytes,StandardCharsets.UTF_8));return result.toString();
        }
    }
    private byte[] readLimited(InputStream stream,int maximum)throws Exception{
        if(stream==null)return new byte[0];try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int total=0,read;while(total<maximum&&(read=input.read(buffer,0,Math.min(buffer.length,maximum-total)))!=-1){output.write(buffer,0,read);total+=read;}return output.toByteArray();}
    }
    private boolean isRedirect(int status){return status==301||status==302||status==303||status==307||status==308;}
    private String headerValue(Map<String,List<String>> headers,String wanted){for(Map.Entry<String,List<String>> item:headers.entrySet())if(item.getKey()!=null&&item.getKey().equalsIgnoreCase(wanted)&&item.getValue()!=null&&!item.getValue().isEmpty())return item.getValue().get(0);return null;}
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
    private LinearLayout createPrivacyPage(){
        LinearLayout page=column();
        place(page,label("Privacy controls",19,WHITE,true),4);
        place(page,label("Open Android settings to review permissions and privacy controls. This app does not collect device details.",12,MUTED,false),8);
        Button permissions=button("Review this app's permissions",CYAN);place(page,permissions,14);
        permissions.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:"+getPackageName()))));
        Button dashboard=button("Open Android privacy settings",Color.rgb(195,216,241));place(page,dashboard,8);
        dashboard.setOnClickListener(v->openSettings(new Intent(Settings.ACTION_PRIVACY_SETTINGS)));
        place(page,infoCard("Review location, camera, microphone, contacts, and nearby-device access for apps that need them. Revoke permissions that are no longer necessary."),15);
        return page;
    }
    private TextView infoCard(String text){
        TextView card=label(text,13,WHITE,false);card.setPadding(dp(14),dp(14),dp(14),dp(14));card.setBackground(background(PANEL,12));return card;
    }
    private void showPage(int selected){
        for(int i=0;i<pages.length;i++){
            pages[i].setVisibility(i==selected?View.VISIBLE:View.GONE);
            navigationButtons[i].setBackground(background(i==selected?CYAN:Color.rgb(195,216,241),10));
        }
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
            if(!uri.getHost().equalsIgnoreCase(scope) || scope.isEmpty() || scope.endsWith("."))throw new IllegalArgumentException();
        }catch(Exception e){statusView.setText("Enter a valid HTTPS URL and its exact authorized hostname (no custom port).");return;}
        runButton.setEnabled(false);shareButton.setEnabled(false);statusView.setText("Running a single HTTPS request…");
        new Thread(()->{
            try {
                JSONObject result=audit(url,scope);
                String json=result.toString(2);
                String formatted=pretty(result);
                runOnUiThread(()->{reportJson=json;reportView.setText(formatted);statusView.setText("Finished. No redirect was followed.");runButton.setEnabled(true);shareButton.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{statusView.setText("Audit failed: "+e.getClass().getSimpleName()+" — "+e.getMessage());runButton.setEnabled(true);});}
        }).start();
    }
    private JSONObject audit(String address,String scope)throws Exception {
        URL url=new URL(address);HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","RedmiBugHunter/0.3 (authorized-security-audit)");
        JSONArray findings=new JSONArray();int status;
        try {
            status=c.getResponseCode();Map<String,List<String>> headers=c.getHeaderFields();
            String[][] checks={{"Strict-Transport-Security","HSTS header not observed"},{"Content-Security-Policy","CSP header not observed"},
                {"X-Content-Type-Options","X-Content-Type-Options header not observed"},{"Referrer-Policy","Referrer-Policy header not observed"}};
            for(String[] check:checks)if(c.getHeaderField(check[0])==null)issue(findings,"info",check[1],"Review in the application context; absence alone is not a confirmed vulnerability.");
            String xcto=c.getHeaderField("X-Content-Type-Options");
            if(xcto!=null&&!"nosniff".equalsIgnoreCase(xcto.trim()))issue(findings,"info","Unexpected X-Content-Type-Options","Expected nosniff.");
            int cookieNumber=0;
            for(Map.Entry<String,List<String>> entry:headers.entrySet()){
                if(entry.getKey()!=null&&entry.getKey().equalsIgnoreCase("Set-Cookie")&&entry.getValue()!=null){
                    for(String cookie:entry.getValue()){
                        cookieNumber++;String lower=cookie.toLowerCase(Locale.ROOT);
                        String name=cookie.split("=",2)[0].trim();
                        if(!lower.contains("; secure"))issue(findings,"info","Cookie missing Secure","Cookie #"+cookieNumber+" ("+name+") needs contextual review.");
                        if(!lower.contains("; httponly"))issue(findings,"info","Cookie missing HttpOnly","Cookie #"+cookieNumber+" ("+name+") may intentionally be accessible to scripts.");
                        if(!lower.contains("; samesite"))issue(findings,"info","Cookie missing SameSite","Cookie #"+cookieNumber+" ("+name+") needs contextual review.");
                    }
                }
            }
        }finally{c.disconnect();}
        JSONObject output=new JSONObject();output.put("tool","Redmi Bug Hunter");output.put("version","0.3.0");
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));
        output.put("timestamp_utc",format.format(new Date()));output.put("scope_host",scope);output.put("url",address);
        output.put("http_status",status);output.put("redirect_followed",false);output.put("checks","One HTTPS GET and passive header/cookie review");
        output.put("findings",findings);output.put("notice","Informational findings are not confirmed vulnerabilities.");return output;
    }
    private void issue(JSONArray items,String severity,String title,String detail)throws Exception{
        JSONObject f=new JSONObject();f.put("severity",severity);f.put("title",title);f.put("detail",detail);items.put(f);
    }
    private String pretty(JSONObject result)throws Exception {
        StringBuilder b=new StringBuilder();b.append("Host: ").append(result.getString("scope_host"));
        b.append("\nHTTP status: ").append(result.getInt("http_status"));
        JSONArray items=result.getJSONArray("findings");b.append("\nFindings: ").append(items.length());
        for(int i=0;i<items.length();i++){
            JSONObject f=items.getJSONObject(i);b.append("\n\n• ").append(f.getString("title"));b.append("\n  ").append(f.getString("detail"));
        }
        if(items.length()==0)b.append("\n\nNo issues identified by this limited baseline check.");return b.toString();
    }
    private void share(){
        if(reportJson.isEmpty())return;
        Intent intent=new Intent(Intent.ACTION_SEND);intent.setType("application/json");intent.putExtra(Intent.EXTRA_TEXT,reportJson);
        startActivity(Intent.createChooser(intent,"Share security report"));
    }
}
