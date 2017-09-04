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
package org.jboss.as.cli.impl.aesh;

import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.AeshContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;

/**
 * Intermediate layer that converts expressions prior to inject values into aesh
 * command options.
 *
 * @author jdenise@redhat.com
 */
public class ExpressionValueConverter<T> implements Converter<T, ConverterInvocation> {

    private static class ResolvedConverterInvocation implements ConverterInvocation {

        private final String resolved;
        private final AeshContext ctx;

        private ResolvedConverterInvocation(String resolved, AeshContext ctx) {
            this.resolved = resolved;
            this.ctx = ctx;
        }

        @Override
        public String getInput() {
            return resolved;
        }

        @Override
        public AeshContext getAeshContext() {
            return ctx;
        }

    }
    private final Converter<T, ConverterInvocation> target;

    ExpressionValueConverter(Converter<T, ConverterInvocation> target) {
        this.target = target;
    }

    @Override
    public T convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
        try {
            String resolved = ArgumentWithValue.resolveValue(converterInvocation.getInput());
            return target.convert(new ResolvedConverterInvocation(resolved, converterInvocation.getAeshContext()));
        } catch (CommandFormatException ex) {
            throw new OptionValidatorException(Util.getMessagesFromThrowable(ex));
        }
    }

}
