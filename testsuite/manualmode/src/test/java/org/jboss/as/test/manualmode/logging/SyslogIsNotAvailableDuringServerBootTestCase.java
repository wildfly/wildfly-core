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
import org.wildfly.core.testrunner.WildFlyRunner;

/**
 * Test whether application server can log into UDP and TCP syslog server even if this server is not available during server
 * boot. Syslog server is started after application server booting.
 *
 * Regression test for https://bugzilla.redhat.com/show_bug.cgi?id=1295665
 *
 * @author olukas
 */
@RunWith(WildFlyRunner.class)
@ServerControl(manual = true)
public class SyslogIsNotAvailableDuringServerBootTestCase extends AbstractSyslogReconnectionTestCase {

    @Before
    public void setupContainer() throws Exception {
        container.start();
        host = CoreUtils.stripSquareBrackets(TestSuiteEnvironment.getServerAddress());
        final JavaArchive deployment = createDeployment();
        deploy(deployment);
        SyslogServer.shutdown();
        BlockedAllProtocolsSyslogServerEventHandler.initializeForProtocol(SyslogConstants.UDP);
        BlockedAllProtocolsSyslogServerEventHandler.initializeForProtocol(SyslogConstants.TCP);
        setupServer();
        container.stop();
    }

    @After
    public void resetContainer() throws Exception {
        stopSyslogServers();
        Assert.assertTrue(container.isStarted());
        tearDownServer();
        undeploy();
        container.stop();
    }

    @Test
    public void testSyslogIsNotAvailableDuringServerBoot() throws Exception {
        container.start();
        Assert.assertTrue(container.isStarted());

        final BlockingQueue<SyslogServerEventIF> udpQueue = BlockedAllProtocolsSyslogServerEventHandler.getQueue("udp");
        final BlockingQueue<SyslogServerEventIF> tcpQueue = BlockedAllProtocolsSyslogServerEventHandler.getQueue("tcp");
        udpQueue.clear();
        tcpQueue.clear();

        // do log when syslog is not running
        makeLog_syslogIsOffline();
        SyslogServerEventIF udpSyslogEvent = udpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the UDP syslog even if syslog server should be stopped", udpSyslogEvent);
        SyslogServerEventIF tcpSyslogEvent = tcpQueue.poll(1 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNull("Message was logged into the TCP syslog even if syslog server should be stopped", tcpSyslogEvent);

        startSyslogServers(host);

        // do log when syslog is running
        makeLog();
        udpSyslogEvent = udpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the UDP syslog", udpSyslogEvent);
        tcpSyslogEvent = tcpQueue.poll(5 * ADJUSTED_SECOND, TimeUnit.MILLISECONDS);
        Assert.assertNotNull("No message was logged into the TCP syslog", tcpSyslogEvent);
    }

}
