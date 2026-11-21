package dev.redmi.bughunter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** A deliberately small, bounded crawler for explicitly authorized HTTPS hosts. */
public final class ReconEngine {
    public static final int MAX_REQUESTS=10;
    public static final int MAX_DEPTH=2;
    public static final int MAX_RESPONSE_BYTES=262144;
    public static final long MIN_DELAY_MS=500L;

    public interface ProgressListener { void onProgress(int completed,int maximum,String url); }

    private static final Pattern TITLE=Pattern.compile("(?is)<title[^>]*>(.*?)</title>");
    private static final Pattern LINK=Pattern.compile("(?is)<a\\b[^>]*?\\bhref\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern SCRIPT=Pattern.compile("(?is)<script\\b[^>]*?\\bsrc\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern FORM=Pattern.compile("(?is)<form\\b([^>]*)>");
    private static final Pattern ACTION=Pattern.compile("(?is)\\baction\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern METHOD=Pattern.compile("(?is)\\bmethod\\s*=\\s*(['\"])(.*?)\\1");
    private static final Pattern SITEMAP_LOCATION=Pattern.compile("(?is)<loc\\b[^>]*>(.*?)</loc>");

    private final AtomicBoolean cancelled=new AtomicBoolean(false);
    private volatile HttpURLConnection activeConnection;
    private long lastRequestStarted;

    public void cancel(){cancelled.set(true);HttpURLConnection connection=activeConnection;if(connection!=null)connection.disconnect();}
    public boolean isCancelled(){return cancelled.get();}

    public JSONObject crawl(String startAddress,String authorizedHost,ProgressListener listener)throws Exception{
        cancelled.set(false);lastRequestStarted=0L;
        String scope=normalizeScope(authorizedHost);URI start=validate(startAddress,scope);
        ArrayDeque<Target> queue=new ArrayDeque<>();Set<String> queued=new HashSet<>(),visited=new HashSet<>();
        enqueue(queue,queued,start,0,"start");
        URI origin=new URI("https",null,scope,-1,"/",null,null);
        enqueue(queue,queued,origin.resolve("/robots.txt"),0,"standard-discovery");
        enqueue(queue,queued,origin.resolve("/.well-known/security.txt"),0,"standard-discovery");
        enqueue(queue,queued,origin.resolve("/sitemap.xml"),0,"standard-discovery");
        JSONArray pages=new JSONArray();LinkedHashSet<String> allUrls=new LinkedHashSet<>();allUrls.add(canonical(start));
        int completed=0;
        while(!queue.isEmpty()&&completed<MAX_REQUESTS&&!cancelled.get()){
            Target target=queue.removeFirst();String key=canonical(target.uri);if(!visited.add(key))continue;
            waitForRateLimit();if(cancelled.get())break;
            if(listener!=null)listener.onProgress(completed,MAX_REQUESTS,key);
            JSONObject page=fetch(target,scope,queue,queued,visited,allUrls);pages.put(page);completed++;
            if(listener!=null)listener.onProgress(completed,MAX_REQUESTS,key);
        }
        JSONObject limits=new JSONObject();limits.put("max_requests",MAX_REQUESTS);limits.put("max_depth",MAX_DEPTH);limits.put("minimum_delay_ms",MIN_DELAY_MS);limits.put("max_response_bytes",MAX_RESPONSE_BYTES);
        JSONObject report=new JSONObject();report.put("tool","Redmi Bug Hunter Recon");report.put("version","1.0");report.put("timestamp_utc",isoDate(new Date()));report.put("scope_host",scope);report.put("start_url",canonical(start));report.put("limits",limits);report.put("requests_completed",completed);report.put("cancelled",cancelled.get());report.put("pages",pages);report.put("discovered_urls",new JSONArray(allUrls));return report;
    }

    private JSONObject fetch(Target target,String scope,ArrayDeque<Target> queue,Set<String> queued,Set<String> visited,Set<String> allUrls)throws Exception{
        String address=canonical(target.uri);JSONObject page=new JSONObject();page.put("url",address);page.put("depth",target.depth);page.put("source",target.source);
        HttpsURLConnection connection=null;long started=System.nanoTime();
        try{
            connection=(HttpsURLConnection)new URL(address).openConnection();activeConnection=connection;connection.setRequestMethod("GET");connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);connection.setRequestProperty("User-Agent","RedmiBugHunter/0.8 (authorized-bounded-recon)");connection.setRequestProperty("Accept","text/html,application/xhtml+xml,application/xml,text/plain;q=0.9,*/*;q=0.2");lastRequestStarted=System.currentTimeMillis();
            int status=connection.getResponseCode();String type=connection.getContentType(),location=connection.getHeaderField("Location");page.put("status",status);page.put("response_time_ms",(System.nanoTime()-started)/1000000L);page.put("content_type",type==null?JSONObject.NULL:type);page.put("redirect_location",location==null?JSONObject.NULL:location);
            InputStream stream=status>=400?connection.getErrorStream():connection.getInputStream();Body body=readBody(stream);page.put("content_bytes",body.bytes.length);page.put("truncated",body.truncated);page.put("sha256",sha256(body.bytes));
            String text=new String(body.bytes,charset(type));JSONArray links=new JSONArray(),scripts=new JSONArray(),forms=new JSONArray(),parameters=queryParameters(target.uri);
            page.put("title",extractTitle(text));
            if(isText(type,text)){
                collectLinks(text,target.uri,scope,target.depth,queue,queued,visited,allUrls,links);
                collectScripts(text,target.uri,scope,allUrls,scripts);
                collectForms(text,target.uri,scope,allUrls,forms);
                if(target.uri.getPath().equals("/robots.txt"))page.put("robots",parseRobots(text,target.uri,scope,queue,queued,visited,allUrls));
                if(target.uri.getPath().endsWith("sitemap.xml")||typeContains(type,"xml"))page.put("sitemap_urls",parseSitemap(text,target.uri,scope,queue,queued,visited,allUrls));
                if(target.uri.getPath().endsWith("security.txt"))page.put("security_txt",parseSecurityText(text));
            }
            if(location!=null&&target.depth<MAX_DEPTH){URI redirect=safeResolve(target.uri,location,scope);if(redirect!=null){allUrls.add(canonical(redirect));enqueueIfNew(queue,queued,visited,redirect,target.depth+1,"redirect");}}
            page.put("links",links);page.put("scripts",scripts);page.put("forms",forms);page.put("query_parameters",parameters);return page;
        }catch(Exception error){
            if(cancelled.get())page.put("error","Cancelled");else page.put("error",safe(error));page.put("response_time_ms",(System.nanoTime()-started)/1000000L);return page;
        }finally{activeConnection=null;if(connection!=null)connection.disconnect();}
    }

    private void collectLinks(String html,URI base,String scope,int depth,ArrayDeque<Target> queue,Set<String> queued,Set<String> visited,Set<String> allUrls,JSONArray output)throws Exception{
        Matcher matcher=LINK.matcher(html);Set<String> local=new LinkedHashSet<>();while(matcher.find()&&local.size()<100){URI uri=safeResolve(base,decodeEntities(matcher.group(2)),scope);if(uri==null)continue;String value=canonical(uri);if(local.add(value)){output.put(value);allUrls.add(value);if(depth<MAX_DEPTH)enqueueIfNew(queue,queued,visited,uri,depth+1,"html-link");}}
    }
    private void collectScripts(String html,URI base,String scope,Set<String> allUrls,JSONArray output)throws Exception{
        Matcher matcher=SCRIPT.matcher(html);Set<String> local=new LinkedHashSet<>();while(matcher.find()&&local.size()<100){URI uri=safeResolve(base,decodeEntities(matcher.group(2)),scope);if(uri!=null&&local.add(canonical(uri))){String value=canonical(uri);output.put(value);allUrls.add(value);}}
    }
    private void collectForms(String html,URI base,String scope,Set<String> allUrls,JSONArray output)throws Exception{
        Matcher matcher=FORM.matcher(html);int count=0;while(matcher.find()&&count++<50){String attributes=matcher.group(1),method="GET",action=canonical(base);Matcher methodMatch=METHOD.matcher(attributes),actionMatch=ACTION.matcher(attributes);if(methodMatch.find())method=methodMatch.group(2).trim().toUpperCase(Locale.ROOT);if(actionMatch.find()){URI uri=safeResolve(base,decodeEntities(actionMatch.group(2)),scope);action=uri==null?"outside-scope-or-invalid":canonical(uri);if(uri!=null)allUrls.add(action);}JSONObject form=new JSONObject();form.put("method",method);form.put("action",action);output.put(form);}
    }
    private JSONArray parseRobots(String text,URI base,String scope,ArrayDeque<Target> queue,Set<String> queued,Set<String> visited,Set<String> allUrls)throws Exception{
        JSONArray entries=new JSONArray();for(String line:text.split("\\r?\\n")){String clean=line.split("#",2)[0].trim();int colon=clean.indexOf(':');if(colon<=0)continue;String name=clean.substring(0,colon).trim(),value=clean.substring(colon+1).trim();if(!(name.equalsIgnoreCase("Allow")||name.equalsIgnoreCase("Disallow")||name.equalsIgnoreCase("Sitemap")))continue;JSONObject entry=new JSONObject();entry.put("directive",name);entry.put("value",value);entries.put(entry);if(name.equalsIgnoreCase("Sitemap")){URI uri=safeResolve(base,value,scope);if(uri!=null){allUrls.add(canonical(uri));enqueueIfNew(queue,queued,visited,uri,1,"robots-sitemap");}}}return entries;
    }
    private JSONArray parseSitemap(String text,URI base,String scope,ArrayDeque<Target> queue,Set<String> queued,Set<String> visited,Set<String> allUrls)throws Exception{
        JSONArray urls=new JSONArray();Matcher matcher=SITEMAP_LOCATION.matcher(text);Set<String> local=new LinkedHashSet<>();while(matcher.find()&&local.size()<100){URI uri=safeResolve(base,decodeEntities(matcher.group(1).trim()),scope);if(uri!=null&&local.add(canonical(uri))){String value=canonical(uri);urls.put(value);allUrls.add(value);enqueueIfNew(queue,queued,visited,uri,1,"sitemap");}}return urls;
    }
    private JSONObject parseSecurityText(String text)throws Exception{JSONObject result=new JSONObject();for(String line:text.split("\\r?\\n")){if(line.startsWith("#"))continue;int colon=line.indexOf(':');if(colon<=0)continue;String key=line.substring(0,colon).trim(),value=line.substring(colon+1).trim();if(!key.isEmpty()&&!value.isEmpty()){JSONArray values=result.optJSONArray(key);if(values==null){values=new JSONArray();result.put(key,values);}values.put(value);}}return result;}

    private void waitForRateLimit()throws InterruptedException{long remaining=MIN_DELAY_MS-(System.currentTimeMillis()-lastRequestStarted);while(remaining>0&&!cancelled.get()){Thread.sleep(Math.min(remaining,100L));remaining=MIN_DELAY_MS-(System.currentTimeMillis()-lastRequestStarted);}}
    private static void enqueue(ArrayDeque<Target> queue,Set<String> queued,URI uri,int depth,String source){String key=canonical(uri);if(queued.add(key))queue.addLast(new Target(uri,depth,source));}
    private static void enqueueIfNew(ArrayDeque<Target> queue,Set<String> queued,Set<String> visited,URI uri,int depth,String source){String key=canonical(uri);if(!visited.contains(key))enqueue(queue,queued,uri,depth,source);}
    private static String normalizeScope(String host){String value=host==null?"":host.trim().toLowerCase(Locale.ROOT);if(!validHostname(value))throw new IllegalArgumentException("Enter one exact authorized hostname");return value;}
    private static boolean validHostname(String host){if(host==null||host.length()>253||host.startsWith(".")||host.endsWith("."))return false;String[] labels=host.split("\\.",-1);for(String item:labels)if(item.isEmpty()||item.length()>63||item.startsWith("-")||item.endsWith("-")||!item.matches("[a-z0-9-]+"))return false;return true;}
    private static URI validate(String address,String scope)throws Exception{URI uri=new URI(address);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!uri.getHost().equalsIgnoreCase(scope)||uri.getPort()!=-1||uri.getUserInfo()!=null||uri.getRawFragment()!=null)throw new IllegalArgumentException("URL must be HTTPS on the exact authorized hostname, without a custom port or fragment");return uri.normalize();}
    private static URI safeResolve(URI base,String reference,String scope){try{if(reference==null||reference.trim().isEmpty()||reference.startsWith("#")||reference.toLowerCase(Locale.ROOT).startsWith("javascript:")||reference.toLowerCase(Locale.ROOT).startsWith("mailto:")||reference.toLowerCase(Locale.ROOT).startsWith("tel:"))return null;URI uri=base.resolve(reference.trim());uri=new URI(uri.getScheme(),uri.getUserInfo(),uri.getHost(),uri.getPort(),uri.getPath(),uri.getQuery(),null).normalize();if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||!uri.getHost().equalsIgnoreCase(scope)||uri.getPort()!=-1||uri.getUserInfo()!=null)return null;return uri;}catch(Exception ignored){return null;}}
    private static String canonical(URI uri){return uri.toASCIIString();}
    private static JSONArray queryParameters(URI uri)throws Exception{JSONArray result=new JSONArray();Set<String> names=new LinkedHashSet<>();String query=uri.getRawQuery();if(query!=null)for(String pair:query.split("&")){String name=pair.split("=",2)[0];try{name=URLDecoder.decode(name,StandardCharsets.UTF_8.name());}catch(Exception ignored){}if(!name.isEmpty())names.add(name);}for(String name:names)result.put(name);return result;}
    private static String extractTitle(String text){Matcher matcher=TITLE.matcher(text);return matcher.find()?decodeEntities(matcher.group(1)).replaceAll("\\s+"," ").trim():"";}
    private static boolean isText(String type,String body){return type==null||typeContains(type,"text")||typeContains(type,"html")||typeContains(type,"xml")||typeContains(type,"json")||body.trim().startsWith("<");}
    private static boolean typeContains(String type,String value){return type!=null&&type.toLowerCase(Locale.ROOT).contains(value);}
    private static Charset charset(String type){if(type!=null)for(String part:type.split(";")){String value=part.trim();if(value.toLowerCase(Locale.ROOT).startsWith("charset="))try{return Charset.forName(value.substring(8).replace("\"","").trim());}catch(Exception ignored){}}return StandardCharsets.UTF_8;}
    private static String decodeEntities(String value){return value.replace("&amp;","&").replace("&quot;","\"").replace("&#39;","'").replace("&lt;","<").replace("&gt;",">");}
    private static Body readBody(InputStream stream)throws Exception{if(stream==null)return new Body(new byte[0],false);try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int total=0,read;while(total<=MAX_RESPONSE_BYTES&&(read=input.read(buffer,0,Math.min(buffer.length,MAX_RESPONSE_BYTES+1-total)))!=-1){output.write(buffer,0,read);total+=read;}byte[] all=output.toByteArray();if(all.length<=MAX_RESPONSE_BYTES)return new Body(all,false);byte[] limited=new byte[MAX_RESPONSE_BYTES];System.arraycopy(all,0,limited,0,MAX_RESPONSE_BYTES);return new Body(limited,true);}}
    private static String sha256(byte[] input)throws Exception{byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(input);StringBuilder value=new StringBuilder();for(byte item:digest)value.append(String.format(Locale.US,"%02x",item&0xff));return value.toString();}
    private static String isoDate(Date date){SimpleDateFormat format=new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",Locale.US);format.setTimeZone(TimeZone.getTimeZone("UTC"));return format.format(date);}
    private static String safe(Exception error){String value=error.getMessage();return value==null||value.trim().isEmpty()?error.getClass().getSimpleName():value;}
    private static final class Target{final URI uri;final int depth;final String source;Target(URI uri,int depth,String source){this.uri=uri;this.depth=depth;this.source=source;}}
    private static final class Body{final byte[] bytes;final boolean truncated;Body(byte[] bytes,boolean truncated){this.bytes=bytes;this.truncated=truncated;}}
}
