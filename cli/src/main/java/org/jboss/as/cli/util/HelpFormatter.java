/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Arrays;

import org.jboss.as.cli.CommandContext;

/**
 * This is a simple formatter which takes into account the current
 * terminal width when printing text content.
 *
 * Specifically, if the terminal width is less than the length of
 * the lines of content, it'll try to preserve the readability of the
 * text by splitting/breaking the lines and adding whitespace offsets
 * to more or less preserve the original structure of the text.
 *
 * @author Alexey Loubyansky
 *
 */
public class HelpFormatter {

    public static void format(CommandContext ctx, BufferedReader reader) throws IOException {

        int width = ctx.getTerminalWidth();
        if(width <= 0) {
             width = 80;
        }
        String line = reader.readLine();

        while(line != null) {
            final String next = reader.readLine();

            if(line.length() < width) {
                ctx.printLine(line);
            } else {
                int offset = 0;
                if(next != null && !next.isEmpty()) {
                    int i = 0;
                    while(i < next.length()) {
                        if(!Character.isWhitespace(next.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                } else {
                    int i = 0;
                    while(i < line.length()) {
                        if(!Character.isWhitespace(line.charAt(i))) {
                            offset = i;
                            break;
                        }
                        ++i;
                    }
                }

                final char[] offsetArr;
                if(offset == 0) {
                    offsetArr = null;
                } else {
                    offsetArr = new char[offset];
                    Arrays.fill(offsetArr, ' ');
                }

                int endLine = width;
                while(endLine >= 0) {
                    if(Character.isWhitespace(line.charAt(endLine - 1))) {
                       break;
                    }
                    --endLine;
                }
                if(endLine < 0) {
                    endLine = width;
                }

                ctx.printLine(line.substring(0, endLine));

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
                    if(endLine > line.length()) {
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

                    if(offsetArr != null) {
                        final StringBuilder lineBuf = new StringBuilder();
                        lineBuf.append(offsetArr);
                        lineBuf.append(line.substring(startLine, endLine));
                        ctx.printLine(lineBuf.toString());
                    } else {
                        ctx.printLine(line.substring(startLine, endLine));
                    }
                }
            }

            line = next;
        }
    }
}
