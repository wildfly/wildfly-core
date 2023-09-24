/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.util.concurrent.atomic.AtomicReference;
import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.embedded.EmbeddedProcessLaunch;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class ShutdownHandlerTestCase {
    @Test
    public void test() throws CliInitializationException {
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        ShutdownHandler handler = new ShutdownHandler(ctx, new AtomicReference<EmbeddedProcessLaunch>());
        assertEquals(null, handler.getArgument(ctx, "--headers"));
    }
}
