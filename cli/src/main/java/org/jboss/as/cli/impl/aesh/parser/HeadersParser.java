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
package org.jboss.as.cli.impl.aesh.parser;

import org.aesh.command.impl.internal.ProcessedOption;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.impl.DefaultCallbackHandler;
import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.ParserUtil;
import org.jboss.as.cli.parsing.operation.HeaderListState;
import org.wildfly.core.cli.command.aesh.CLICommandInvocation;

/**
 * An header parser.
 *
 * @author jdenise@redhat.com
 */
public class HeadersParser extends AbstractParser {
    private final DefaultCallbackHandler callback = new DefaultCallbackHandler();
    private final DefaultParsingState initialState = new DefaultParsingState("INITIAL_STATE");

    public HeadersParser() {
        initialState.enterState('{', HeaderListState.INSTANCE_LEAVE_PARSING);
    }

    @Override
    public int parse(String valueAndMore, ProcessedOption option, CLICommandInvocation ctx) throws CommandFormatException {
        callback.reset();
        ParserUtil.parse(valueAndMore, callback, initialState);
        return callback.getEndOfParsing();
    }
}
