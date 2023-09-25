/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.test.manualmode.logging;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.jboss.as.test.integration.security.common.CoreUtils;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.jboss.as.test.syslogserver.BlockedAllProtocolsSyslogServerEventHandler;
import org.jboss.shrinkwrap.api.spec.JavaArchive;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.productivity.java.syslog4j.SyslogConstants;
import org.productivity.java.syslog4j.server.SyslogServer;
import org.productivity.java.syslog4j.server.SyslogServerEventIF;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test case whether logging to syslog through UDP and TCP is possible if syslog server is restarted once and twice.
 *
 * Regression test case for https://bugzilla.redhat.com/show_bug.cgi?id=1295660.
 *
 * @author olukas
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class ReconnectSyslogServerTestCase extends AbstractSyslogReconnectionTestCase {

    @Before
    public void setupContainer() throws Exception {
        container.start();
        host = CoreUtils.stripSquareBrackets(TestSuiteEnvironment.getServerAddress());
        final JavaArchive deployment = createDeployment();
        deploy(deployment);
        SyslogServer.shutdown();
        BlockedAllProtocolsSyslogServerEventHandler.initializeForProtocol(SyslogConstants.UDP);
        BlockedAllProtocolsSyslogServerEventHandler.initializeForProtocol(SyslogConstants.TCP);
        startSyslogServers(host);
        setupServer();
    }

    @After
    public void resetContainer() throws Exception {
        Assert.assertTrue(container.isStarted());
        tearDownServer();
        stopSyslogServers();
        undeploy();
        container.stop();
    }

    @Test
    public void testReconnectSyslogServer() throws Exception {
        final BlockingQueue<SyslogServerEventIF> udpQueue = BlockedAllProtocolsSyslogServerEventHandler.getQueue("udp");
        final BlockingQueue<SyslogServerEventIF> tcpQueue = BlockedAllProtocolsSyslogServerEventHandler.getQueue("tcp");
        udpQueue.clear();
        tcpQueue.clear();

        // logging before syslog restart
        makeLog();
        SyslogServerEventIF udpSyslogEvent = udpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the UDP syslog", udpSyslogEvent);
        SyslogServerEventIF tcpSyslogEvent = tcpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the TCP syslog", tcpSyslogEvent);

        stopSyslogServers();

        makeLog_syslogIsOffline();
        udpSyslogEvent = udpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the UDP syslog even if syslog server should be stopped", udpSyslogEvent);
        tcpSyslogEvent = tcpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the TCP syslog even if syslog server should be stopped", tcpSyslogEvent);

        startSyslogServers(host);

        // logging after first syslog restart
        makeLog();
        udpSyslogEvent = udpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the UDP syslog after first syslog server restart", udpSyslogEvent);
        tcpSyslogEvent = tcpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the TCP syslog after first syslog server restart", tcpSyslogEvent);

        stopSyslogServers();

        makeLog_syslogIsOffline();
        udpSyslogEvent = udpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the UDP syslog even if syslog server should be stopped", udpSyslogEvent);
        tcpSyslogEvent = tcpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the TCP syslog even if syslog server should be stopped", tcpSyslogEvent);

        startSyslogServers(host);

        // logging after second syslog restart
        makeLog();
        udpSyslogEvent = udpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the UDP syslog after second syslog server restart", udpSyslogEvent);
        tcpSyslogEvent = tcpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the TCP syslog after second syslog server restart", tcpSyslogEvent);

    }

}
