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
package org.jboss.as.cli.parsing;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.aesh.command.operator.OperatorType;
import org.jboss.as.cli.CommandFormatException;

/**
 * Should not be used, outside of tests... in case the legacy handler parse
 * operators.
 *
 * @author jfdenise
 */
public class OperatorState {

    private static final List<OpState> OPERATORS = new ArrayList<>();

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
            String id = entry.getKey() == '>' ? OutputTargetState.ID : "IGNORE_OP";
            OPERATORS.add(new OpState(id, entry.getKey(), entry.getValue()));
        }
    }

    // Noop Parser, just to make the legacy parsing not to fail when
    // parsing operators.
    public static class OpState extends DefaultParsingState {

        private final char firstChar;

        public OpState(String ID, char c, List<String> operators) {
            super(ID);
            firstChar = c;
            setDefaultHandler(GlobalCharacterHandlers.CONTENT_CHARACTER_HANDLER);
            setEnterHandler(new CharacterHandler() {

                @Override
                public void handle(ParsingContext ctx)
                        throws CommandFormatException {
                    String operator = null;
                    for (String op : operators) {
                        char[] chars = op.toCharArray();
                        boolean found = true;
                        for (int i = 1; i < chars.length; i++) {
                            if (ctx.isEndOfContent() || ctx.getInput().charAt(ctx.getLocation() + i) != c) {
                                found = false;
                                break;
                            }
                        }
                        if (found) {
                            operator = op;
                            break;
                        }
                    }
                    // Skip the operator.
                    if (operator != null) {
                        ctx.advanceLocation(operator.length() - 1);
                    }
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
            state.enterState(op.getFirstChar(), op);
        }
    }

    public static void registerLeaveHandlers(DefaultParsingState state) {
        for (OpState op : OPERATORS) {

            state.putHandler(op.getFirstChar(), GlobalCharacterHandlers.LEAVE_STATE_HANDLER);
        }
    }
}
