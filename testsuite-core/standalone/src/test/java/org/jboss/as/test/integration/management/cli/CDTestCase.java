/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014, Red Hat, Inc., and individual contributors
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

package org.jboss.as.test.integration.management.cli;

import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineException;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.wildfly.core.testrunner.WildflyTestRunner;

/**
 *
 * @author Alexey Loubyansky
 */
@RunWith(WildflyTestRunner.class)
public class CDTestCase {

    private CommandContext ctx;

    @Before
    public void setup() throws Exception {
        ctx = CommandContextFactory.getInstance().newCommandContext();
        ctx.connectController();
    }

    @After
    public void cleanup() throws Exception {
        if(ctx != null) {
            ctx.terminateSession();
        }
    }

    @Test
    public void main() throws Exception {
        ctx.handle("cd /core-service=management");
        try {
            ctx.handle("cd /this-cant-exist=never");
            Assert.fail("The path should not have passed validation");
        } catch(CommandLineException e) {
            // expected
        }
        ctx.handle("cd /this-cant-exist=never --no-validation");
    }
}
