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
package org.jboss.as.cli.impl.aesh;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.handlers.CommandHandlerWithHelp;
import org.jboss.as.protocol.StreamUtils;
import org.wildfly.security.manager.WildFlySecurityManager;

/**
 *
 * @author jdenise@redhat.com
 */
public class HelpSupport {

    public static void printHelp(CommandContext ctx) {
        ctx.printLine(printHelp(ctx, "help"));
    }

    public static String printHelp(CommandContext ctx, String filename) {
        filename = "help/" + filename + ".txt";
        InputStream helpInput = WildFlySecurityManager.getClassLoaderPrivileged(CommandHandlerWithHelp.class).getResourceAsStream(filename);
        if (helpInput != null) {
            BufferedReader reader = new BufferedReader(new InputStreamReader(helpInput));
            try {
                /*                String helpLine = reader.readLine();
                while(helpLine != null) {
                    ctx.printLine(helpLine);
                    helpLine = reader.readLine();
                }
                 */
                return format(ctx, reader);
            } catch (java.io.IOException e) {
                return "Failed to read " + filename + ". " + e.getLocalizedMessage();
            } finally {
                StreamUtils.safeClose(reader);
            }
        } else {
            return "Failed to locate command description " + filename;
        }
    }

    public static String format(CommandContext ctx, BufferedReader reader) throws IOException {

        StringBuilder builder = new StringBuilder();
        int width = ctx.getTerminalWidth();
        if (width <= 0) {
            width = 80;
        }
        String line = reader.readLine();

        while (line != null) {
            final String next = reader.readLine();

            if (line.length() < width) {
                builder.append(line).append("\n");
            } else {
                int offset = 0;
                if (next != null && !next.isEmpty()) {
                    int i = 0;
                    while (i < next.length()) {
                        if (!Character.isWhitespace(next.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                } else {
                    int i = 0;
                    while (i < line.length()) {
                        if (!Character.isWhitespace(line.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                }

                final char[] offsetArr;
                if (offset == 0) {
                    offsetArr = null;
                } else {
                    offsetArr = new char[offset];
                    Arrays.fill(offsetArr, ' ');
                }

                int endLine = width;
                while (endLine >= 0) {
                    if (Character.isWhitespace(line.charAt(endLine - 1))) {
                        break;
                    }
                    --endLine;
                }
                if (endLine < 0) {
                    endLine = width;
                }

                builder.append(line.substring(0, endLine)).append("\n");

                int lineIndex = endLine;
                while (lineIndex < line.length()) {
                    int startLine = lineIndex;
                    endLine = Math.min(startLine + width - offset, line.length());

                    while (startLine < endLine) {
                        if (!Character.isWhitespace(line.charAt(startLine))) {
                            break;
                        }
                        ++startLine;
                    }
                    if (startLine == endLine) {
                        startLine = lineIndex;
                    }

                    endLine = startLine + width - offset;
                    if (endLine > line.length()) {
                        endLine = line.length();
                    } else {
                        while (endLine > startLine) {
                            if (Character.isWhitespace(line.charAt(endLine - 1))) {
                                --endLine;
                                break;
                            }
                            --endLine;
                        }
                        if (endLine == startLine) {
                            endLine = Math.min(startLine + width - offset, line.length());
                        }
                    }
                    lineIndex = endLine;

                    if (offsetArr != null) {
                        final StringBuilder lineBuf = new StringBuilder();
                        lineBuf.append(offsetArr);
                        lineBuf.append(line.substring(startLine, endLine));
                        builder.append(lineBuf.toString()).append("\n");
                    } else {
                        builder.append(line.substring(startLine, endLine)).append("\n");
                    }
                }
            }

            line = next;
        }
        return builder.toString();
    }
}
