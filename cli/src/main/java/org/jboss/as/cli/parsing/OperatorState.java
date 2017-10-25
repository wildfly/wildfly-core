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
package org.jboss.as.cli.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.aesh.command.operator.OperatorType;
import org.jboss.as.cli.CommandFormatException;

/**
 * Operator parsing state. The operators are parsed although they are not used
 * Aesh runtime parses operators to initialize execution context. Everything
 * that follow the operator is parsed to resolve variables, eg: ls -l >
 * $myfileVar or version | grep $var
 *
 * @author jfdenise
 */
public class OperatorState {

    private static final List<OpState> OPERATORS = new ArrayList<>();
    public static final String ID = "OPERATOR_OP";
    static {
        Map<Character, List<String>> map = new HashMap<>();
        for (OperatorType ot : OperatorType.values()) {
            // We don't want other operators.
            if (ot.value().startsWith(">") || ot.value().startsWith("|")) {
                if (!ot.value().isEmpty()) {
                    char c = ot.value().charAt(0);
                    List<String> operators = map.get(c);
                    if (operators == null) {
                        operators = new ArrayList<>();
                        map.put(c, operators);
                    }
                    operators.add(ot.value());
                }
            }
        }
        for (Entry<Character, List<String>> entry : map.entrySet()) {
            OPERATORS.add(new OpState(entry.getKey() == '>' ? OutputTargetState.ID : ID,
                    entry.getKey()));
        }
    }

    public static class OpState extends DefaultParsingState {

        private final char firstChar;

        public OpState(String ID, char c) {
            super(ID);
            firstChar = c;
            setDefaultHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx) throws CommandFormatException {
                    // resolve expression in case right part of the operator contains variables.
                    ctx.resolveExpression(true, true);
                    GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER.handle(ctx);
                }
            });
            setEnterHandler(new CharacterHandler() {
                @Override
                public void handle(ParsingContext ctx)
                        throws CommandFormatException {
                    // NO-OP.
                }
            });
        }

        public char getFirstChar() {
            return firstChar;
        }
    }

    private OperatorState() {

    }

    public static void registerEnterStates(DefaultParsingState state) {
        for (OpState op : OPERATORS) {
            // Enter an opState when the first character of the operator is encountered
            state.enterState(op.getFirstChar(), op);
        }
    }

    public static void registerLeaveHandlers(DefaultParsingState state) {
        for (OpState op : OPERATORS) {
            // Leave the state when the first character of the operator is encountered
            state.putHandler(op.getFirstChar(), GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }
}
