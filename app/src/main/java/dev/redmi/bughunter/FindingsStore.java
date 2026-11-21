package dev.redmi.bughunter;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Local, bounded storage for manually verified bug-bounty findings. */
public final class FindingsStore {
    private static final String PREFS="bug_bounty_findings";
    private static final String KEY_ITEMS="items_json";
    private static final int MAX_ITEMS=100;
    private static final int MAX_TITLE=200;
    private static final int MAX_TARGET=500;
    private static final int MAX_EVIDENCE=8000;
    private static final int MAX_REMEDIATION=4000;
    private static final int MAX_STATUS=30;
    private final SharedPreferences preferences;

    public FindingsStore(Context context){
        preferences=context.getApplicationContext().getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }

    public synchronized JSONObject add(String title,String severity,String target,String evidence,String remediation,String status)throws JSONException{
        String cleanTitle=bounded(title,MAX_TITLE,"Title");
        if(cleanTitle.isEmpty())throw new IllegalArgumentException("Title is required");
        String cleanSeverity=normalizeSeverity(severity);
        JSONObject item=new JSONObject();
        item.put("id",UUID.randomUUID().toString());
        item.put("created_at_utc",timestamp());
        item.put("title",cleanTitle);
        item.put("severity",cleanSeverity);
        item.put("target",bounded(target,MAX_TARGET,"Target"));
        item.put("evidence",bounded(evidence,MAX_EVIDENCE,"Evidence"));
        item.put("remediation",bounded(remediation,MAX_REMEDIATION,"Remediation"));
        String cleanStatus=bounded(status,MAX_STATUS,"Status").toLowerCase(Locale.ROOT);
        item.put("status",cleanStatus.isEmpty()?"open":cleanStatus);

        JSONArray current=load();
        JSONArray updated=new JSONArray();updated.put(item);
        for(int i=0;i<current.length()&&updated.length()<MAX_ITEMS;i++)updated.put(current.getJSONObject(i));
        save(updated);return new JSONObject(item.toString());
    }

    /** Returns an independent newest-first snapshot. */
    public synchronized JSONArray list()throws JSONException{
        JSONArray stored=load();List<JSONObject> items=new ArrayList<>();
        for(int i=0;i<stored.length();i++)items.add(stored.getJSONObject(i));
        Collections.sort(items,(left,right)->right.optString("created_at_utc","").compareTo(left.optString("created_at_utc","")));
        JSONArray result=new JSONArray();for(JSONObject item:items)result.put(new JSONObject(item.toString()));return result;
    }

    public synchronized boolean delete(String id)throws JSONException{
        String wanted=id==null?"":id.trim();if(wanted.isEmpty())return false;
        JSONArray current=load(),updated=new JSONArray();boolean removed=false;
        for(int i=0;i<current.length();i++){JSONObject item=current.getJSONObject(i);if(wanted.equals(item.optString("id")))removed=true;else updated.put(item);}
        if(removed)save(updated);return removed;
    }

    public synchronized void clear(){preferences.edit().remove(KEY_ITEMS).apply();}

    public synchronized String exportJson()throws JSONException{
        JSONArray snapshot=list();JSONObject export=new JSONObject();export.put("tool","Redmi Bug Hunter");export.put("exported_at_utc",timestamp());export.put("count",snapshot.length());export.put("findings",snapshot);return export.toString(2);
    }

    private JSONArray load(){
        String raw=preferences.getString(KEY_ITEMS,"[]");
        try{return new JSONArray(raw==null?"[]":raw);}catch(Exception ignored){return new JSONArray();}
    }

    private void save(JSONArray items){
        if(!preferences.edit().putString(KEY_ITEMS,items.toString()).commit())throw new IllegalStateException("Could not save findings");
    }

    private static String normalizeSeverity(String severity){
        String value=severity==null?"info":severity.trim().toLowerCase(Locale.ROOT);
        if(!value.equals("info")&&!value.equals("low")&&!value.equals("medium")&&!value.equals("high")&&!value.equals("critical"))throw new IllegalArgumentException("Unsupported severity");
        return value;
    }

    private static String bounded(String value,int maximum,String field){
        String clean=value==null?"":value.trim();if(clean.length()>maximum)throw new IllegalArgumentException(field+" exceeds "+maximum+" characters");return clean;
    }

    private static String timestamp(){
        SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(new Date());
    }
}
