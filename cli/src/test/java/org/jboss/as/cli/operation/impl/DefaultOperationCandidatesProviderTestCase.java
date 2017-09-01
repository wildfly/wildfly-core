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
package org.jboss.as.cli.operation.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.CommandLineCompleter;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.Property;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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
            assertFalse("Invalid property " + p, p == null);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade.sasl-authentication-factory",
                            ModelNode.fromString(nested_content));
            assertFalse("Invalid property " + p, p == null);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade[190].sasl-authentication-factory",
                            ModelNode.fromString(nested_content));
            assertFalse("Invalid property " + p, p == null);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-downgrade",
                            ModelNode.fromString(nested_content));
            assertEquals("Invalid property " + p, p, null);
        }

        {
            Property p
                    = DefaultOperationCandidatesProvider.
                    getProperty("http-upgrade.sasl-authentication-f",
                            ModelNode.fromString(nested_content));
            assertEquals("Invalid property " + p, p, null);
        }
    }
}
