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
package org.jboss.as.cli.impl;

import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.parsing.arguments.ArgumentValueState;

/**
 *
 * @author jdenise@redhat.com
 */
public class BytesCompleter implements CommandLineCompleter {
    public static final BytesCompleter INSTANCE = new BytesCompleter();

    private BytesCompleter(){}

    @Override
    public int complete(CommandContext ctx, String buffer, int cursor, List<String> candidates) {
        if (buffer == null || buffer.isEmpty() || !buffer.startsWith(ArgumentValueState.BYTES_TOKEN)) {
            candidates.add(ArgumentValueState.BYTES_TOKEN);
            return 0;
        } else // Limited support, offer separators when obvious.
        {
            if (!buffer.endsWith(",")) {
                int i = buffer.indexOf("{");
                String bytes = buffer.substring(i + 1);
                String[] split = bytes.split(",");
                if (split.length != 0) {
                    String lastByte = split[split.length - 1];
                    if (lastByte.startsWith("0x")) {
                        if (lastByte.length() == 4) {
                            candidates.add(buffer + ",");
                            return 0;
                        }
                    } else if (lastByte.startsWith("+") || lastByte.startsWith("-")) {
                        if (lastByte.length() == 4) {
                            candidates.add(buffer + ",");
                            return 0;
                        }
                    } else if (lastByte.length() == 3) {
                        candidates.add(buffer + ",");
                        return 0;
                    }
                }
            }
        }
        return -1;
    }
}
