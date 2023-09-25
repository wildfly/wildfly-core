/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.handlers;

import java.io.File;
import java.util.List;

import org.jboss.as.cli.CommandContext;

/**
 *
 * @author Alexey Loubyansky
 */
public class WindowsFilenameTabCompleter extends FilenameTabCompleter {

    public WindowsFilenameTabCompleter(CommandContext ctx) {
        super(ctx);
    }

    /**
     * The only supported syntax at command execution is fully quoted, e.g.:
     * "c:\Program Files\..." or not quoted at all. Completion supports only
     * these 2 syntaxes.
     */
    @Override
    void completeCandidates(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        if (candidates.isEmpty()) {
            if (buffer.startsWith("\"") && buffer.length() >= 2) {
                // Quotes are added back by super class.
                buffer = buffer.substring(1);
            }
            if (buffer.length() == 2 && buffer.endsWith(":")) {
                candidates.add(buffer + File.separator);
            }
        }
    }
}
