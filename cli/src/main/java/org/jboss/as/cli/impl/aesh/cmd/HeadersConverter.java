/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.impl.aesh.cmd;

import org.aesh.command.converter.Converter;
import org.aesh.command.validator.OptionValidatorException;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.HeadersArgumentValueConverter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.wildfly.core.cli.command.aesh.CLIConverterInvocation;

/**
 * Convert string onto headers prior to value injection.
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
