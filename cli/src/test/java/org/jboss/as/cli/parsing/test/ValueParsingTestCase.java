/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.cli.parsing.test;

import org.aesh.command.parser.OptionParserException;
import org.aesh.parser.LineParser;
import org.aesh.parser.ParsedLine;
import org.aesh.parser.ParsedLineIterator;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.impl.aesh.parser.CompositeParser;
import org.jboss.as.cli.impl.aesh.parser.HeadersParser;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class ValueParsingTestCase {

    @Test
    public void testHeaders() throws CommandFormatException, OptionParserException {
        {
            String value = "    {ax=true; rollout ( server_group ) toto;}";
            String others = " --doit";
            String cmdLine = value + others;
            int offset = new HeadersParser().parse(cmdLine, null, null);
            String remain = cmdLine.substring(offset + 1);
            assertTrue("Offset is " + offset + ", remain:" + remain, others.equals(remain));
        }

        // Do same with iterator
        {
            String value = "--headers={ax=true; rollout ( server_group ) toto;}";
            String others = " --doit";
            String cmdLine = value + others;
            ParsedLine line = new LineParser().parseLine(cmdLine);
            ParsedLineIterator iterator = line.iterator();
            new HeadersParser().parse(iterator, null);
            char c = iterator.pollChar();
            assertTrue("Char is [" + c + "]", c == ' ');
            String word = iterator.pollWord();
            assertTrue(word.equals(others.substring(1)));
        }

        {
            String value = "--headers={ax=true; rollout ( server_group ) toto;}";
            ParsedLine line = new LineParser().parseLine(value);
            ParsedLineIterator iterator = line.iterator();
            new HeadersParser().parse(iterator, null);
            assertTrue("Iterator is not finished", iterator.finished());
        }
    }

    @Test
    public void testObject1() throws CommandFormatException {
        String value = "    {az=10,ax={}, az=[{},{},{}]}";
        String others = " --doit={}";
        String cmdLine = value + others;
        int offset = new CompositeParser().parse(cmdLine, null, null);
        String remain = cmdLine.substring(offset + 1);
        assertTrue(remain, others.equals(remain));
    }

    @Test
    public void testObject2() throws CommandFormatException {
        String value = "{}";
        String others = " --doit={}";
        String cmdLine = value + others;
        int offset = new CompositeParser().parse(cmdLine, null, null);
        String remain = cmdLine.substring(offset + 1);
        assertTrue(remain, others.equals(remain));
    }

    @Test
    public void testList1() throws CommandFormatException {
        String value = "[{az=10,ax={}, az=[{},{},{}]}, {az=10,ax={}, az=[{},{},{}]}]";
        String others = " --doit={}";
        String cmdLine = value + others;
        int offset = new CompositeParser().parse(cmdLine, null, null);
        String remain = cmdLine.substring(offset + 1);
        assertTrue(remain, others.equals(remain));
    }

    @Test
    public void testList2() throws CommandFormatException, OptionParserException {
        {
            String value = "         [{az=10,ax={}, az=[{},{},{}]}, {az=10,ax={}, az=[{},{},{}]}]";
            String others = "     --doit={}";
            String cmdLine = value + others;
            int offset = new CompositeParser().parse(cmdLine, null, null);
            String remain = cmdLine.substring(offset + 1);
            assertTrue(remain, others.equals(remain));
        }

        // Do same with iterator
        {
            String value = "--list=[{az=10,ax={}, az=[{},{},{}]}, {az=10,ax={}, az=[{},{},{}]}]";
            String others = " --doit";
            String cmdLine = value + others;
            ParsedLine line = new LineParser().parseLine(cmdLine);
            ParsedLineIterator iterator = line.iterator();
            new CompositeParser().parse(iterator, null);
            char c = iterator.pollChar();
            assertTrue("Char is [" + c + "]", c == ' ');
            String word = iterator.pollWord();
            assertTrue(word.equals(others.substring(1)));
        }
    }

}
