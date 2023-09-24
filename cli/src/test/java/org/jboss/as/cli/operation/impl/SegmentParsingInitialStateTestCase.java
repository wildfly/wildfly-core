/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.operation.impl;

import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.parsing.StateParser;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Tomas Hofman (thofman@redhat.com)
 */
public class SegmentParsingInitialStateTestCase {

    @Test
    public void testOffset() throws CommandFormatException {
        SegmentParsingInitialState.SegmentParsingCallbackHandler handler = new SegmentParsingInitialState.SegmentParsingCallbackHandler();

        String chunk = " \"test";
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(2, handler.getOffset());

        chunk = " \"test\"";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(3, handler.getOffset());

        chunk = "  test";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(2, handler.getOffset());

        chunk = "test  ";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(0, handler.getOffset());

        chunk = "\"te\\\"st\"";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(2, handler.getOffset());

        chunk = "te\\\"st";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(0, handler.getOffset());

        chunk = "\"test\"\"test\"";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(4, handler.getOffset());

        chunk = "\"test\"test\"test\"";
        handler.reset();
        StateParser.parse(chunk, handler, SegmentParsingInitialState.INSTANCE, false);
        Assert.assertEquals(4, handler.getOffset());
    }
}
