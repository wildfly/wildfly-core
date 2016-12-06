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
package org.jboss.as.cli;

import org.jboss.dmr.ModelNode;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author jdenise@redhat.com
 */
public class AttachmentTestCase {

    private static final String PATH = Util.isWindows()
            ? "c:\\\\a\\\\nice\\\\attachment\\\\path" : "/a/nice/attachment/path";

    private static final String EXPECTED_PATH = Util.isWindows()
            ? "c:\\a\\nice\\attachment\\path" : PATH;

    private static final String VAULT_ADD_DESCRIPTION = "{\n"
            + "          \"request-properties\" : {\n"
            + "            \"code\" : {\n"
            + "                \"type\" : \"STRING\",\n"
            + "                \"description\" : \"Fully Qualified Name of the Security Vault Implementation.\",\n"
            + "                \"expressions-allowed\" : true,\n"
            + "                \"required\" : false,\n"
            + "                \"nillable\" : true\n"
            + "            },\n"
            + "            \"module\" : {\n"
            + "                \"type\" : \"STRING\",\n"
            + "                \"description\" : \"The name of the module to load up the vault implementation from.\",\n"
            + "                \"expressions-allowed\" : true,\n"
            + "                \"required\" : false,\n"
            + "                \"nillable\" : true,\n"
            + "                \"requires\" : [\"code\"]\n"
            + "            },\n"
            + "            \"vault-options\" : {\n"
            + "                \"type\" : \"OBJECT\",\n"
            + "                \"description\" : \"Security Vault options.\",\n"
            + "                \"expressions-allowed\" : true,\n"
            + "                \"required\" : false,\n"
            + "                \"nillable\" : true,\n"
            + "                \"value-type\" : \"STRING\"\n"
            + "            }\n"
            + "        }\n"
            + "}";

    private static final String VAULT_ADD_VALUE = "{\n"
            + "\"vault-options\" : {\n"
            + "        \"KEYSTORE_PASSWORD\" : \"MASK-20OB41ZkH8YzlPTICpKg5.\",\n"
            + "        \"KEYSTORE_ALIAS\"    : \"jboss\" }\n"
            + "}";

    private static final String DESCRIPTION = "{\n"
            + "    \"request-properties\": {\n"
            + "        \"toto\": {\n"
            + "            \"type\": \"LIST\",\n"
            + "            \"description\": \"\",\n"
            + "            \"value-type\": \"STRING\"\n"
            + "        },\n"
            + "        \"titi_a\": {\n"
            + "            \"type\": \"INT\",\n"
            + "            \"description\": \"\",\n"
            + "            \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "            \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "        },\n"
            + "        \"tata_a\": {\n"
            + "            \"type\": \"LIST\",\n"
            + "            \"description\": \"\",\n"
            + "            \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "            \"" + Util.FILESYSTEM_PATH + "\": true,\n"
            + "            \"value-type\": \"INT\"\n"
            + "        },\n"
            + "        \"tutu\": {\n"
            + "            \"type\": \"OBJECT\",\n"
            + "            \"description\": \"\",\n"
            + "            \"value-type\": {\n"
            + "                \"p1_a\": {\n"
            + "                    \"type\": \"INT\",\n"
            + "                    \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "                    \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                },\n"
            + "                \"p2_a\": {\n"
            + "                    \"type\": \"INT\",\n"
            + "                    \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "                    \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                },\n"
            + "                \"p3\": {\n"
            + "                    \"type\": \"LIST\",\n"
            + "                    \"description\": \"\",\n"
            + "                    \"value-type\": {\n"
            + "                        \"oo_file_a\": {\n"
            + "                            \"type\": \"INT\",\n"
            + "                            \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "                            \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                        },\n"
            + "                        \"ii_file\": {\n"
            + "                            \"type\": \"STRING\"\n"
            + "                        }\n"
            + "                    }\n"
            + "                },\n"
            + "                \"p4\": {\n"
            + "                    \"type\": \"OBJECT\",\n"
            + "                    \"description\": \"\",\n"
            + "                    \"value-type\": {\n"
            + "                        \"oo_file_a\": {\n"
            + "                            \"type\": \"INT\",\n"
            + "                            \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "                            \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                        },\n"
            + "                        \"ii_file\": {\n"
            + "                            \"type\": \"STRING\"\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "        ,\n"
            + "        \"content\": {\n"
            + "            \"type\": \"LIST\",\n"
            + "            \"description\": \"\",\n"
            + "            \"value-type\": {\n"
            + "                \"source_file_a\": {\n"
            + "                    \"type\": \"INT\",\n"
            + "                    \"" + Util.ATTACHED_STREAMS + "\": true,\n"
            + "                    \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                },\n"
            + "                \"target_file\": {\n"
            + "                    \"type\": \"STRING\"\n"
            + "                }\n"
            + "            }\n"
            + "        }\n"
            + "    }\n"
            + "}\n";
    private static final String VALUE = "{\n"
            + "    \"toto\": [\"a\", \"b\", \"c\"],\n"
            + "    \"titi_a\": \"" + PATH + "0\",\n"
            + "    \"tata_a\": [\"" + PATH + "1\", \"" + PATH + "2\", \"" + PATH + "3\"],\n"
            + "    \"tutu\": {\n"
            + "        \"p1_a\": \"" + PATH + "4\",\n"
            + "        \"p2_a\": \"" + PATH + "5\",\n"
            + "        \"p3\": [\n"
            + "            {\n"
            + "                \"oo_file_a\": \"" + PATH + "6\",\n"
            + "                \"ii_file\": \"spmeyhing\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"oo_file_a\": \"" + PATH + "7\",\n"
            + "                \"ii_file\": \"spmeyhing2\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"p4\": {\n"
            + "            \"oo_file_a\": \"" + PATH + "8\",\n"
            + "            \"ii_file\": \"spmeyhing3\"\n"
            + "        }\n"
            + "    },\n"
            + "    \"content\": [{\n"
            + "            \"source_file_a\": \"" + PATH + "9\",\n"
            + "            \"target_file\": \"atarget1\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"source_file_a\": \"" + PATH + "10\",\n"
            + "            \"target_file\": \"atarget2\"\n"
            + "        }]\n"
            + "}\n"
            + "\n";

    private static final String RESULT = "{\n"
            + "    \"toto\" : [\n"
            + "        \"a\",\n"
            + "        \"b\",\n"
            + "        \"c\"\n"
            + "    ],\n"
            + "    \"titi_a\" : 0,\n"
            + "    \"tata_a\" : [\n"
            + "        1,\n"
            + "        2,\n"
            + "        3\n"
            + "    ],\n"
            + "    \"tutu\" : {\n"
            + "        \"p1_a\" : 4,\n"
            + "        \"p2_a\" : 5,\n"
            + "        \"p3\" : [\n"
            + "            {\n"
            + "                \"oo_file_a\" : 6,\n"
            + "                \"ii_file\" : \"spmeyhing\"\n"
            + "            },\n"
            + "            {\n"
            + "                \"oo_file_a\" : 7,\n"
            + "                \"ii_file\" : \"spmeyhing2\"\n"
            + "            }\n"
            + "        ],\n"
            + "        \"p4\" : {\n"
            + "            \"oo_file_a\" : 8,\n"
            + "            \"ii_file\" : \"spmeyhing3\"\n"
            + "        }\n"
            + "    },\n"
            + "    \"content\" : [\n"
            + "        {\n"
            + "            \"source_file_a\" : 9,\n"
            + "            \"target_file\" : \"atarget1\"\n"
            + "        },\n"
            + "        {\n"
            + "            \"source_file_a\" : 10,\n"
            + "            \"target_file\" : \"atarget2\"\n"
            + "        }\n"
            + "    ]\n"
            + "}";

    @Test
    public void test1() throws CliInitializationException {
        // 10 files should be replaced by indexes.
        ModelNode description = ModelNode.fromJSONString(DESCRIPTION);
        ModelNode value = ModelNode.fromJSONString(VALUE);
        ModelNode expected = ModelNode.fromJSONString(RESULT);
        ModelNode req = description.get(Util.REQUEST_PROPERTIES).asObject();
        Attachments attachments = new Attachments();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        for (String k : value.keys()) {
            Util.applyReplacements(ctx, k, value.get(k), req.get(k), req.get(k).get(Util.TYPE).asType(), attachments);
        }
        Assert.assertEquals("Should be equal", expected, value);
        Assert.assertEquals(11, attachments.getAttachedFiles().size());
        for (int i = 0; i < attachments.getAttachedFiles().size(); i++) {
            String p = attachments.getAttachedFiles().get(i);
            Assert.assertEquals(p, EXPECTED_PATH + i);
        }
    }

    @Test
    public void testInvalidValueType() throws CliInitializationException {
        // 10 files should be replaced by indexes.
        ModelNode description = ModelNode.fromJSONString(VAULT_ADD_DESCRIPTION);
        ModelNode value = ModelNode.fromJSONString(VAULT_ADD_VALUE);
        ModelNode req = description.get(Util.REQUEST_PROPERTIES).asObject();
        Attachments attachments = new Attachments();
        CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
        for (String k : value.keys()) {
            Util.applyReplacements(ctx, k, value.get(k), req.get(k), req.get(k).get(Util.TYPE).asType(), attachments);
        }
    }
}
