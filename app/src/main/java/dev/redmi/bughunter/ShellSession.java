package dev.redmi.bughunter;

import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ShellSession {
    public interface Listener { void onOutput(String text); void onStopped(String reason); }
    private final File workingDirectory;
    private final Listener listener;
    private Process process;
    private BufferedWriter input;
    private Thread reader;
    private final File pidFile;

    public ShellSession(File workingDirectory,Listener listener){this.workingDirectory=workingDirectory;this.listener=listener;this.pidFile=new File(workingDirectory,".rbh_shell_pid");}
    public synchronized void start()throws Exception{
        if(isRunning())return;
        ProcessBuilder builder=new ProcessBuilder("/system/bin/sh");builder.directory(workingDirectory);builder.redirectErrorStream(true);
        process=builder.start();Process startedProcess=process;input=new BufferedWriter(new OutputStreamWriter(startedProcess.getOutputStream(),StandardCharsets.UTF_8));
        if(pidFile.exists())pidFile.delete();input.write("echo $$ > .rbh_shell_pid");input.newLine();input.flush();
        reader=new Thread(()->readLoop(startedProcess),"embedded-shell-output");reader.setDaemon(true);reader.start();
    }
    private void readLoop(Process startedProcess){
        try(InputStreamReader output=new InputStreamReader(startedProcess.getInputStream(),StandardCharsets.UTF_8)){char[] buffer=new char[2048];int count;while((count=output.read(buffer))!=-1)listener.onOutput(new String(buffer,0,count));}
        catch(Exception e){if(isRunning())listener.onOutput("\n[terminal read error: "+safe(e)+"]\n");}
        finally{listener.onStopped("Shell session ended.");}
    }
    public synchronized void execute(String command)throws Exception{
        if(!isRunning())throw new IllegalStateException("Shell is not running");input.write(command);input.newLine();input.flush();
    }
    public synchronized boolean isRunning(){return process!=null&&process.isAlive();}
    public synchronized void stop(){
        int shellPid=readShellPid();Set<Integer> descendants=shellPid<0?new HashSet<>():descendantPids(shellPid);
        try{if(input!=null)input.close();}catch(Exception ignored){}if(process!=null){process.destroy();if(process.isAlive())process.destroyForcibly();}for(int pid:descendants)try{android.os.Process.killProcess(pid);}catch(Exception ignored){}if(pidFile.exists())pidFile.delete();process=null;input=null;
    }
    private int readShellPid(){try(BufferedReader reader=new BufferedReader(new FileReader(pidFile))){return Integer.parseInt(reader.readLine().trim());}catch(Exception ignored){return -1;}}
    private static Set<Integer> descendantPids(long rootPid){
        Map<Integer,Integer> parents=new HashMap<>();File[] entries=new File("/proc").listFiles();if(entries==null)return new HashSet<>();
        for(File entry:entries){if(!entry.getName().matches("[0-9]+"))continue;try{int pid=Integer.parseInt(entry.getName()),parent=-1;try(BufferedReader status=new BufferedReader(new FileReader(new File(entry,"status")))){String line;while((line=status.readLine())!=null)if(line.startsWith("PPid:")){parent=Integer.parseInt(line.substring(5).trim());break;}}if(parent>=0)parents.put(pid,parent);}catch(Exception ignored){}}
        Set<Integer> found=new HashSet<>();boolean changed=true;while(changed){changed=false;for(Map.Entry<Integer,Integer> item:parents.entrySet())if((item.getValue()==rootPid||found.contains(item.getValue()))&&found.add(item.getKey()))changed=true;}return found;
    }
    private static String safe(Exception e){String value=e.getMessage();return value==null?e.getClass().getSimpleName():value;}
}
