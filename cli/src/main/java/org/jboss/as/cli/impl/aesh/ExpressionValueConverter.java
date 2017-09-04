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
package org.jboss.as.cli.impl.aesh;

import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.readline.AeshContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;

/**
 * Intermediate layer that converts expressions prior to inject values in aesh
 * commands.
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
