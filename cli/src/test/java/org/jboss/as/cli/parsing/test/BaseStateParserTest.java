/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.parsing.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.operation.OperationFormatException;
import org.jboss.as.cli.parsing.BasicInitialParsingState;
import org.jboss.as.cli.parsing.ParsingContext;
import org.jboss.as.cli.parsing.ParsingStateCallbackHandler;
import org.jboss.as.cli.parsing.StateParser;
import org.junit.Before;

/**
*
* @author Alexey Loubyansky
*/
public class BaseStateParserTest {

    protected ParsingStateCallbackHandler callbackHandler = getCallbackHandler();
    protected ParsedTerm result;
    private ParsedTerm temp;

    public BaseStateParserTest() {
        super();
    }

    @Before
    public void setup() {
        result = new ParsedTerm(null);
    }

    protected void parse(String str) throws CommandFormatException {
        StateParser.parse(str, callbackHandler, BasicInitialParsingState.INSTANCE);
    }

    private ParsingStateCallbackHandler getCallbackHandler() {
        return new ParsingStateCallbackHandler() {
            @Override
            public void enteredState(ParsingContext ctx) {
                //System.out.println("[" + ctx.getLocation() + "] '" + ctx.getCharacter() + "' entered " + ctx.getState().getId());

                if (temp == null) {
                    temp = result;
                }

                ParsedTerm newTerm = new ParsedTerm(temp);
                temp = newTerm;
                //temp.append(ctx.getCharacter());
            }

            @Override
            public void leavingState(ParsingContext ctx) {
                //System.out.println("[" + ctx.getLocation() + "] '" + ctx.getCharacter() + "' left " + ctx.getState().getId());

                //temp.append(ctx.getCharacter());
                temp.parent.addChild(temp);
                temp = temp.parent;
            }

            @Override
            public void character(ParsingContext ctx)
                    throws OperationFormatException {
                //System.out.println("[" + ctx.getLocation() + "] '" + ctx.getCharacter() + "' content of " + ctx.getState().getId());

                temp.append(ctx.getCharacter());
            }};
    }

    static class ParsedTerm {
        final ParsedTerm parent;
        StringBuilder buffer;
        List<ParsedTerm> children = Collections.emptyList();
        StringBuilder valueAsString = new StringBuilder();

        ParsedTerm(ParsedTerm parent) {
            this.parent = parent;
        }

        void append(char ch) {
            if(buffer == null) {
                buffer = new StringBuilder();
            }
            buffer.append(ch);
            valueAsString.append(ch);
        }

        void addChild(ParsedTerm child) {
            if (children.isEmpty()) {
                children = new ArrayList<ParsedTerm>();
            }
            children.add(child);
            valueAsString.append(child.valueAsString());
        }

        String valueAsString() {
            return valueAsString.toString();
        }
    }
}