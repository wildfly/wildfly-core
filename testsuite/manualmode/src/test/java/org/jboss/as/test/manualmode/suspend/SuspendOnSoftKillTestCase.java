/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.test.manualmode.suspend;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.READ_ATTRIBUTE_OPERATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUSPEND_STATE;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.SocketPermission;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.jboss.as.test.integration.common.HttpRequest;
import org.jboss.as.test.shared.PermissionUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.shared.TimeoutUtil;
import org.jboss.dmr.ModelNode;
import org.jboss.logging.Logger;
import org.jboss.msc.service.ServiceActivator;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.asset.StringAsset;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.AssumptionViolatedException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.UnsuccessfulOperationException;
import org.wildfly.core.testrunner.WildFlyRunner;
import org.wildfly.test.suspendresumeendpoint.SuspendResumeHandler;
import org.wildfly.test.suspendresumeendpoint.TestSuspendServiceActivator;
import org.wildfly.test.suspendresumeendpoint.TestUndertowService;
import org.xnio.IoUtils;

/**
 * WFCORE-3073. Tests that a softly killed process shuts down gracefully.
 *
 * @author Brian Stansberry
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class SuspendOnSoftKillTestCase {

    private static final String WEB_SUSPEND_JAR = "web-suspend.jar";

    @SuppressWarnings("unused")
    @Inject
    private static ServerController serverController;

    private String jbossArgs;
    private ProcessUtil processUtil;

    @After
    public void stopContainer() {
        try {
            // The os kills we use in the test don't result in serverController
            // being in an internal state that reflects its server has stopped.
            // So, regardless of whether we think it was killed, stop the container
            // If we did do the kill, this puts the serverController in the correct state.
            // And, if the test failed before we got to the kill, this will do
            // the necessary stop.
            serverController.stop();
        } finally {
            if (jbossArgs == null) {
                System.clearProperty("jboss.args");
            } else {
                System.setProperty("jboss.args", jbossArgs);
            }
        }
    }

    @AfterClass
    public static void cleanDeployment() {
        // When the test methods are done, start up the server again so we can clean out the deployment
        try {
            serverController.start();
            serverController.undeploy(WEB_SUSPEND_JAR);
        } catch (Exception e) {
            Logger.getLogger(SuspendOnSoftKillTestCase.class.getName()).error("Failed undeploying in stopContainer", e);
        } finally {
            // Stop the container
            serverController.stop();
        }
    }

    @Test
    public void testSuspendOnSoftKillPositiveTimeout() throws Exception {
        startContainer(15);
        suspendOnSoftKillTest();
    }

    @Test
    public void testSuspendOnSoftKillNegativeTimeout() throws Exception {
        startContainer(-1);
        suspendOnSoftKillTest();
    }

    private void startContainer(int suspendTimeout) throws Exception {

        suspendTimeout = suspendTimeout < 1 ? suspendTimeout : TimeoutUtil.adjust(suspendTimeout);

        jbossArgs = System.getProperty("jboss.args");
        String newArgs = jbossArgs == null ? "" : jbossArgs;
        newArgs += " -D[Standalone] -Dorg.wildfly.sigterm.suspend.timeout=" + suspendTimeout;
        System.setProperty("jboss.args", newArgs);

        if (File.pathSeparatorChar == ':'){
            processUtil = new UnixProcessUtil();
        } else {
            processUtil = new WindowsProcessUtil();
        }

        // Start the server
        serverController.start();

        // If there's already the deployment there from a previous test, remove it
        try {
            serverController.undeploy(WEB_SUSPEND_JAR);
        } catch (Exception ignored) {
            // assume it wasn't deployed. if it was we'll fail below
        }

        JavaArchive war = ShrinkWrap.create(JavaArchive.class, WEB_SUSPEND_JAR);
        war.addPackage(SuspendResumeHandler.class.getPackage());
        war.addAsServiceProvider(ServiceActivator.class, TestSuspendServiceActivator.class);
        war.addAsResource(new StringAsset("Dependencies: org.jboss.dmr, org.jboss.as.controller, io.undertow.core, org.jboss.as.server,org.wildfly.extension.request-controller, org.jboss.as.network\n"), "META-INF/MANIFEST.MF");
        war.addAsManifestResource(PermissionUtils.createPermissionsXmlAsset(
                new RuntimePermission("createXnioWorker"),
                new SocketPermission(TestSuiteEnvironment.getServerAddress() + ":8080", "listen,resolve"),
                new SocketPermission("*", "accept,resolve")
        ), "permissions.xml");
        serverController.deploy(war, WEB_SUSPEND_JAR);
    }

    private void suspendOnSoftKillTest() throws Exception {

        final String address = "http://" + TestSuiteEnvironment.getServerAddress() + ":8080/web-suspend";
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            // Send a request that will block
            Future<Object> result = executorService.submit(new Callable<Object>() {
                @Override
                public Object call() throws Exception {
                    return HttpRequest.get(address, 60, TimeUnit.SECONDS);
                }
            });

            Thread.sleep(TimeoutUtil.adjust(1000)); //nasty, but we need to make sure the HTTP request has started

            softKillServer();

            ModelNode op = new ModelNode();
            op.get(OP).set(READ_ATTRIBUTE_OPERATION);
            op.get(NAME).set(SUSPEND_STATE);

            String suspendState;
            long timeout = System.currentTimeMillis() + TimeoutUtil.adjust(10000);
            do {
                suspendState = serverController.getClient().executeForResult(op).asString();
                if (!"SUSPENDING".equals(suspendState)) {
                    Thread.sleep(50);
                } else {
                    break;
                }
            } while (System.currentTimeMillis() < timeout);
            if ("RUNNING".equals(suspendState) && processUtil instanceof WindowsProcessUtil) {
                // taskkill with no /f doesn't seem to soft kill Windows vms. I can't find
                // any other way to test this than trying taskkill without the hard-kill /f arg.
                // I tried wmic and terminate but that results in a hard kill.
                // So just ignore this test. The point of this test is to test how the shutdown hook
                // we install handles things. If the hook is never invoked by the VM we
                // can't do anything about that anyway.
                // I let the test run all the way to here before ignoring so that if
                // taskkill happens to actually work in some Windows environment, then
                // we get the coverage
                throw new AssumptionViolatedException("taskkill did not produce a 'soft kill' of the server on Windows");
            }
            Assert.assertEquals("SUSPENDING", suspendState);

            final HttpURLConnection conn = (HttpURLConnection) new URL(address).openConnection();
            try {
                conn.setDoInput(true);
                int responseCode = conn.getResponseCode();
                Assert.assertEquals(503, responseCode);
            } finally {
                conn.disconnect();
            }

            // Send a request that will trigger the first request to complete
            HttpRequest.get(address + "?" + TestUndertowService.SKIP_GRACEFUL + "=true", TimeoutUtil.adjust(30), TimeUnit.SECONDS);
            // Confirm 1st request completed
            Assert.assertEquals(SuspendResumeHandler.TEXT, result.get());

            // Check server behaves like a suspended, suspending or stopped server; which it will be is unknown.
            // If we are still in suspending state, wait a bit longer until get suspended or the server is down.
            try {
                timeout = System.currentTimeMillis() + TimeoutUtil.adjust(10000);
                do {
                    suspendState = serverController.getClient().executeForResult(op).asString();
                    if ("SUSPENDING".equals(suspendState)) {
                        Thread.sleep(50);
                    } else {
                        break;
                    }
                } while (System.currentTimeMillis() < timeout);
                Assert.assertEquals("SUSPENDED", suspendState);
            } catch (UnsuccessfulOperationException | RuntimeException ok) {
                // ignore; it's fine if the server's down
            }
        } finally {
            executorService.shutdown();
        }

        waitForServerShutdown();
    }

    private void softKillServer() {
        List<RunningProcess> runningProcesses = processUtil.getRunningProcesses();
        Assert.assertEquals("Incorrect number of processes -- " + runningProcesses, 1, runningProcesses.size());
        processUtil.softKillProcess(runningProcesses.get(0));
    }

    private void waitForServerShutdown() throws Exception {
        // Server should be suspended already with no in-flight requests, so it should stop quickly.
        // But we're willing to be patient and wait to be sure it stops even on a slow system.
        // The point is just to make sure it eventually stops, not to check how fast
        final long time = System.currentTimeMillis() + TimeoutUtil.adjust(20000);
        List<RunningProcess> runningProcesses;
        do {
            runningProcesses = processUtil.getRunningProcesses();
            if (runningProcesses.size() == 0){
                return;
            }
            Thread.sleep(200);
        } while(System.currentTimeMillis() < time);
        Assert.fail("Did not have all running processes " + runningProcesses);
    }

    private abstract static class ProcessUtil {

        List<String> initialProcessIds;

        ProcessUtil(){
            initialProcessIds = getInitialProcessIds();
        }

        List<String> getInitialProcessIds(){
            List<String> processes = listProcesses();
            List<String> ids = new ArrayList<String>();
            for (String proc : processes){
                ids.add(parseProcessId(proc));
            }
            return ids;
        }

        String parseProcessId(String proc){
            proc = proc.trim();
            int i = proc.indexOf(' ');
            return proc.substring(0, i);
        }

        List<RunningProcess> getRunningProcesses(){
            List<RunningProcess> running = new ArrayList<RunningProcess>();
            List<String> processes = listProcesses();
            for (String proc : processes){
                String id = parseProcessId(proc);
                if (!initialProcessIds.contains(id)){
                    if (proc.contains("-D[Standalone]")){
                        running.add(new RunningProcess(id, "Standalone"));
                    }
                }
            }
            return running;
        }

        List<String> listProcesses() {
            final Process p;
            try {
                p = Runtime.getRuntime().exec(getJpsCommand());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            List<String> processes = new ArrayList<String>();
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            try {
                String line;
                while ((line = input.readLine()) != null) {
                    if (line.contains("jboss-modules.jar")) {
                        processes.add(line);
                    }

                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                IoUtils.safeClose(input);
            }
            return processes;
        }

        void softKillProcess(RunningProcess process) {
            try {
                Runtime.getRuntime().exec(getSoftKillCommand(process));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        abstract String[] getJpsCommand();

        abstract String getSoftKillCommand(RunningProcess process);
    }

    private static class UnixProcessUtil extends ProcessUtil {
        @Override
        String[] getJpsCommand() {
            final File jreHome = new File(System.getProperty("java.home"));
            Assert.assertTrue("JRE home not found. File: " + jreHome.getAbsoluteFile(), jreHome.exists());
            if (TestSuiteEnvironment.isIbmJvm()) {
                return new String[] { "sh", "-c", "ps -ef | awk '{$1=\"\"; print $0}'" };
            } else {
                File jpsExe = new File(jreHome, "bin/jps");
                if (!jpsExe.exists()) {
                    jpsExe = new File(jreHome, "../bin/jps");
                }
                Assert.assertTrue("JPS executable not found. File: " + jpsExe, jpsExe.exists());
                return new String[] { jpsExe.getAbsolutePath(), "-l", "-v" };
            }
        }

        @Override
        String getSoftKillCommand(RunningProcess process) {
            return "kill -15 " + process.getProcessId();
        }
    }

    private static class WindowsProcessUtil extends ProcessUtil {

        @Override
        String[] getJpsCommand() {
            final File jreHome = new File(System.getProperty("java.home"));
            Assert.assertTrue("JRE home not found. File: " + jreHome.getAbsoluteFile(), jreHome.exists());
            File jpsExe = new File(jreHome, "bin/jps.exe");
            if (!jpsExe.exists()) {
                jpsExe = new File(jreHome, "../bin/jps.exe");
            }
            Assert.assertTrue("JPS executable not found. File: " + jpsExe, jpsExe.exists());
            return new String[] { jpsExe.getAbsolutePath(), "-l", "-v" };
        }

        @Override
        String getSoftKillCommand(RunningProcess process) {
            return "taskkill /pid " + process.getProcessId();
        }
    }

    private static class RunningProcess {
        final String processId;
        final String process;

        private RunningProcess(String processId, String process) {
            this.processId = processId;
            this.process = process;
        }

        String getProcessId() {
            return processId;
        }

        @Override
        public int hashCode() {
            return processId.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof RunningProcess && ((RunningProcess) obj).processId.equals(processId);
        }

        @Override
        public String toString() {
            return "Process{id=" + processId + ", process=" + process + "}";
        }
    }
}
