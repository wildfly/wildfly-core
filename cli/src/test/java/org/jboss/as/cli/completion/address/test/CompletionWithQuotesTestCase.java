/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.completion.address.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.cli.CliInitializationException;
import org.jboss.as.cli.CommandFormatException;
import org.jboss.as.cli.CommandLineException;
import org.jboss.as.cli.completion.mock.MockNode;
import org.junit.Assert;
import org.junit.Test;


/**
 * https://issues.jboss.org/browse/WFCORE-1971
 *
 * This test case is checking an offset and candidates returned by the OperationRequestCompleter when parsing
 * an operation address.
 *
 * The offset determines where the candidate completions would be inserted in the buffer.
 *
 * @author Tomas Hofman (thofman@redhat.com)
 */
@SuppressWarnings("ArraysAsListWithZeroOrOneArgument")
public class CompletionWithQuotesTestCase extends AbstractAddressCompleterTest {

    public CompletionWithQuotesTestCase() throws IOException, CliInitializationException {
        MockNode root1 = addRoot("type");
        MockNode root2 = addRoot("test");

        root2.addChild("esca\'ped");
        root2.addChild("esca\\ped");
        root2.addChild("esca\"ped");
        root2.addChild("esca/ped");
        root2.addChild("esca:ped");
        root2.addChild("esca=ped");
        root2.addChild("multiple/escaped1");
        root2.addChild("multiple/escaped2");
        root2.addChild("multiple\"escaped1");
        root2.addChild("multiple\"escaped2");
        root2.addChild("test::test");

        MockNode typeOne = root1.addChild("typeOne");
        root1.addChild("typeTwo");
        root1.addChild("type\"Three");

        MockNode subtype = typeOne.addChild("subtype");
        subtype.addChild("subtypeOne");
    }

    @Test
    public void testNodeNameMultipleCandidates() throws CommandLineException {
        String cmd = "/type=";
        checkCompletion(cmd, cmd.length(), Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        cmd = "/type=type";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        cmd = "/type=\"";
        checkCompletion(cmd, cmd.length(), Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        cmd = "/type=\"type";
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        cmd = "/type=\"typeTwo\"";
        checkCompletion(cmd, cmd.length(), Arrays.asList(":", "/"));
    }

    @Test
    public void testNodeNameSingleCandidate() throws CommandFormatException {
        String cmd = "/type=typeOne/subtype=";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 1, Arrays.asList("subtypeOne"));

        cmd = "/type=typeOne/subtype=subtype";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 1, Arrays.asList("subtypeOne"));

        cmd = "/type=typeOne/subtype=\"";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtypeOne\""));

        cmd = "/type=typeOne/subtype=\"sub";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtypeOne\""));

        cmd = "/type=typeOne/subtype=\"subtypeOne";
        checkCompletion(cmd, cmd.length(), Arrays.asList("\""));
    }

    @Test
    public void testNodeTypeMultipleCandidates() throws CommandFormatException {
        String cmd = "/";
        checkCompletion(cmd, 1, Arrays.asList("test", "type"));

        cmd = "/t";
        checkCompletion(cmd, 1, Arrays.asList("test", "type"));
    }

    @Test
    public void testNodeTypeSingleCandidate() throws CommandFormatException {
        String cmd = "/type=typeOne/";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 1, Arrays.asList("subtype="));

        cmd = "/type=typeOne/sub";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 1, Arrays.asList("subtype="));
    }

    @Test
    public void testOpenQuotesMultipleCandidates() throws CommandFormatException {
        String cmd = "/\"";
        checkCompletion(cmd, 2, Arrays.asList("test", "type"));

        cmd = "/\"t";
        checkCompletion(cmd, 2, Arrays.asList("test", "type"));

        // escaped char

        cmd = "/test=multiple\\/\"";
        checkCompletion(cmd, cmd.indexOf('=') + 3, Arrays.asList("multiple/escaped1", "multiple/escaped2"));

        cmd = "/test=multiple\"/";
        checkCompletion(cmd, cmd.indexOf('=') + 2, Arrays.asList("multiple/escaped1", "multiple/escaped2"));

        cmd = "/test=multiple\"\\/";
        checkCompletion(cmd, cmd.indexOf('=') + 3, Arrays.asList("multiple/escaped1", "multiple/escaped2"));


        cmd = "/test=multiple\\\"\"";
        checkCompletion(cmd, cmd.indexOf('=') + 2, Arrays.asList("multiple\\\"escaped1", "multiple\\\"escaped2"));

        cmd = "/test=multiple\"\\\"";
        checkCompletion(cmd, cmd.indexOf('=') + 2, Arrays.asList("multiple\\\"escaped1", "multiple\\\"escaped2"));
    }

    @Test
    public void testOpenQuotesSingleCandidate() throws CommandFormatException {
        String cmd = "/type=typeOne/\"";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtype\"="));

        cmd = "/type=typeOne/\"sub";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtype\"="));

        cmd = "/type=typeOne/\"subtype";
        checkCompletion(cmd, cmd.length(), Arrays.asList("\""));

        cmd = "/type=typeOne/\"subtype\"";
        checkCompletion(cmd, cmd.length(), Arrays.asList("="));

        // escaped char

        cmd = "/test=esca\\:\"";
        checkCompletion(cmd, cmd.indexOf('=') + 2, Arrays.asList("\"esca:ped\""));

        cmd = "/test=esca\":";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("\"esca:ped\""));

        cmd = "/test=esca\"\\:";
        checkCompletion(cmd, cmd.indexOf('=') + 2, Arrays.asList("\"esca:ped\""));
    }

    @Test
    public void testClosedQuotesMultipleCandidates() throws CommandFormatException {
        String cmd = "/\"t\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("test", "type"));

        cmd = "/type=\"type\""; // single closed quotes
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        cmd = "/type=\"ty\"\"pe\""; // multiple closed quotes
        checkCompletion(cmd, cmd.indexOf('"') + 4, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        // escaped char in quotes

        cmd = "/test=\"multiple/\"";
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("multiple\\/escaped1", "multiple\\/escaped2"));

        cmd = "/test=\"multiple\\/\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("multiple\\/escaped1", "multiple\\/escaped2"));

        cmd = "/test=\"multiple\"\\/";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("multiple\\/escaped1", "multiple\\/escaped2"));
    }

    @Test
    public void testClosedQuotesSingleCandidate() throws CommandFormatException {
        String cmd = "/\"te\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("test="));

        cmd = "/type=\"typeO\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("typeOne"));

        cmd = "/type=\"type\\\"\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("type\\\"Three"));

        // escaped char in quotes

        cmd = "/test=\"esca:\"";
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("esca\\:ped"));

        cmd = "/test=\"esca\\:\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("esca\\:ped"));

        cmd = "/test=\"esca\"\\:";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("esca\\:ped"));
    }

    @Test
    public void testClosedAndOpenQuotesSingleCandidate() throws CommandFormatException {
        String cmd = "/\"te\"\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("\"test\"="));

        cmd = "/\"ty\"\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("\"type\"="));

        cmd = "/\"ty\"\"p\"\"";
        checkCompletion(cmd, cmd.indexOf('"') + 4, Arrays.asList("\"type\"="));

        cmd = "/type=\"typeO\"\"";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("\"typeOne\""));

        cmd = "/type=\"type\"\"O\"\""; // two closed, last open
        checkCompletion(cmd, cmd.indexOf('"') + 4, Arrays.asList("\"typeOne\""));
    }

    @Test
    public void testLeadingSpacesSingleCandidate() throws CommandFormatException {
        // leading space

        String cmd = "/type=typeOne/ ";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 2, Arrays.asList("subtype="));

        cmd = "/type=typeOne/subtype= ";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 2, Arrays.asList("subtypeOne"));

        // combination of leading space and opening quote

        cmd = "/type=typeOne/ \"";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtype\"="));

        cmd = "/type=typeOne/subtype= \"";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtypeOne\""));

        // combination of leading space and starting characters

        cmd = "/type=typeOne/ sub";
        checkCompletion(cmd, cmd.lastIndexOf("sub"), Arrays.asList("subtype="));

        cmd = "/type=typeOne/subtype= sub";
        checkCompletion(cmd, cmd.lastIndexOf("sub"), Arrays.asList("subtypeOne"));

        // combination of leading space, opening quote and starting characters

        cmd = "/type=typeOne/ \"sub";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtype\"="));

        cmd = "/type=typeOne/subtype= \"sub";
        checkCompletion(cmd, cmd.lastIndexOf('"'), Arrays.asList("\"subtypeOne\""));
    }

    @Test
    public void testLeadingSpacesMultipleCandidates() throws CommandFormatException {
        // leading space

        String cmd = "/ ";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 2, Arrays.asList("test", "type"));

        cmd = "/type= ";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 2, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        // combination of leading space and opening quote

        cmd = "/ \"";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 3, Arrays.asList("test", "type"));

        cmd = "/type= \"";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 3, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        // combination of leading space and starting characters

        cmd = "/ t";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 2, Arrays.asList("test", "type"));

        cmd = "/type= type";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 2, Arrays.asList("type\"Three", "typeOne", "typeTwo"));

        // combination of leading space, opening quote and starting characters

        cmd = "/ \"t";
        checkCompletion(cmd, cmd.lastIndexOf('/') + 3, Arrays.asList("test", "type"));

        cmd = "/type= \"type";
        checkCompletion(cmd, cmd.lastIndexOf('=') + 3, Arrays.asList("type\"Three", "typeOne", "typeTwo"));
    }

    @Test
    public void testEscapedCharactersWithoutQuotesSingleCandidate() throws CommandFormatException {
        String cmd = "/test=esca\\\"";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\\"ped"));

        cmd = "/test=esca\\'";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\'ped"));

        cmd = "/test=esca\\\\";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\\\ped"));

        cmd = "/test=esca\\/";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\/ped"));

        cmd = "/test=esca\\:";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\:ped"));

        cmd = "/test=esca\\=";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("esca\\=ped"));
    }

    @Test
    public void testEscapedCharactersWithoutQuotesMultipleCandidates() throws CommandFormatException {
        String cmd = "/test=multiple\\/";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("multiple\\/escaped1", "multiple\\/escaped2"));

        cmd = "/test=multiple\\\"";
        checkCompletion(cmd, cmd.indexOf('=') + 1, Arrays.asList("multiple\\\"escaped1", "multiple\\\"escaped2"));
    }

    @Test
    public void testEscapedCharactersWithQuotesSingleCandidate() throws CommandFormatException {
        // only " and \ should be escaped inside quotes

        String cmd = "/test=\"esca\\\"";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca\\\"ped\""));

        cmd = "/test=\"esca'";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca'ped\""));

        cmd = "/test=\"esca\\'"; // unnecessarily escaped
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("\"esca'ped\""));

        cmd = "/test=\"esca\\\\";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca\\\\ped\""));

        cmd = "/test=\"esca/";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca/ped\""));

        cmd = "/test=\"esca\\/"; // unnecessarily escaped
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("\"esca/ped\""));

        cmd = "/test=\"esca:";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca:ped\""));

        cmd = "/test=\"esca\\:"; // unnecessarily escaped
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("\"esca:ped\""));

        cmd = "/test=\"esca=";
        checkCompletion(cmd, cmd.indexOf('"'), Arrays.asList("\"esca=ped\""));

        cmd = "/test=\"esca\\="; // unnecessarily escaped
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("\"esca=ped\""));
    }

    @Test
    public void testEscapedCharactersWithQuotesMultipleCandidates() throws CommandFormatException {
        String cmd = "/test=\"multiple/";
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("multiple/escaped1", "multiple/escaped2"));

        cmd = "/test=\"multiple\\/";
        checkCompletion(cmd, cmd.indexOf('"') + 2, Arrays.asList("multiple/escaped1", "multiple/escaped2"));

        cmd = "/test=\"multiple\\\"";
        checkCompletion(cmd, cmd.indexOf('"') + 1, Arrays.asList("multiple\\\"escaped1", "multiple\\\"escaped2"));
    }


    private void checkCompletion(String cmd, int expectedOffset, List<String> expectedCandidates) throws CommandFormatException {
        ArrayList<String> candidates = new ArrayList<>();
        int offset = complete(cmd, candidates);
        Assert.assertEquals("Wrong offset", expectedOffset, offset);
        Assert.assertEquals("Expected different candidates", expectedCandidates, candidates);
    }

    private int complete(String buffer, List<String> candidates) throws CommandFormatException {
        ctx.parseCommandLine(buffer, false);
        return completer.complete(ctx, buffer, 0, candidates);
    }
}
