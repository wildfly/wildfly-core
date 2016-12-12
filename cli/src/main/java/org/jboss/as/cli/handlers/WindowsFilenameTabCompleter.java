/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat, Inc., and individual contributors
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
