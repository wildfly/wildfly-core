package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.Util;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;

import static org.junit.Assert.fail;

/**
 * Tool for creating CLI processes for testing
 *
 * @author Joe Wertz ewertz@redhat.com
 */
public class CliProcessWrapper extends CliProcessBuilder {

    public CliProcessWrapper(){
        cliProcessWrapper = this;
    }

    private Process cliProcess;
    private volatile StringBuffer cliOutputBuffer = new StringBuffer();
    private volatile StringBuffer cliErrorBuffer = new StringBuffer();
    private BufferedWriter bufferedWriter = null;


    private boolean logErrors = false;
    public CliProcessWrapper setLogErrors(boolean logErrors){
        this.logErrors = logErrors;
        return this;
    }

    /**
     * Clear current output buffer
     */
    public void clearOutput(){
        cliOutputBuffer = new StringBuffer();
    }

    /**
     * Get existing output buffer.
     * @return CLI Output
     */
    public String getOutput(){
        String output = cliOutputBuffer.toString();
        if(output.contains("\u001B[0G\u001B[2K")){
            return output.replace("\u001B[0G\u001B[2K", "");
        }
        return output;
    }

    public String getErrors(){
        return cliErrorBuffer.toString();
    }

    /**
     * Start a CLI process with the current options and
     * wait for the output to show the startup has completed
     */
    public void executeInteractive(){
        executeInteractive(null);
    }

    /**
     * Start a CLI process with the current options and
     * wait for the output to show the startup has completed
     *
     * @param prompt - Expected prompt after startup
     * @return Returns true if the expected prompt is found. False if the timeout is reached.
     */
    public boolean executeInteractive(String prompt) {
        if( cliProcess  != null ){
            throw new RuntimeException("Process Already Started");
        }
        cliProcess = createProcess();
        new Thread(new CliResultsReader(cliProcess.getInputStream())).start();
        new Thread(new CliErrorsReader(cliProcess.getErrorStream())).start();
        bufferedWriter = new BufferedWriter(new OutputStreamWriter(cliProcess.getOutputStream()));

        return waitForPrompt(prompt);
    }

    /**
     * Run a CLI process with the current options and
     * wait for the process to end. If not ended within the timeout,
     * the process is destroyed.
     *
     * @return All output from the process after completion.
     */
    public String executeNonInteractive() throws IOException {
        if( cliProcess  != null ){
            throw new RuntimeException("Process Already Started");
        }
        cliProcess = createProcess();
        new Thread(new CliResultsReader(cliProcess.getInputStream())).start();
        new Thread(new CliErrorsReader(cliProcess.getErrorStream())).start();
        waitForClose();
        return cliOutputBuffer.toString();
    }

    /**
     * Push string to CLI input and wait for results.
     *
     * @param string - String pushed to CLI input
     */
    public void pushLineAndWaitForResults(String string) throws IOException {
        pushLineAndWaitForResults(string, null);
    }

    /**
     * Push string to CLI input and wait for results.
     *
     * @param string - String pushed to CLI input
     * @param prompt - Expected prompt after line is processed
     * @return Returns true if the expected prompt is found. False if the timeout is reached.
     */
    public boolean pushLineAndWaitForResults(String string, String prompt) throws IOException {
        pushToInput(string);
        return waitForPrompt(prompt);
    }

    /**
     * Push string to CLI input and wait for process to end
     *
     * @param string - String pushed to CLI input
     * @return Whether the process closed within the timeout or was forced.
     */
    public boolean pushLineAndWaitForClose(String string) throws IOException {
        pushToInput(string);
        return waitForClose();
    }

    /**
     * Push ctrl-c to CLI input and wait for process to end.
     * Separate from normal 'push' because no newLine can be pushed after the ctrl-c
     *
     * @return Whether the process closed within the timeout or was forced.
     */
    public boolean ctrlCAndWaitForClose() throws IOException {
        try {
            if(cliProcess != null){
                bufferedWriter.write('\u0003');
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            fail("Failed to push ctrl-c char, '\\u0003', to CLI input: " + e.getLocalizedMessage());
        }
        return waitForClose();
    }

    /**
     * Passthrough method to get the process exit value
     *
     * @return process exit value
     */
    public int getProcessExitValue() {
        return cliProcess.exitValue();
    }

    /**
     * Passthrough method to get the process exit value
     *
     * @return process exit value
     */
    public void destroyProcess() {
        cliProcess.destroy();
    }

    /**
     * Returns the last line in the current output buffer.
     * Unless the buffer has been cleared, this should be an active prompt waiting for user input.
     *
     * @return Last line in the current output buffer
     */
    public String getCurrentPrompt(){
        if(cliOutputBuffer.toString().contains("\n")) {
            return cliOutputBuffer.toString().substring(cliOutputBuffer.toString().lastIndexOf("\n")+1);
        }else{
            return cliOutputBuffer.toString();
        }
    }

    private void pushToInput(String line) {
        try {
            if(cliProcess != null){
                bufferedWriter.write(line);
                bufferedWriter.newLine();
                bufferedWriter.flush();
            }
        } catch (IOException e) {
            fail("Failed to push command '" + line + "' to CLI input: " + e.getLocalizedMessage());
        }
    }

    private int resultTimeout = 10000;
    private int resultInterval = 100;

    private boolean waitForPrompt(String prompt) {
        boolean success = false;
        boolean wait = true;
        int waitingTime = 0;

        while(wait) {

            try {
                Thread.sleep(resultInterval);
            } catch (InterruptedException e) {
                fail("Interrupted");
            }

            waitingTime += resultInterval;

            // If the timeout is reached, return regardless.
            if (waitingTime > resultTimeout) {
                wait = false;
            }

            // If the expected prompt is not in the output, keep waiting
            if (wait && outputHasPrompt(prompt)){
                success = true;
                wait = false;
            }
        }

        if(logErrors && !success){
            logErrors();
        }

        return success;
    }

    private boolean waitForClose() throws IOException {
        if( bufferedWriter != null ){
            bufferedWriter.close();
        }

        boolean closed = false;
        int waitingTime = 0;
        boolean wait = true;

        while(wait) {
            try {
                Thread.sleep(resultInterval);
            } catch (InterruptedException e) {
                fail("Interrupted");
            }

            waitingTime += resultInterval;

            try{
                cliProcess.exitValue();
                wait = false;
                closed = true;
            } catch(IllegalThreadStateException e) {
                // Windows check...
                if(cliOutputBuffer.toString().endsWith("Press any key to continue . . . ")){
                    pushLineAndWaitForClose("l");
                }
                // cli still working
            }

            // If the timeout is reached, destroy the process and return false
            if (waitingTime > resultTimeout) {
                cliProcess.destroy();
                wait = false;
            }
        }

        if(logErrors && !closed){
            logErrors();
        }

        return closed;
    }

    private boolean outputHasPrompt(String prompt){
        if(cliOutputBuffer.toString().contains("\n")) {
            if (prompt == null) {
                return cliOutputBuffer.toString().substring(cliOutputBuffer.toString().lastIndexOf("\n")+1).matches(".*[\\[].*[\\]].*");
            } else {
                return cliOutputBuffer.toString().substring(cliOutputBuffer.toString().lastIndexOf("\n")).contains(prompt);
            }
        }else{
            if (prompt == null) {
                return cliOutputBuffer.toString().matches(".*[\\[].*[\\]].*");
            } else {
                return cliOutputBuffer.toString().contains(prompt);
            }
        }
    }

    private class CliResultsReader implements Runnable {

        InputStream cliStream = null;

        public CliResultsReader(InputStream inputStream){
            cliStream = inputStream;
        }

        @Override
        public void run() {
            java.io.InputStreamReader isr = new java.io.InputStreamReader(cliStream);
            java.io.BufferedReader br = new java.io.BufferedReader(isr);

            try{
                int intCharacter = 0;
                Character character;
                // While the next character is a valid character and isn't null
                while ((intCharacter = br.read()) != -1 && (character = (char)intCharacter) != null) {
                    cliOutputBuffer.append(character);
                }
            } catch (IOException e) {
                fail("Failed to read commands output: " + e.getLocalizedMessage());
            }
        }

    }

    private class CliErrorsReader implements Runnable {

        InputStream cliStream = null;

        public CliErrorsReader(InputStream inputStream){
            cliStream = inputStream;
        }

        @Override
        public void run() {
            java.io.InputStreamReader isr = new java.io.InputStreamReader(cliStream);
            java.io.BufferedReader br = new java.io.BufferedReader(isr);

            try{
                int intCharacter = 0;
                Character character;
                // While the next character is a valid character and isn't null
                while ((intCharacter = br.read()) != -1 && (character = (char)intCharacter) != null) {
                    cliOutputBuffer.append(character);
                }
            } catch (IOException e) {
                fail("Failed to read commands errors: " + e.getLocalizedMessage());
            }
        }

    }

    private void logErrors() {
        //System.out.println("Failed to execute '" + cmd + "'");
        System.out.println("Command's output: '" + cliOutputBuffer + "'");

        java.io.InputStreamReader isr = new java.io.InputStreamReader(cliProcess.getErrorStream());
        java.io.BufferedReader br = new java.io.BufferedReader(isr);
        String line=null;
        try {
            line = br.readLine();
            if(line == null) {
                System.out.println("No output data for the command.");
            } else {
                StringBuilder buf = new StringBuilder(line);
                while((line = br.readLine()) != null) {
                    buf.append(Util.LINE_SEPARATOR);
                    buf.append(line);
                }
                System.out.println("Command's error log: '" + buf + "'");

            }
        } catch (IOException e) {
            fail("Failed to read command's error output: " + e.getLocalizedMessage());
        }
    }
}
