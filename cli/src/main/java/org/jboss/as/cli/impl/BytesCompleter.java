/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
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
