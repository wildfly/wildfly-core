/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
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
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 * Test case whether logging to syslog through UDP and TCP is possible if syslog server is restarted once and twice.
 *
 * Regression test case for https://bugzilla.redhat.com/show_bug.cgi?id=1295660.
 *
 * @author olukas
 */
@RunWith(WildflyTestRunner.class)
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
