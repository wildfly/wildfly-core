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
package org.jboss.as.cli.impl.aesh.converter;

import org.aesh.command.converter.Converter;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.HeadersArgumentValueConverter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;

/**
 *
 * @author jdenise@redhat.com
 */
public class HeadersConverter implements Converter<ModelNode, CLIConverterInvocation> {

    public static HeadersConverter INSTANCE = new HeadersConverter();

    public HeadersConverter() {

    }

    @Override
    public ModelNode convert(CLIConverterInvocation converterInvocation) throws OptionValidatorException {
        try {
            ModelNode mn = HeadersArgumentValueConverter.INSTANCE.
                    fromString(converterInvocation.getCommandContext(),
                            converterInvocation.getInput());
            if (!mn.getType().equals(ModelType.OBJECT)) {
                throw new OptionValidatorException("Invalid headers format " + converterInvocation.getInput());
            }
            return mn;
        } catch (CommandFormatException ex) {
            throw new OptionValidatorException(ex.getLocalizedMessage());
        }
    }
}
