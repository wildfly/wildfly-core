/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli;

/**
 * Service provider interface to add extra command handlers to the CLI using
 * <a href="http://docs.oracle.com/javase/tutorial/sound/SPI-intro.html">service provider mechanism</a>.
 *
 * @author <a href="http://jmesnil.net/">Jeff Mesnil</a> (c) 2012 Red Hat Inc.
 */
public interface CommandHandlerProvider {

    CommandHandler createCommandHandler(CommandContext ctx);

    boolean isTabComplete();

    String[] getNames();
}
