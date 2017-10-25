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

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.aesh.command.converter.Converter;
import org.aesh.command.converter.ConverterInvocation;
import org.aesh.command.impl.internal.ProcessedOption;
import org.aesh.command.impl.internal.ProcessedOptionBuilder;
import org.aesh.command.parser.OptionParserException;
import org.aesh.command.validator.OptionValidatorException;
import org.aesh.converter.CLConverterManager;
import org.aesh.readline.AeshContext;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.impl.ArgumentWithValue;
import org.jboss.as.cli.parsing.ExpressionBaseState;
import org.jboss.as.cli.parsing.ParsingState;
import org.jboss.as.cli.parsing.WordCharacterHandler;

/**
 * Intermediate layer that converts expressions prior to inject values into aesh
 * command options.
 *
 * @author jdenise@redhat.com
 */
public class ExpressionValueConverter<T> implements Converter<T, ConverterInvocation> {

    private static final ParsingState DEFAULT_EXPRESSION_STATE;

    static {
        final ExpressionBaseState state = new ExpressionBaseState("EXPR", true, false);
        state.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
        DEFAULT_EXPRESSION_STATE = state;
    }

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
    private final ParsingState state;
    private static boolean convertersRegistered;

    private ExpressionValueConverter(Converter<T, ConverterInvocation> target, ParsingState state) {
        this.target = target;
        this.state = state;
    }

    @Override
    public T convert(ConverterInvocation converterInvocation) throws OptionValidatorException {
        try {
            String resolved = ArgumentWithValue.resolveValue(converterInvocation.getInput(), state);
            return target.convert(new ResolvedConverterInvocation(resolved, converterInvocation.getAeshContext()));
        } catch (CommandFormatException ex) {
            throw new OptionValidatorException(Util.getMessagesFromThrowable(ex));
        }
    }

    static ProcessedOption disableResolution(ProcessedOption opt) throws OptionParserException {
        if (opt == null) {
            return opt;
        }
        ProcessedOptionBuilder c = ProcessedOptionBuilder.builder();
        if (opt.activator() != null) {
            c.activator(opt.activator());
        }
        if (opt.getDefaultValues() != null) {
            c.addAllDefaultValues(opt.getDefaultValues());
        }
        if (opt.getArgument() != null) {
            c.argument(opt.getArgument());
        }
        if (opt.completer() != null) {
            c.completer(opt.completer());
        }
        if (opt.converter() instanceof ExpressionValueConverter) {
            ExpressionValueConverter converter = (ExpressionValueConverter) opt.converter();
            c.converter(converter.target);
        }
        if (opt.description() != null) {
            c.description(opt.description());
        }
        c.fieldName(opt.getFieldName());
        c.hasMultipleValues(opt.hasMultipleValues());
        c.hasValue(opt.hasValue());
        c.isProperty(opt.isProperty());
        c.name(opt.name());
        c.optionType(opt.getOptionType());
        c.overrideRequired(opt.doOverrideRequired());
        if (opt.getRenderer() != null) {
            c.renderer(opt.getRenderer());
        }
        c.required(opt.isRequired());
        if (opt.shortName() != null && !opt.shortName().isEmpty()) {
            c.shortName(opt.shortName().charAt(0));
        }
        c.type(opt.type());
        if (opt.validator() != null) {
            c.validator(opt.validator());
        }
        c.valueSeparator(opt.getValueSeparator());
        return c.build();
    }

    static List<ProcessedOption> disableResolution(List<ProcessedOption> options) throws OptionParserException {
        List<ProcessedOption> converted = new ArrayList<>();
        for (ProcessedOption opt : options) {
            converted.add(disableResolution(opt));
        }
        return converted;
    }

    public static void registerConverters() {
        if (!convertersRegistered) {
            for (Class<?> converted : CLConverterManager.getInstance().getConvertedTypes()) {
                ParsingState state = DEFAULT_EXPRESSION_STATE;
                if (converted.isAssignableFrom(File.class)) {
                    ExpressionBaseState s = new ExpressionBaseState("EXPR", true, false);
                    if (Util.isWindows()) {
                        // to not require escaping FS name separator
                        s.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_OFF);
                    } else {
                        s.setDefaultHandler(WordCharacterHandler.IGNORE_LB_ESCAPE_ON);
                    }
                    state = s;
                }
                CLConverterManager.getInstance().setConverter(converted,
                        new ExpressionValueConverter(CLConverterManager.getInstance().getConverter(converted), state));
            }
            convertersRegistered = true;
        }
    }
}
