package net.phonex.util.system;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;

public class CmdExecutor {
	private static final String THIS_FILE = "CmdExecutor";
    
    /**
     * Enum defining possible ways of handling process output streams.
     */
    public static enum OutputOpt {
        EXECUTE_STDOUT_ONLY,
        EXECUTE_STDERR_ONLY,
        EXECUTE_STD_COMBINE,
        EXECUTE_STD_SEPARATE
    }
    
    /**
     * Wrapper class for job execution result.
     */
    public static class CmdExecutionResult {
        public int exitValue;
        public String stdErr;
        public String stdOut;
        public long time;
    }
    
    /**
     * Simple helper for executing a command.
     * 
     * @param command
     * @param outOpt
     * @return
     * @throws IOException
     * @throws InterruptedException 
     */
    public static CmdExecutionResult execute(final String command, OutputOpt outOpt) throws IOException, InterruptedException{
        return execute(command, outOpt, null);
    }
    
    /**
     * Simple helper for executing a command.
     * 
     * @param command
     * @param outOpt
     * @param workingDir
     * @return
     * @throws IOException
     * @throws InterruptedException 
     */
    public static CmdExecutionResult execute(final String command, OutputOpt outOpt, File workingDir) throws IOException, InterruptedException{
        CmdExecutionResult res = new CmdExecutionResult();
        
        // Execute motelist command
        Process p = workingDir == null ? 
                Runtime.getRuntime().exec(command) :
                Runtime.getRuntime().exec(command, null, workingDir);

        // If interested only in stdErr, single thread is OK, otherwise 2 stream
        // reading threads are needed.
        if (outOpt==OutputOpt.EXECUTE_STDERR_ONLY || outOpt==OutputOpt.EXECUTE_STDOUT_ONLY){
            StringBuilder sb = new StringBuilder();
            BufferedReader bri = new BufferedReader(new InputStreamReader(
                            outOpt==OutputOpt.EXECUTE_STDERR_ONLY ? p.getErrorStream() : p.getInputStream()));
            
            String line;
            while ((line = bri.readLine()) != null) {
                sb.append(line).append("\n");
            }
            bri.close();
            
            if (outOpt==OutputOpt.EXECUTE_STDOUT_ONLY)
                res.stdOut = sb.toString();
            else if (outOpt==OutputOpt.EXECUTE_STDERR_ONLY)
                res.stdErr = sb.toString();
            
            // synchronous call, wait for command completion
            p.waitFor();
        } else if (outOpt==OutputOpt.EXECUTE_STD_COMBINE){
            // Combine both streams together
            StreamMerger sm = new StreamMerger(p.getInputStream(), p.getErrorStream());
            sm.run();
            
            // synchronous call, wait for command completion
            p.waitFor();
            
            res.stdOut = sm.getOutput();
        } else {
            // Consume streams, older jvm's had a memory leak if streams were not read,
            // some other jvm+OS combinations may block unless streams are consumed.
            StreamGobbler errorGobbler  = new StreamGobbler(p.getErrorStream(), true);
            StreamGobbler outputGobbler = new StreamGobbler(p.getInputStream(), true);
            errorGobbler.start();
            outputGobbler.start();
            
            // synchronous call, wait for command completion
            p.waitFor();
            
            res.stdErr = errorGobbler.getOutput();
            res.stdOut = outputGobbler.getOutput();
        }
        
        res.exitValue = p.exitValue();
        return res;
    }
    
    /**
     * Private helper class - holds info from parsing udev file
     */
    protected static class NodeConfigRecordLocal{
        private String device;
        private Integer nodeid;

        public String getDevice() {
            return device;
        }

        public void setDevice(String device) {
            this.device = device;
        }

        public Integer getNodeid() {
            return nodeid;
        }

        public void setNodeid(Integer nodeid) {
            this.nodeid = nodeid;
        }
    }
    
    public static class StreamGobbler extends Thread {
        private InputStream is;
        private StringBuffer output;
        private volatile boolean completed; // mark volatile to guarantee a thread safety
        private OutputStream os;

        public StreamGobbler(InputStream is, boolean readStream) {
            this.is = is;
            this.output = (readStream ? new StringBuffer(256) : null);
        }
        
        public StreamGobbler(InputStream is, StringBuffer buff) {
            this.is = is;
            this.output = buff;
        }

        @Override
        public void run() {
            completed = false;
            PrintWriter writer = os==null ? null : new PrintWriter(os);
            
            try {
                String NL = System.getProperty("line.separator", "\r\n");

                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                while ((line = br.readLine()) != null) {
                    if (output != null) {
                        output.append(line);
                        output.append(NL);
                    }
                    
                    if (writer != null){
                        writer.write(line+NL);
                        writer.flush();
                    }
                }
                
                if (writer != null){
                    writer.flush();
                }
                
            } catch (IOException ex) {
            	Log.e(THIS_FILE, "Exception in reading stream", ex);
            }
            completed = true;
        }

        /**
         * Get inputstream buffer or null if stream was not consumed.
         *
         * @return
         */
        public String getOutput() {
            return (output != null ? output.toString() : null);
        }

        /**
         * Is input stream completed.
         *
         * @return
         */
        public boolean isCompleted() {
            return completed;
        }
        
        public OutputStream getOs() {
            return os;
        }

        public void setOs(OutputStream os) {
            this.os = os;
        }
    }
    
    /**
     * Merges two String input streams to one.
     * @author ph4r05
     */
    public static class StreamMerger {
        private final InputStream is1;
        private final InputStream is2;
        private final StreamGobbler g1;
        private final StreamGobbler g2;
        
        private final StringBuffer output = new StringBuffer(256);

        public StreamMerger(InputStream is1, InputStream is2) {
            this.is1 = is1;
            this.is2 = is2;
            g1 = new StreamGobbler(is1, output);
            g2 = new StreamGobbler(is2, output);
        }
        
        public void setOutputStream(OutputStream os){
            g1.setOs(os);
            g2.setOs(os);
        }
        
        public void run() {
            g1.start();
            g2.start();
        }
        
        /**
         * Get inputstream buffer or null if stream was not consumed.
         *
         * @return
         */
        public String getOutput() {
            return (output != null ? output.toString() : null);
        }

        /**
         * Is input stream completed.
         *
         * @return
         */
        public boolean isCompleted() {
            return (g1==null || g1.isCompleted()) && (g2==null || g2.isCompleted());
        }
    }
}
