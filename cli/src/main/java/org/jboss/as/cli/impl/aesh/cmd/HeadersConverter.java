/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
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
