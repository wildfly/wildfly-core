/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;

import java.util.List;

/**
 * Command (argument) completer.
 *
 * @author Alexey Loubyansky
 */
public interface CommandLineCompleter {

    int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates);
}
