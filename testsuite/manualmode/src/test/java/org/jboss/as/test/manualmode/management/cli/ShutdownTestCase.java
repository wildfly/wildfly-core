/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.test.manualmode.management.cli;

import javax.inject.Inject;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.impl.CommandContextConfiguration;
import org.jboss.as.test.shared.TestSuiteEnvironment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.ServerControl;
import org.wildfly.core.testrunner.ServerController;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author jdenise@redhat.com
 */
@RunWith(WildflyTestRunner.class)
@ServerControl(manual = true)
public class ShutdownTestCase {
    @Inject
    private ServerController serverController;

    @Test
    public void test() throws Exception {
        serverController.start();
        CommandContextConfiguration config = new CommandContextConfiguration.Builder().
                setController("remote+http://"
                        + TestSuiteEnvironment.getServerAddress() + ":"
                        + TestSuiteEnvironment.getServerPort()).build();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext(config);
        ctx.handle("command-timeout set 60");
        ctx.handle("connect");
        ctx.handle("shutdown");
    }
}
