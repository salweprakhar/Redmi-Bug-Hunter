package dev.redmi.bughunter;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
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
        place(root,label("◈  REDMI BUG HUNTER",23,CYAN,true),4);
        place(root,label("Authorized web/API security research · v0.1",13,MUTED,false),8);
        TextView tag=label("PASSIVE AUDIT  •  HTTPS ONLY",12,CYAN,true);
        tag.setGravity(Gravity.CENTER_VERTICAL);tag.setPadding(dp(13),dp(12),dp(13),dp(12));tag.setBackground(background(PANEL,10));
        place(root,tag,22);
        place(root,label("Target URL",13,WHITE,true),24);
        urlInput=field("https://your-test-site.example/");place(root,urlInput,8);
        place(root,label("Authorized host (exact hostname)",13,WHITE,true),17);
        hostInput=field("your-test-site.example");place(root,hostInput,8);
        permissionBox=new CheckBox(this);permissionBox.setText("I own this host or have explicit permission to test it.");
        permissionBox.setTextColor(WHITE);permissionBox.setTextSize(13);permissionBox.setButtonTintList(android.content.res.ColorStateList.valueOf(CYAN));
        place(root,permissionBox,18);
        runButton=button("Run security audit",CYAN);place(root,runButton,13);
        statusView=label("Ready. The scan makes one HTTPS GET request and never follows redirects.",12,MUTED,false);
        place(root,statusView,18);
        place(root,label("Security report",17,WHITE,true),25);
        reportView=label("No audit has been run yet.",12,MUTED,false);
        reportView.setTextIsSelectable(true);reportView.setPadding(dp(14),dp(15),dp(14),dp(15));reportView.setBackground(background(PANEL,12));
        place(root,reportView,10);
        shareButton=button("Share JSON report",Color.rgb(195,216,241));shareButton.setEnabled(false);place(root,shareButton,13);
        place(root,label("Findings are informational and require manual verification before reporting. No brute force, exploit delivery, or credential collection.",12,MUTED,false),20);
        runButton.setOnClickListener(v->scan());shareButton.setOnClickListener(v->share());
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
                runOnUiThread(()->{reportJson=json;reportView.setText(pretty(result));statusView.setText("Finished. No redirect was followed.");runButton.setEnabled(true);shareButton.setEnabled(true);});
            }catch(Exception e){runOnUiThread(()->{statusView.setText("Audit failed: "+e.getClass().getSimpleName()+" — "+e.getMessage());runButton.setEnabled(true);});}
        }).start();
    }
    private JSONObject audit(String address,String scope)throws Exception {
        URL url=new URL(address);HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET");c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent","RedmiBugHunter/0.1 (authorized-security-audit)");
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
        JSONObject output=new JSONObject();output.put("tool","Redmi Bug Hunter");output.put("version","0.1.0");
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
