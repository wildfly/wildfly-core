/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat Middleware LLC, and individual contributors
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
 *
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
