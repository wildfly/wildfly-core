/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli;


import org.jboss.as.cli.parsing.DefaultParsingState;
import org.jboss.as.cli.parsing.EnterStateCharacterHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.jboss.as.cli.parsing.arguments.ArgumentValueCallbackHandler;
import org.jboss.as.cli.parsing.arguments.ArgumentValueInitialState;
import org.jboss.as.cli.parsing.arguments.ArgumentValueState;
import org.jboss.as.cli.parsing.arguments.CompositeState;
import org.jboss.as.cli.parsing.arguments.NonObjectArgumentValueState;
import org.jboss.as.cli.util.CLIExpressionResolver;
import org.jboss.dmr.ModelNode;


/**
 *
 * @author Alexey Loubyansky
 */
public interface ArgumentValueConverter {

    abstract class DMRWithFallbackConverter implements ArgumentValueConverter {
        @Override
        public ModelNode fromString(CommandContext ctx, String value) throws CommandFormatException {
            if(value == null) {
                return new ModelNode();
            }
            if(ctx.isResolveParameterValues()) {
                value = CLIExpressionResolver.resolveLax(value);
            }
            try {
                return ModelNode.fromString(value);
            } catch(Exception e) {
                return fromNonDMRString(ctx, value);
            }
        }

        protected abstract ModelNode fromNonDMRString(CommandContext ctx, String value) throws CommandFormatException;
    }

    ArgumentValueConverter DEFAULT = new ArgumentValueConverter() {
        @Override
        public ModelNode fromString(CommandContext ctx, String value) throws CommandFormatException {
            if (value == null) {
                return new ModelNode();
            }
            if(ctx.isResolveParameterValues()) {
                value = CLIExpressionResolver.resolveLax(value);
            }
            ModelNode toSet = null;
            try {
                toSet = ModelNode.fromString(value);
            } catch (Exception e) {
                final ArgumentValueCallbackHandler handler = new ArgumentValueCallbackHandler();
                StateParser.parse(value, handler, ArgumentValueInitialState.INSTANCE);
                toSet = handler.getResult();
            }
            return toSet;
        }
    };

    /**
     * Basically, for STRING with support for expressions.
     */
    ArgumentValueConverter NON_OBJECT = new DMRWithFallbackConverter() {
        final DefaultParsingState initialState = new DefaultParsingState("IE"){
            {
                setDefaultHandler(new EnterStateCharacterHandler(NonObjectArgumentValueState.INSTANCE));
            }
        };
        @Override
        protected ModelNode fromNonDMRString(CommandContext ctx, String value) throws CommandFormatException {
            final ArgumentValueCallbackHandler handler = new ArgumentValueCallbackHandler();
            StateParser.parse(value, handler, initialState);
            return handler.getResult();
        }
    };

    ArgumentValueConverter LIST = new DMRWithFallbackConverter() {
        final DefaultParsingState initialState = new DefaultParsingState("IL"){
            {
                setDefaultHandler(new EnterStateCharacterHandler(new CompositeState(true, ArgumentValueState.INSTANCE)));
            }
        };
        @Override
        protected ModelNode fromNonDMRString(CommandContext ctx, String value) throws CommandFormatException {
            final ArgumentValueCallbackHandler handler = new ArgumentValueCallbackHandler();
            StateParser.parse(value, handler, initialState);
            return handler.getResult();
        }
    };

    ArgumentValueConverter PROPERTIES = new DMRWithFallbackConverter() {
        final DefaultParsingState initialState = new DefaultParsingState("IPL"){
            {
                setDefaultHandler(new EnterStateCharacterHandler(new CompositeState(true, ArgumentValueState.INSTANCE)));
            }
        };
        @Override
        protected ModelNode fromNonDMRString(CommandContext ctx, String value) throws CommandFormatException {
            final ArgumentValueCallbackHandler handler = new ArgumentValueCallbackHandler();
            StateParser.parse(value, handler, initialState);
            return handler.getResult();
        }
    };

    ModelNode fromString(CommandContext ctx, String value) throws CommandFormatException;
}
