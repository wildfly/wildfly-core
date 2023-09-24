/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.jboss.as.cli.CommandArgument;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.as.cli.completion.mock.MockCommandContext;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class DefaultOperationCandidatesProviderTestCase {

    private static final String nested_content = "{\n" +
"            \"http-upgrade\" => {\n" +
"                \"type\" => OBJECT,\n" +
"                \"description\" => \"HTTP Upgrade specific configuration\",\n" +
"                \"expressions-allowed\" => false,\n" +
"                \"required\" => false,\n" +
"                \"nillable\" => true,\n" +
"                \"value-type\" => {\n" +
"                    \"enabled\" => {\n" +
"                        \"type\" => BOOLEAN,\n" +
"                        \"expressions-allowed\" => false,\n" +
"                        \"required\" => false,\n" +
"                        \"nillable\" => true,\n" +
"                        \"default\" => false\n" +
"                    },\n" +
"                    \"sasl-authentication-factory\" => {\n" +
"                        \"type\" => STRING,\n" +
"                        \"expressions-allowed\" => false,\n" +
"                        \"required\" => false,\n" +
"                        \"nillable\" => true,\n" +
"                        \"capability-reference\" => \"org.wildfly.security.sasl-authentication-factory\",\n" +
"                        \"min-length\" => 1L,\n" +
"                        \"max-length\" => 2147483647L\n" +
"                    }\n" +
"                },\n" +
"                \"access-type\" => \"read-write\",\n" +
"                \"storage\" => \"configuration\",\n" +
"                \"restart-required\" => \"no-services\"\n" +
"            },\n" +
"        }";

    private static final String list_content = "{\n"
            + "            \"type\" => LIST,\n"
            + "            \"value-type\" => STRING\n"
            + "        }";

    private static final String obj_content = "{\n"
            + "            \"type\" => OBJECT,\n"
            + "            \"value-type\" => { \"prop1\" => { \"type\" => STRING,\n \"required\" => false},\n"
            + "                \"prop2\" => { \"type\" => STRING,\n \"required\" => false}\n"
            + "        } }";

    private static final String map_content = "{\n"
            + "            \"type\" => OBJECT,\n"
            + "            \"value-type\" => STRING }";

    private static final String boolean_content = "{\n"
            + "            \"type\" => BOOLEAN,\n"
            + "        }";

    private static final String string_content = "{\n"
            + "            \"type\" => STRING,\n"
            + "        }";

    private static final String allowed_content = "{\n"
            + "                \"type\" => STRING,\n"
            + "                \"allowed\" => [\n"
            + "                    \"ALL\",\n"
            + "                    \"FINEST\",\n"
            + "                    \"FINER\",\n"
            + "                ]"
            + "            }";

    private static final String bytes_content = "{\n"
            + "            \"type\" => BYTES}";


    @Test
    public void testGetPropertiesFromPropList() throws Exception {
        List<Property> propList = new ArrayList<>();
        propList.add(new Property("blocking", ModelNode.fromString(list_content)));
        propList.add(new Property("blocking-param", ModelNode.fromString(list_content)));
        propList.add(new Property("start-mode", ModelNode.fromString(list_content)));

        MockCommandContext ctx = new MockCommandContext();
        String operationName = "operationName";
        ctx.parseCommandLine(":" + operationName + "(!blocking", false);

        DefaultOperationRequestAddress address = new DefaultOperationRequestAddress();

        DefaultOperationCandidatesProvider candidatesProvider = new DefaultOperationCandidatesProvider();
        List<CommandArgument> candidates = candidatesProvider
                .getPropertiesFromPropList(propList, ctx, operationName, address);

        assertEquals(propList.size(), candidates.size());
        for (CommandArgument candidate : candidates) {
            if(candidate.getFullName().equals("blocking"))
                assertFalse("Property blocking can't appear next, since it's completely specified" +
                        " on the commandline as !blocking", candidate.canAppearNext(ctx));
            else
                assertTrue(candidate.canAppearNext(ctx));
        }
    }

    @Test
    public void testAttributeValueCompleter() throws Exception {
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        DefaultOperationRequestAddress address = new DefaultOperationRequestAddress();
        {
            Property prop = new Property("arg", ModelNode.fromString(list_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "", 0, candidates);
            assertEquals(Arrays.asList("["), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(map_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "", 0, candidates);
            assertEquals(Arrays.asList("{"), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(obj_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "{", 0, candidates);
            assertEquals(Arrays.asList("prop1", "prop2"), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(boolean_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "", 0, candidates);
            assertEquals(Arrays.asList("false", "true"), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(allowed_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "", 0, candidates);
            assertEquals(Arrays.asList("ALL", "FINER", "FINEST"), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(string_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            assertEquals(null, completer);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(bytes_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "", 0, candidates);
            assertEquals(Arrays.asList("bytes{"), candidates);
        }

        {
            Property prop = new Property("arg", ModelNode.fromString(bytes_content));
            CommandLineCompleter completer = DefaultOperationCandidatesProvider.getCompleter(prop, ctx, address);
            List<String> candidates = new ArrayList<>();
            completer.complete(ctx, "bytes", 0, candidates);
            assertEquals(Arrays.asList("bytes{"), candidates);
        }
    }

    @Test
    public void testAttributeNamePath() throws Exception {

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade",
                            ModelNode.fromString(nested_content));
            assertNotNull("Invalid property " + p, p);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade.sasl-authentication-factory",
                            ModelNode.fromString(nested_content));
            assertNotNull("Invalid property " + p, p);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade[190].sasl-authentication-factory",
                            ModelNode.fromString(nested_content));
            assertNotNull("Invalid property " + p, p);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-downgrade",
                            ModelNode.fromString(nested_content));
            assertNull("Invalid property " + p, p);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade.sasl-authentication-f",
                            ModelNode.fromString(nested_content));
            assertNull("Invalid property " + p, p);
        }
    }
}
