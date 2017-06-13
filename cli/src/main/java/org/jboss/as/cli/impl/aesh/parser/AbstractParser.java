/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.impl.aesh.parser;

import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.parser.OptionParser;
import org.aesh.command.parser.OptionParserException;
import org.aesh.parser.ParsedLineIterator;
import org.jboss.as.cli.CommandFormatException;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
abstract class AbstractParser implements OptionParser {

    public abstract int parse(String valueAndMore, ProcessedOption option, CLICommandInvocation ctx) throws CommandFormatException;

    @Override
    public void parse(ParsedLineIterator parsedLineIterator, ProcessedOption option) throws OptionParserException {
        String valueAndMore = parsedLineIterator.stringFromCurrentPosition();
        int offset;
        int i = valueAndMore.indexOf("=") + 1;
        String str = valueAndMore.substring(i);

        try {
            offset = parse(str, option, null);
        } catch (CommandFormatException ex) {
            throw new OptionParserException(ex.getLocalizedMessage(), ex);
        }

        updateOptionValue(option, str, offset);
        offset += 1;

        parsedLineIterator.updateIteratorPosition(offset + i);
    }

    private static void updateOptionValue(ProcessedOption option, String line, int offset) {
        if (option != null) {
            if (offset < 0 || offset > line.length() - 1) {
                offset = line.length() - 1;
            }
            String value = line.substring(0, offset + 1);
            option.addValue(value);
        }
    }

}
