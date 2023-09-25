/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.cli.parsing.test;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.CommandContextFactory;
import org.jboss.as.cli.Util;

import org.jboss.as.cli.impl.ValueTypeCompleter;
import org.jboss.as.cli.impl.ValueTypeCompleter.CapabilityCompleterFactory;
import org.jboss.as.cli.operation.OperationRequestAddress;
import org.jboss.as.cli.operation.impl.CapabilityReferenceCompleter;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 * @author Alexey Loubyansky
 */
public class ValueTypeCompletionTestCase {

    private static final String requires_alternatives1 = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"A\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"required\" => true,\n"
            + "                        \"requires\" => [\n"
            + "                            \"B\",\n"
            + "                            \"C\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"B\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => false,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"C\",\n"
            + "                            \"D\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"C\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => false,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"B\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"D\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"B\",\n"
            + "                        ],\n"
            + "                    },\n"
            + "                    \"E\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => false\n"
            + "                    }\n"
            + "                }\n"
            + "            }";

    private static final String requiresInvalidAlternatives = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"A\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"B\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"B\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"A\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"C\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"requires\" => [\n"
            + "                            \"A\"\n"
            + "                        ],\n"
            + "                        \"required\" => false,\n"
            + "                    }\n"
            + "                }\n"
            + "            }";

    private static final String requiresAlternatives = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"A\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"B\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"B\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"A\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"C\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"requires\" => [\n"
            + "                            \"A\",\n"
            + "                            \"B\"\n"
            + "                        ],\n"
            + "                        \"required\" => false,\n"
            + "                    }\n"
            + "                }\n"
            + "            }";


    private static final String requires_alternatives2 = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"A\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => true,\n"
            + "                        \"requires\" => [\n"
            + "                            \"B\",\n"
            + "                            \"C\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"B\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => false,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"D\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"C\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => false,\n"
            + "                    },\n"
            + "                    \"D\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"B\",\n"
            + "                        ],\n"
            + "                    }\n"
            + "                }\n"
            + "            }";

    private static final String required_alternatives = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"module\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"load-services\",\n"
            + "                            \"class-names\",\n"
            + "                            \"path\",\n"
            + "                            \"relative-to\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"load-services\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"module\",\n"
            + "                            \"class-names\",\n"
            + "                            \"path\",\n"
            + "                            \"relative-to\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"class-names\" => {\n"
            + "                        \"type\" => LIST,\n"
            + "                        \"required\" => true,\n"
            + "                        \"value-type\" => STRING,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"module\",\n"
            + "                            \"load-services\",\n"
            + "                            \"path\",\n"
            + "                            \"relative-to\"\n"
            + "                        ]\n"
            + "                    },\n"
            + "                    \"path\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"alternatives\" => [\n"
            + "                            \"module\",\n"
            + "                            \"load-services\",\n"
            + "                            \"property-list\",\n"
            + "                            \"class-names\"\n"
            + "                        ],\n"
            + "                        \"requires\" => [\"relative-to\"]\n"
            + "                    },\n"
            + "                    \"relative-to\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"required\" => false,\n"
            + "                        \"requires\" => [\"path\"],\n"
            + "                    },\n"
            + "                    \"recursive\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"required\" => false,\n"
            + "                    },\n"
            + "                    \"property-list\" => {\n"
            + "                        \"type\" => LIST,\n"
            + "                        \"required\" => true,\n"
            + "                        \"alternatives\" => [\"path\"],\n"
            + "                        \"value-type\" => {\n"
            + "                            \"name\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"description\" => \"The key for the property to be set.\",\n"
            + "                                \"expressions-allowed\" => true,\n"
            + "                                \"required\" => true,\n"
            + "                                \"nillable\" => false,\n"
            + "                                \"min-length\" => 1L,\n"
            + "                                \"max-length\" => 2147483647L\n"
            + "                            },\n"
            + "                            \"value\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"description\" => \"The value of the property to be set.\",\n"
            + "                                \"expressions-allowed\" => true,\n"
            + "                                \"required\" => true,\n"
            + "                                \"nillable\" => false,\n"
            + "                                \"min-length\" => 1L,\n"
            + "                                \"max-length\" => 2147483647L\n"
            + "                            }\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "            }";

    private static final String capabilities_prop = "{\n"
            + "            \"type\" => OBJECT,\n"
            + "            \"value-type\" => {\n"
            + "                \"prop1\" => {\n"
            + "                      \"type\" => STRING,\n"
            + "                      \"capability-reference\" => \"org.wildfly.security.role-mapper\"\n"
            + "                },\n"
            + "                \"prop2\" => {\n"
            + "                      \"type\" => BOOLEAN\n"
            + "                }\n"
            + "            }\n"
            + "        }";

    private static final String bytes_prop = "{\n"
            + "            \"type\" => OBJECT,\n"
            + "            \"value-type\" => {\n"
            + "                \"prop1\" => {\n"
            + "                      \"type\" => BYTES\n"
            + "                },\n"
            + "                \"prop2\" => {\n"
            + "                      \"type\" => BOOLEAN\n"
            + "                }\n"
            + "            }\n"
            + "        }";

    private static final String elytron_provider = "{\n"
            + "                \"type\" => LIST,\n"
            + "                \"description\" => \"The providers to be loaded by this resource.\",\n"
            + "                \"expressions-allowed\" => false,\n"
            + "                \"required\" => false,\n"
            + "                \"nillable\" => true,\n"
            + "                \"value-type\" => {\n"
            + "                    \"module\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"description\" => \"The name of the module to load the provider from.\",\n"
            + "                        \"expressions-allowed\" => true,\n"
            + "                        \"required\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"min-length\" => 1L,\n"
            + "                        \"max-length\" => 2147483647L\n"
            + "                    },\n"
            + "                    \"load-services\" => {\n"
            + "                        \"type\" => BOOLEAN,\n"
            + "                        \"description\" => \"Should service loader discovery be used to load the providers.\",\n"
            + "                        \"expressions-allowed\" => true,\n"
            + "                        \"required\" => true,\n"
            + "                        \"nillable\" => false,\n"
            + "                        \"default\" => false\n"
            + "                    },\n"
            + "                    \"class-names\" => {\n"
            + "                        \"type\" => LIST,\n"
            + "                        \"description\" => \"The fully qualified class names of the providers to load, these are loaded after the service-loader discovered providers and duplicates will be skipped.\",\n"
            + "                        \"expressions-allowed\" => true,\n"
            + "                        \"required\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"value-type\" => STRING\n"
            + "                    },\n"
            + "                    \"path\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"description\" => \"The path of the file to use to initialise the providers.\",\n"
            + "                        \"expressions-allowed\" => true,\n"
            + "                        \"required\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"alternatives\" => [\"property-list\"],\n"
            + "                        \"min-length\" => 1L,\n"
            + "                        \"max-length\" => 2147483647L\n"
            + "                    },\n"
            + "                    \"relative-to\" => {\n"
            + "                        \"type\" => STRING,\n"
            + "                        \"description\" => \"The base path of the configuration file.\",\n"
            + "                        \"expressions-allowed\" => false,\n"
            + "                        \"required\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"requires\" => [\"path\"],\n"
            + "                        \"min-length\" => 1L,\n"
            + "                        \"max-length\" => 2147483647L\n"
            + "                    },\n"
            + "                    \"property-list\" => {\n"
            + "                        \"type\" => LIST,\n"
            + "                        \"description\" => \"Configuration properties to be applied to the loaded provider. (Can not be set at the same time as path)\",\n"
            + "                        \"expressions-allowed\" => false,\n"
            + "                        \"required\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"alternatives\" => [\"path\"],\n"
            + "                        \"value-type\" => {\n"
            + "                            \"name\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"description\" => \"The key for the property to be set.\",\n"
            + "                                \"expressions-allowed\" => true,\n"
            + "                                \"required\" => true,\n"
            + "                                \"nillable\" => false,\n"
            + "                                \"min-length\" => 1L,\n"
            + "                                \"max-length\" => 2147483647L\n"
            + "                            },\n"
            + "                            \"value\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"description\" => \"The value of the property to be set.\",\n"
            + "                                \"expressions-allowed\" => true,\n"
            + "                                \"required\" => true,\n"
            + "                                \"nillable\" => false,\n"
            + "                                \"min-length\" => 1L,\n"
            + "                                \"max-length\" => 2147483647L\n"
            + "                            }\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "            }";

    private static final String role_mapper = "{\n"
            + "            \"type\" => LIST,\n"
            + "            \"description\" => \"The referenced role mappers to aggregate.\",\n"
            + "            \"expressions-allowed\" => false,\n"
            + "            \"required\" => true,\n"
            + "            \"nillable\" => false,\n"
            + "            \"capability-reference\" => \"org.wildfly.security.role-mapper\",\n"
            + "            \"value-type\" => STRING\n"
            + "        }";

    private static final String nested_objects = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => {\n"
            + "                    \"prop1\" => {\n"
            + "                        \"type\" => OBJECT,\n"
            + "                        \"value-type\" => {\n"
            + "                             \"prop1_1\" => {\n"
            + "                                 \"type\" => OBJECT,\n"
            + "                                 \"value-type\" => {\n"
            + "                                      \"prop1_1_1\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop1_1_1_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop1_1_1_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                                 \"required\" => false,\n"
            + "                                             },\n"
            + "                                             \"prop1_1_1_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                                 \"required\" => false,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     },\n"
            + "                                     \"prop1_1_2\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop1_1_2_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                                 \"required\" => false,\n"
            + "                                             },\n"
            + "                                             \"prop1_1_2_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                                 \"required\" => false,\n"
            + "                                             },\n"
            + "                                             \"prop1_1_2_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                                 \"required\" => false,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     }\n"
            + "                                 }\n"
            + "                             },\n"
            + "                              \"prop1_2\" => {\n"
            + "                                 \"type\" => OBJECT,\n"
            + "                                 \"value-type\" => {\n"
            + "                                      \"prop1_2_1\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop1_2_1_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop1_2_1_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop1_2_1_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     }\n"
            + "                                 }\n"
            + "                             }\n"
            + "                        }\n"
            + "                    },\n"
            + "                    \"prop2\" => {\n"
            + "                        \"type\" => OBJECT,\n"
            + "                        \"value-type\" => {\n"
            + "                             \"prop2_1\" => {\n"
            + "                                 \"type\" => OBJECT,\n"
            + "                                 \"value-type\" => {\n"
            + "                                      \"prop2_1_1\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop2_1_1_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_1_1_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_1_1_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     },\n"
            + "                                     \"prop2_1_2\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop2_1_2_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_1_2_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_1_2_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     }\n"
            + "                                 }\n"
            + "                             },\n"
            + "                              \"prop2_2\" => {\n"
            + "                                 \"type\" => OBJECT,\n"
            + "                                 \"value-type\" => {\n"
            + "                                      \"prop2_2_1\" => {\n"
            + "                                         \"type\" => OBJECT,\n"
            + "                                         \"value-type\" => {\n"
            + "                                              \"prop2_2_1_1\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_2_1_2\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             },\n"
            + "                                             \"prop2_2_1_3\" => {\n"
            + "                                                 \"type\" => BOOLEAN,\n"
            + "                                             }\n"
            + "                                         }\n"
            + "                                     }\n"
            + "                                 }\n"
            + "                             }\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "              }\n";


    private static final String nested_lists = "{\n"
            + "                \"type\" => LIST,\n"
            + "                \"description\" => \"The defined permission mappings.\",\n"
            + "                \"expressions-allowed\" => false,\n"
            + "                \"required\" => false,\n"
            + "                \"nillable\" => false,\n"
            + "                \"value-type\" => LIST"
            + "                }\n";

    private static final String simple_map = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"value-type\" => STRING"
            + "                }\n";

    private static final String elytron_simple_permission_mapper_add = "{\n" +
"                \"type\" => LIST,\n" +
"                \"description\" => \"The defined permission mappings.\",\n" +
"                \"expressions-allowed\" => false,\n" +
"                \"required\" => false,\n" +
"                \"nillable\" => false,\n" +
"                \"value-type\" => {\n" +
"                    \"principals\" => {\n" +
"                        \"type\" => LIST,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"Principals to compare when mapping permissions, if the identities principal matches any one in the list it is a match.\",\n" +
"                        \"expressions-allowed\" => true,\n" +
"                        \"nillable\" => true,\n" +
"                        \"value-type\" => STRING\n" +
"                    },\n" +
"                    \"roles\" => {\n" +
"                        \"type\" => LIST,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"Roles to compare when mapping permissions, if the identity is a member of any one in the list it is a match.\",\n" +
"                        \"expressions-allowed\" => true,\n" +
"                        \"nillable\" => true,\n" +
"                        \"value-type\" => STRING\n" +
"                    },\n" +
"                    \"permissions\" => {\n" +
"                        \"type\" => LIST,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"The permissions to assign in the event of a match.\",\n" +
"                        \"expressions-allowed\" => false,\n" +
"                        \"nillable\" => false,\n" +
"                        \"value-type\" => {\n" +
"                            \"class-name\" => {\n" +
"                                \"type\" => STRING,\n" +
"                                \"required\" => false,\n" +
"                                \"description\" => \"The fully qualified class name of the permission.\",\n" +
"                                \"expressions-allowed\" => true,\n" +
"                                \"nillable\" => false,\n" +
"                                \"min-length\" => 1L,\n" +
"                                \"max-length\" => 2147483647L\n" +
"                            },\n" +
"                            \"module\" => {\n" +
"                                \"type\" => STRING,\n" +
"                                \"required\" => false,\n" +
"                                \"description\" => \"The module to use to load the permission.\",\n" +
"                                \"expressions-allowed\" => true,\n" +
"                                \"nillable\" => true,\n" +
"                                \"min-length\" => 1L,\n" +
"                                \"max-length\" => 2147483647L\n" +
"                            },\n" +
"                            \"target-name\" => {\n" +
"                                \"type\" => STRING,\n" +
"                                \"required\" => false,\n" +
"                                \"description\" => \"The target name to pass to the permission as it is constructed.\",\n" +
"                                \"expressions-allowed\" => true,\n" +
"                                \"nillable\" => true,\n" +
"                                \"min-length\" => 1L,\n" +
"                                \"max-length\" => 2147483647L\n" +
"                            },\n" +
"                            \"action\" => {\n" +
"                                \"type\" => STRING,\n" +
"                                \"required\" => false,\n" +
"                                \"description\" => \"The action to pass to the permission as it is constructed.\",\n" +
"                                \"expressions-allowed\" => true,\n" +
"                                \"nillable\" => true,\n" +
"                                \"min-length\" => 1L,\n" +
"                                \"max-length\" => 2147483647L\n" +
"                            }\n" +
"                        }\n" +
"                    }\n" +
"                }\n" +
"            }";

    private static final String compositeSteps = "{\n"
            + "            \"type\" => LIST,\n"
            + "            \"description\" => \"A list of the operation requests that constitute the composite request.\",\n"
            + "            \"expressions-allowed\" => false,\n"
            + "            \"required\" => true,\n"
            + "            \"nillable\" => false,\n"
            + "            \"value-type\" => OBJECT\n"
            + "        }";

    private static final String jgroupsProtocolsAdd = "{\n" +
"                \"type\" => LIST,\n" +
"                \"description\" => \"The list of configured protocols for a protocol stack.\",\n" +
"                \"expressions-allowed\" => false,\n" +
"                \"required\" => false,\n" +
"                \"nillable\" => true,\n" +
"                \"deprecated\" => {\n" +
"                    \"since\" => \"3.0.0\",\n" +
"                    \"reason\" => \"Deprecated. Use separate protocol add operations instead.\"\n" +
"                },\n" +
"                \"value-type\" => {\n" +
"                    \"type\" => {\n" +
"                        \"type\" => STRING,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"The implementation class for a protocol, which determines protocol functionality.\",\n" +
"                        \"expressions-allowed\" => true,\n" +
"                        \"nillable\" => true,\n" +
"                        \"min-length\" => 1L,\n" +
"                        \"max-length\" => 2147483647L\n" +
"                    },\n" +
"                    \"socket-binding\" => {\n" +
"                        \"type\" => STRING,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"Optional socket binding specification for this protocol layer, used to specify IP interfaces and ports for communication.\",\n" +
"                        \"expressions-allowed\" => true,\n" +
"                        \"nillable\" => true,\n" +
"                        \"capability-reference\" => \"org.wildfly.network.socket-binding\",\n" +
"                        \"min-length\" => 1L,\n" +
"                        \"max-length\" => 2147483647L\n" +
"                    },\n" +
"                    \"properties\" => {\n" +
"                        \"type\" => OBJECT,\n" +
"                        \"required\" => false,\n" +
"                        \"description\" => \"Optional LIST parameter specifying the protocol list for the stack.\",\n" +
"                        \"expressions-allowed\" => true,\n" +
"                        \"nillable\" => true,\n" +
"                        \"default\" => {},\n" +
"                        \"value-type\" => STRING\n" +
"                    }\n" +
"                }\n" +
"               }";

    private static final String loginModulesDescr = "{" +
            "\"type\" => LIST," +
            "\"description\" => \"List of authentication modules\"," +
            "\"expressions-allowed\" => false," +
            "\"required\" => true," +
            "\"nillable\" => false," +
            "\"value-type\" => {" +
            "     \"code\" => {" +
            "        \"description\" => \"Class name of the module to be instantiated.\"," +
            "        \"type\" => BOOLEAN," +
            "        \"required\" => false,\n" +
            "        \"nillable\" => false" +
            "     }," +
            "    \"flag\" => {" +
            "        \"description\" => \"The flag controls how the module participates in the overall procedure.\"," +
            "        \"type\" => STRING," +
            "        \"required\" => false,\n" +
            "        \"nillable\" => false," +
            "        \"allowed\" => [" +
            "            \"required\"," +
            "            \"requisite\"," +
            "            \"sufficient\"," +
            "            \"optional\"" +
            "        ]" +
            "    }," +
            "    \"module\" => {" +
            "        \"type\" => STRING," +
            "        \"required\" => false,\n" +
            "        \"nillable\" => true," +
            "        \"description\" => \"Name of JBoss Module where the login module code is located.\"" +
            "    }," +
            "    \"module-options\" => {" +
            "        \"description\" => \"List of module options containing a name/value pair.\"," +
            "        \"required\" => false,\n" +
            "        \"type\" => OBJECT," +
            "        \"value-type\" => STRING," +
            "        \"nillable\" => true" +
            "    }," +
            "    \"aa\" => {" +
            "        \"description\" => \"smth\"," +
            "        \"type\" => OBJECT," +
            "        \"required\" => false,\n" +
            "        \"value-type\" => {" +
            "            \"ab1\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => STRING," +
            "                \"required\" => false,\n" +
            "            }," +
            "            \"ab2\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => STRING," +
            "                \"required\" => false,\n" +
            "            }," +
            "            \"ac1\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => BOOLEAN," +
            "                \"required\" => false,\n" +
            "            }" +
            "        }" +
            "    }," +
            "    \"bb\" => {" +
            "        \"description\" => \"smth\"," +
            "        \"type\" => LIST," +
            "        \"required\" => false,\n" +
            "        \"value-type\" => {" +
            "            \"bb1\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => STRING," +
            "                \"required\" => false,\n" +
            "            }," +
            "            \"bb2\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => STRING," +
            "                \"required\" => false,\n" +
            "            }," +
            "            \"bc1\" => {" +
            "                \"description\" => \"smth\"," +
            "                \"type\" => STRING," +
            "                \"required\" => false,\n" +
            "            }" +
            "        }" +
            "      }," +
            "    \"cc\" => {" +
            "        \"description\" => \"smth\"," +
            "        \"type\" => LIST," +
            "        \"required\" => false,\n" +
            "        \"value-type\" => STRING" +
            "    }" +
            "  }" +
            "}";

    private static final String FILTER_DESCRIPTION = "{\n"
            + "                \"type\" => OBJECT,\n"
            + "                \"description\" => \"Defines a simple filter type.\",\n"
            + "                \"expressions-allowed\" => false,\n"
            + "                \"required\" => false,\n"
            + "                \"nillable\" => true,\n"
            + "                \"alternatives\" => [\"filter-spec\"],\n"
            + "                \"deprecated\" => {\n"
            + "                    \"since\" => \"1.2.0\",\n"
            + "                    \"reason\" => \"Use filter-spec.\"\n"
            + "                },\n"
            + "                \"value-type\" => {\n"
            + "                    \"all\" => {\n"
            + "                        \"type\" => OBJECT,\n"
            + "                        \"required\" => false,\n"
            + "                        \"description\" => \"A filter consisting of several filters in a chain.  If any filter finds the log message to be unloggable,the message will not be logged and subsequent filters will not be checked.\",\n"
            + "                        \"expressions-allowed\" => false,\n"
            + "                        \"nillable\" => true,\n"
            + "                        \"value-type\" => {\n"
            + "                            \"accept\" => {\n"
            + "                                \"type\" => BOOLEAN,\n"
            + "                                \"required\" => false,\n"
            + "                                \"description\" => \"Accepts all log messages.\",\n"
            + "                                \"expressions-allowed\" => false,\n"
            + "                                \"nillable\" => true,\n"
            + "                                \"default\" => true\n"
            + "                            },\n"
            + "                            \"change-level\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"required\" => false,\n"
            + "                                \"description\" => \"A filter which modifies the log record with a new level if the nested filter evaluates true for that record.\",\n"
            + "                                \"expressions-allowed\" => false,\n"
            + "                                \"nillable\" => true,\n"
            + "                                \"allowed\" => [\n"
            + "                                    \"ALL\",\n"
            + "                                    \"FINEST\",\n"
            + "                                    \"FINER\",\n"
            + "                                    \"TRACE\",\n"
            + "                                    \"DEBUG\",\n"
            + "                                    \"FINE\",\n"
            + "                                    \"CONFIG\",\n"
            + "                                    \"INFO\",\n"
            + "                                    \"WARN\",\n"
            + "                                    \"WARNING\",\n"
            + "                                    \"ERROR\",\n"
            + "                                    \"SEVERE\",\n"
            + "                                    \"FATAL\",\n"
            + "                                    \"OFF\"\n"
            + "                                ]\n"
            + "                            },\n"
            + "                            \"deny\" => {\n"
            + "                                \"type\" => BOOLEAN,\n"
            + "                                \"required\" => false,\n"
            + "                                \"description\" => \"Denys all log messages.\",\n"
            + "                                \"expressions-allowed\" => false,\n"
            + "                                \"nillable\" => true,\n"
            + "                                \"default\" => true\n"
            + "                            },\n"
            + "                            \"level\" => {\n"
            + "                                \"type\" => STRING,\n"
            + "                                \"required\" => false,\n"
            + "                                \"description\" => \"A filter which excludes a message with the specified level.\",\n"
            + "                                \"expressions-allowed\" => true,\n"
            + "                                \"nillable\" => true,\n"
            + "                                \"default\" => \"ALL\",\n"
            + "                                \"allowed\" => [\n"
            + "                                    \"ALL\",\n"
            + "                                    \"FINEST\",\n"
            + "                                    \"FINER\",\n"
            + "                                    \"TRACE\",\n"
            + "                                    \"DEBUG\",\n"
            + "                                    \"FINE\",\n"
            + "                                    \"CONFIG\",\n"
            + "                                    \"INFO\",\n"
            + "                                    \"WARN\",\n"
            + "                                    \"WARNING\",\n"
            + "                                    \"ERROR\",\n"
            + "                                    \"SEVERE\",\n"
            + "                                    \"FATAL\",\n"
            + "                                    \"OFF\"\n"
            + "                                ]\n"
            + "                            },\n"
            + "                            \"level-range\" => {\n"
            + "                                \"type\" => OBJECT,\n"
            + "                                \"required\" => false,\n"
            + "                                \"description\" => \"A filter which logs only messages that fall within a level range.\",\n"
            + "                                \"expressions-allowed\" => false,\n"
            + "                                \"nillable\" => true,\n"
            + "                                \"value-type\" => {\n"
            + "                                    \"min-level\" => {\n"
            + "                                        \"type\" => STRING,\n"
            + "                                        \"description\" => \"The minimum (least severe) level, inclusive.\",\n"
            + "                                        \"expressions-allowed\" => false,\n"
            + "                                        \"nillable\" => false,\n"
            + "                                        \"allowed\" => [\n"
            + "                                            \"ALL\",\n"
            + "                                            \"FINEST\",\n"
            + "                                            \"FINER\",\n"
            + "                                            \"TRACE\",\n"
            + "                                            \"DEBUG\",\n"
            + "                                            \"FINE\",\n"
            + "                                            \"CONFIG\",\n"
            + "                                            \"INFO\",\n"
            + "                                            \"WARN\",\n"
            + "                                            \"WARNING\",\n"
            + "                                            \"ERROR\",\n"
            + "                                            \"SEVERE\",\n"
            + "                                            \"FATAL\",\n"
            + "                                            \"OFF\"\n"
            + "                                        ]\n"
            + "                                    },\n"
            + "                                    \"min-inclusive\" => {\n"
            + "                                        \"type\" => BOOLEAN,\n"
            + "                                        \"description\" => \"True if the min-level value is inclusive, false if it is exclusive.\",\n"
            + "                                        \"expressions-allowed\" => false,\n"
            + "                                        \"nillable\" => true,\n"
            + "                                        \"default\" => true\n"
            + "                                    },\n"
            + "                                    \"max-level\" => {\n"
            + "                                        \"type\" => STRING,\n"
            + "                                        \"description\" => \"The maximum (most severe) level, inclusive.\",\n"
            + "                                        \"expressions-allowed\" => false,\n"
            + "                                        \"nillable\" => false,\n"
            + "                                        \"allowed\" => [\n"
            + "                                            \"ALL\",\n"
            + "                                            \"FINEST\",\n"
            + "                                            \"FINER\",\n"
            + "                                            \"TRACE\",\n"
            + "                                            \"DEBUG\",\n"
            + "                                            \"FINE\",\n"
            + "                                            \"CONFIG\",\n"
            + "                                            \"INFO\",\n"
            + "                                            \"WARN\",\n"
            + "                                            \"WARNING\",\n"
            + "                                            \"ERROR\",\n"
            + "                                            \"SEVERE\",\n"
            + "                                            \"FATAL\",\n"
            + "                                            \"OFF\"\n"
            + "                                        ]\n"
            + "                                    },\n"
            + "                                    \"max-inclusive\" => {\n"
            + "                                        \"type\" => BOOLEAN,\n"
            + "                                        \"description\" => \"True if the max-level value is inclusive, false if it is exclusive.\",\n"
            + "                                        \"expressions-allowed\" => false,\n"
            + "                                        \"nillable\" => true,\n"
            + "                                        \"default\" => true\n"
            + "                                    }\n"
            + "                                }\n"
            + "                            }"
            + "                        }"
            + "                    },"
            + "                    \"match\" => {\n"
            + "                         \"type\" => STRING,\n"
            + "                         \"required\" => false,\n"
            + "                         \"description\" => \"A regular-expression-based filter. Used to exclude log records which match or don't match the expression. The regular expression is checked against the raw (unformatted) message.\",\n"
            + "                         \"expressions-allowed\" => false,\n"
            + "                         \"nillable\" => true,\n"
            + "                         \"min-length\" => 1L,\n"
            + "                         \"max-length\" => 2147483647L\n"
            + "                    },"
            + "                }"
            + "            }";

    private static final String VALUETYPE_WITH_FILES
            = "{ \"value-type\": \n"
            + "           {\n"
            + "                \"p1_a\": {\n"
            + "                    \"type\": \"INT\",\n"
            + "                    \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                },\n"
            + "                \"p2_a\": {\n"
            + "                    \"type\": \"INT\",\n"
            + "                    \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                },\n"
            + "                \"p3\": {\n"
            + "                    \"type\": \"LIST\",\n"
            + "                    \"description\": \"\",\n"
            + "                    \"value-type\": {\n"
            + "                        \"oo_file_a\": {\n"
            + "                            \"type\": \"INT\",\n"
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
            + "                            \"" + Util.FILESYSTEM_PATH + "\": true\n"
            + "                        },\n"
            + "                        \"ii_file\": {\n"
            + "                            \"type\": \"STRING\"\n"
            + "                        }\n"
            + "                    }\n"
            + "                }\n"
            + "             }\n"
            + "          }\n";

    @Test
    public void testFilter() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(FILTER_DESCRIPTION);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"all", "match"}), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{all={", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"accept", "change-level", "deny",
            "level", "level-range"}), candidates);
        assertEquals(6, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{all={change-level=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ALL", "CONFIG", "DEBUG",
            "ERROR", "FATAL", "FINE", "FINER", "FINEST", "INFO", "OFF",
            "SEVERE", "TRACE", "WARN", "WARNING"}), candidates);
        assertEquals(19, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{all={change-level=ALL,deny=true,"
                + "level=ALL,level-range={},match=cds,not={},"
                + "replace={pattern=cdsc},accept=true", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(109, i);

        candidates.clear();
        String str = "{}";
        i = new ValueTypeCompleter(propDescr).complete(null, str, str.length(), candidates);
        assertEquals(Arrays.asList(str), candidates);
        assertEquals(0, i);
    }

    @Test
    public void testLoginModules() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(loginModulesDescr);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<String>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc", "code", "flag", "module", "module-options"}), candidates);
        assertEquals(2, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{f", 0, candidates);
        assertEquals(Collections.singletonList("flag"), candidates);
        assertEquals(2, i);


        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{m", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(2, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{module", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(2, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{module=", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*7*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{module=m", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*7*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{flag = ", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"optional", "required", "requisite", "sufficient"}), candidates);
        assertEquals(8, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{flag= s", 0, candidates);
        assertEquals(Collections.singletonList("sufficient"), candidates);
        assertEquals(8, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{flag=requi", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"required", "requisite"}), candidates);
        assertEquals(7, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"false", "true"}), candidates);
        assertEquals(/*-1*/7, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=t", 0, candidates);
        assertEquals(Collections.singletonList("true"), candidates);
        assertEquals(/*-1*/7, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*5*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc",  "flag", "module", "module-options"}), candidates);
        assertEquals(12, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,w", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*10*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,module", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(12, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,fl", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"flag"}), candidates);
        assertEquals(12, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = ", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"optional", "required", "requisite", "sufficient"}), candidates);
        assertEquals(18, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = requi", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"required", "requisite"}), candidates);
        assertEquals(19, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required", 0, candidates);
        assertEquals(Arrays.asList(new String[]{","}), candidates);
        assertEquals(27, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc", "module", "module-options"}), candidates);
        assertEquals(28, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);
        assertEquals(31, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab1", "ab2", "ac1"}), candidates);
        assertEquals(32, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab1", "ab2"}), candidates);
        assertEquals(32, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2", "ac1"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,a", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2", "ac1"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ac1"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"false", "true"}), candidates);
        assertEquals(/*36*/42, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=s", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=f", 0, candidates);
        assertEquals(Collections.singletonList("false"), candidates);
        assertEquals(42, i);

        //assertEquals(Arrays.asList(new String[]{","}), valueTypeHandler.getCandidates(valueType, "code=Main,flag = required,aa={ab1=1,ac1=2}"));
        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=false", 0, candidates);
        assertEquals(Collections.singletonList(","), candidates);
        assertEquals(47, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=2,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2"}), candidates);
        assertEquals(44, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=2},", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"bb", "cc", "module", "module-options"}), candidates);
        assertEquals(45, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=2},bb=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"["}), candidates);
        assertEquals(48, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=2},bb=[{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"bb1", "bb2", "bc1"}), candidates);
        assertEquals(50, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=Main,flag = required,aa={ab1=1,ac1=2},cc=[(", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=\"UsersRoles\",flag=required,module-options=[(", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{}", 0, candidates);
        assertEquals(Arrays.asList(new String[]{",", "]"}), candidates);
        assertEquals(3, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required}", 0, candidates);
        assertEquals(Arrays.asList(new String[]{",", "]"}), candidates);
        assertEquals(17, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc", "code", "flag", "module", "module-options"}), candidates);
        assertEquals(28, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{f", 0, candidates);
        assertEquals(Collections.singletonList("flag"), candidates);
        assertEquals(28, i);


        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{m", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(28, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{module", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(28, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{module=", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*7*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{module=m", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*7*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{flag = ", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"optional", "required", "requisite", "sufficient"}), candidates);
        assertEquals(34, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{flag= s", 0, candidates);
        assertEquals(Collections.singletonList("sufficient"), candidates);
        assertEquals(34, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{flag=requi", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"required", "requisite"}), candidates);
        assertEquals(33, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"false", "true"}), candidates);
        assertEquals(/*-1*/33, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=t", 0, candidates);
        assertEquals(Collections.singletonList("true"), candidates);
        assertEquals(/*-1*/33, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*5*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc", "flag", "module", "module-options"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,w", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1 /*10*/, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,module", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"module", "module-options"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,fl", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"flag"}), candidates);
        assertEquals(38, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = ", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"optional", "required", "requisite", "sufficient"}), candidates);
        assertEquals(44, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = requi", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"required", "requisite"}), candidates);
        assertEquals(45, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required", 0, candidates);
        assertEquals(Arrays.asList(new String[]{","}), candidates);
        assertEquals(53, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"aa", "bb", "cc", "module", "module-options"}), candidates);
        assertEquals(54, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);
        assertEquals(57, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab1", "ab2", "ac1"}), candidates);
        assertEquals(58, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab1", "ab2"}), candidates);
        assertEquals(58, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2", "ac1"}), candidates);
        assertEquals(64, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,a", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2", "ac1"}), candidates);
        assertEquals(64, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ac1"}), candidates);
        assertEquals(64, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"false", "true"}), candidates);
        assertEquals(/*36*/68, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=s", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=f", 0, candidates);
        assertEquals(Collections.singletonList("false"), candidates);
        assertEquals(68, i);

        //assertEquals(Arrays.asList(new String[]{","}), valueTypeHandler.getCandidates(valueType, "code=Main,flag = required,aa={ab1=1,ac1=2}"));
        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=false", 0, candidates);
        assertEquals(Collections.singletonList(","), candidates);
        assertEquals(73, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=2,", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"ab2"}), candidates);
        assertEquals(70, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=2},", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"bb", "cc", "module", "module-options"}), candidates);
        assertEquals(71, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=2},bb=", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"["}), candidates);
        assertEquals(74, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=Main,flag = required,aa={ab1=1,ac1=2},bb=[{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"bb1", "bb2", "bc1"}), candidates);
        assertEquals(76, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=\"UsersRoles\",flag=required,module-options=[(", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{code=toto,flag=required},{code=\"UsersRoles\",flag=required,module-options=[{", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        String cmd = "[{code=toto,flag=required},{code=\"UsersRoles\",flag=required}]";
        i = new ValueTypeCompleter(propDescr).complete(null, cmd, 0, candidates);
        assertEquals(Arrays.asList(cmd), candidates);
        assertEquals(0, i);
    }

    @Test
    public void testFileSystem() throws Exception {
        String radical = "valuetype-" + System.currentTimeMillis() + "-test";
        File f = new File(radical + ".txt");
        f.createNewFile();
        try {
            CommandContext ctx = CommandContextFactory.getInstance().newCommandContext();
            ModelNode valueType = ModelNode.fromJSONString(VALUETYPE_WITH_FILES);
            {
                List<String> candidates = new ArrayList<>();
                String content = "{p1_a=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.get(0).equals(f.getName()));
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p1_a=" + f.getName();
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.get(0).equals(","));
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p1_a=toto, p2_a=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.get(0).equals(f.getName()));
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p3=[ { oo_file_a=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.get(0).equals(f.getName()));
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p3=[ { ii_file=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.isEmpty());
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p3=[ { oo_file_a=toto, ii_file=titi }, { oo_file_a=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.size() == 1);
                assertTrue(candidates.get(0).equals(f.getName()));
            }

            {
                List<String> candidates = new ArrayList<>();
                String content = "{p4= { ii_file=" + radical;
                new ValueTypeCompleter(valueType).complete(ctx, content, content.length() - 1, candidates);
                assertTrue(candidates.isEmpty());
            }
        } finally {
            f.delete();
        }
    }

    @Test
    public void testJgroupsProtocolAdd() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(jgroupsProtocolsAdd);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<String>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"properties", "socket-binding", "type"}), candidates);
        assertEquals(2, i);
    }

    @Test
    public void testCompositeSteps() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(compositeSteps);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{", 0, candidates);
        assertTrue(candidates.isEmpty());

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{a", 0, candidates);
        assertTrue(candidates.isEmpty());
    }

    @Test
    public void testElytronPermissionMapper() throws Exception {
    final ModelNode propDescr = ModelNode.fromString(elytron_simple_permission_mapper_add);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[", 0, candidates);
        assertEquals(Arrays.asList(new String[]{"{"}), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{", 0, candidates);
        assertEquals(Arrays.asList("permissions", "principals", "roles"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions", 0, candidates);
        assertEquals(Arrays.asList("permissions="), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[", 0, candidates);
        assertEquals(Arrays.asList("{"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{", 0, candidates);
        assertEquals(Arrays.asList("action", "class-name", "module", "target-name"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name", 0, candidates);
        assertEquals(Arrays.asList("class-name="), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",", 0, candidates);
        assertEquals(Arrays.asList("action", "module", "target-name"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action", 0, candidates);
        assertEquals(Arrays.asList("action="), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx}", 0, candidates);
        assertEquals(Arrays.asList(",", "]"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},", 0, candidates);
        assertEquals(Arrays.asList("{"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{", 0, candidates);
        assertEquals(Arrays.asList("action", "class-name", "module", "target-name"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}]", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}]", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],", 0, candidates);
        assertEquals(Arrays.asList("principals", "roles"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],", 0, candidates);
        assertEquals(Arrays.asList("principals", "roles"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[", 0, candidates);
        assertEquals(Arrays.asList(), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[]", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],", 0, candidates);
        assertEquals(Arrays.asList("roles"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles", 0, candidates);
        assertEquals(Arrays.asList("roles="), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[", 0, candidates);
        assertEquals(Arrays.asList(), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]}", 0, candidates);
        assertEquals(Arrays.asList(",", "]"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},", 0, candidates);
        assertEquals(Arrays.asList("{"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},{", 0, candidates);
        assertEquals(Arrays.asList("permissions", "principals", "roles"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},"
                + "{roles=[],permissions=[{", 0, candidates);
        assertEquals(Arrays.asList("action", "class-name", "module", "target-name"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},"
                + "{roles=[],permissions=[{action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},"
                + "                       {action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},{", 0, candidates);
        assertEquals(Arrays.asList("action", "class-name", "module", "target-name"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},"
                + "{roles=[],permissions=[{action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},"
                + "                       {action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},{}],", 0, candidates);
        assertEquals(Arrays.asList("principals"), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},"
                + "{roles=[],permissions=[{action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},"
                + "                       {action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},{}],principals=[]", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);

        candidates.clear();
        String cmd = "[{permissions=[{class-name=\"toto\",action=xxx},{}],principals=[],roles=[]},"
                + "{roles=[],permissions=[{action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},"
                + "                       {action=cdscds,class-name=cdscds,module=cdscds,target-name=njdsc},{}],principals=[]}]";
        i = new ValueTypeCompleter(propDescr).complete(null, cmd, 0, candidates);
        assertEquals(Arrays.asList(cmd), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{roles=", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{roles=[", 0, candidates);
        assertEquals(Arrays.asList(), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{roles=[role=", 0, candidates);
        assertEquals(Arrays.asList(), candidates);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[{roles=[role=xxx", 0, candidates);
        assertEquals(Arrays.asList(), candidates);

    }

    @Test
    public void testNestedLists() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(nested_lists);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;

        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[", 0, candidates);
        assertEquals(Collections.singletonList("["), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[[]", 0, candidates);
        assertEquals(Arrays.asList(",", "]"), candidates);
        assertEquals(3, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[[],", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);
        assertEquals(4, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[[],[]", 0, candidates);
        assertEquals(Arrays.asList(",", "]"), candidates);
        assertEquals(6, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "[[],[123,qwert,dscds],", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);
        assertEquals(22, i);

        candidates.clear();
        String cmd = "[[],[123,qwert,dscds]]";
        i = new ValueTypeCompleter(propDescr).complete(null, cmd, 0, candidates);
        assertEquals(Arrays.asList(cmd), candidates);
        assertEquals(0, i);
    }

    @Test
    public void testNestedObjects() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(nested_objects);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=", 0, candidates);
        assertEquals(Arrays.asList("false", "true"), candidates);
        assertEquals(40, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(44, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,", 0, candidates);
        assertEquals(Arrays.asList("prop1_1_1_2", "prop1_1_1_3"), candidates);
        assertEquals(45, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(79, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",", 0, candidates);
        assertEquals(Arrays.asList("prop1_1_2"), candidates);
        assertEquals(81, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2=", 0, candidates);
        assertEquals(Arrays.asList("{"), candidates);
        assertEquals(91, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={", 0, candidates);
        assertEquals(Arrays.asList("prop1_1_2_1", "prop1_1_2_2", "prop1_1_2_3"), candidates);
        assertEquals(92, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=", 0, candidates);
        assertEquals(Arrays.asList("false", "true"), candidates);
        assertEquals(139, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(144, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(145, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}}", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(146, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}},", 0, candidates);
        assertEquals(Arrays.asList("prop1_2"), candidates);
        assertEquals(147, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}},"
                + "prop1_2={}", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(157, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}},"
                + "prop1_2={}},", 0, candidates);
        assertEquals(Arrays.asList("prop2"), candidates);
        assertEquals(159, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}},"
                + "prop1_2={}},prop2={}", 0, candidates);
        assertEquals(Arrays.asList("}"), candidates);
        assertEquals(167, i);

        candidates.clear();
        String cmd = "{prop1={prop1_1={prop1_1_1={prop1_1_1_1=true,prop1_1_1_2=false,prop1_1_1_3=true}"
                + ",prop1_1_2={prop1_1_2_1=true,prop1_1_2_2=false,prop1_1_2_3=false}},"
                + "prop1_2={}},prop2={}}";
        i = new ValueTypeCompleter(propDescr).complete(null, cmd, 0, candidates);
        assertEquals(Arrays.asList(cmd), candidates);
        assertEquals(0, i);

        candidates.clear();
        String str = "{}";
        i = new ValueTypeCompleter(propDescr).complete(null, str, str.length(), candidates);
        assertEquals(Arrays.asList(str), candidates);
        assertEquals(0, i);
    }

    @Test
    public void testMap() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(simple_map);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;
        i = new ValueTypeCompleter(propDescr).complete(null, "", 0, candidates);
        assertEquals(Arrays.asList("{"), candidates);
        assertEquals(0, i);
    }

    private static class TestCapabilityReferenceCompleter extends CapabilityReferenceCompleter {

        private final List<String> capabilities;

        public TestCapabilityReferenceCompleter(List<String> capabilities) {
            super(new CandidatesProvider() {
                @Override
                public Collection<String> getAllCandidates(CommandContext ctx) {
                    return capabilities;
                }
            });
            this.capabilities = capabilities;
        }

        @Override
        public List<String> getCapabilityReferenceNames(CommandContext ctx,
                OperationRequestAddress address, String staticPart) {
            return capabilities;
        }

    }

    @Test
    public void testCapabilities() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(capabilities_prop);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();
        List<String> capabilities = new ArrayList<>();
        CapabilityCompleterFactory factory = (OperationRequestAddress address, String staticPart) -> {
            return new TestCapabilityReferenceCompleter(capabilities);
        };
        int i;
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "{prop1=", 0, candidates);
        assertEquals(Arrays.asList(), candidates);
        assertEquals(-1, i);

        capabilities.add("coco");
        capabilities.add("prefMapper001");
        capabilities.add("prefMapper002");

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "{prop1=", 0, candidates);
        assertEquals(capabilities, candidates);
        assertEquals(7, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "{prop1=c", 0, candidates);
        assertEquals(Arrays.asList("coco"), candidates);
        assertEquals(7, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "{prop1=coco", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(11, i);
    }

    @Test
    public void testListCapabilities() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(role_mapper);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();
        List<String> capabilities = new ArrayList<>();
        CapabilityCompleterFactory factory = (OperationRequestAddress address, String staticPart) -> {
            return new TestCapabilityReferenceCompleter(capabilities);
        };
        int i;
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "", 0, candidates);
        assertEquals(Arrays.asList("["), candidates);
        assertEquals(0, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[", 0, candidates);
        assertEquals(Arrays.asList("]"), candidates);
        assertEquals(1, i);

        capabilities.add("coco");
        capabilities.add("prefMapper001");
        capabilities.add("prefMapper002");

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[", 0, candidates);
        assertEquals(capabilities, candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[p", 0, candidates);
        assertEquals(Arrays.asList("prefMapper001", "prefMapper002"), candidates);
        assertEquals(1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(14, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,", 0, candidates);
        assertEquals(Arrays.asList("coco", "prefMapper002"), candidates);
        assertEquals(15, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,c", 0, candidates);
        assertEquals(Arrays.asList("coco"), candidates);
        assertEquals(15, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,coco", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(19, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,coco,", 0, candidates);
        assertEquals(Arrays.asList("prefMapper002"), candidates);
        assertEquals(20, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,coco,c", 0, candidates);
        assertEquals(Arrays.asList(), candidates);
        assertEquals(-1, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,coco,p", 0, candidates);
        assertEquals(Arrays.asList("prefMapper002"), candidates);
        assertEquals(20, i);

        candidates.clear();
        i = new ValueTypeCompleter(propDescr, factory).complete(null, "[prefMapper001,coco,prefMapper002", 0, candidates);
        assertEquals(Arrays.asList("]"), candidates);
        assertEquals(33, i);

    }

    @Test
    public void testBytes() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(bytes_prop);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();

        int i;

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=", 0, candidates);
        assertEquals(Arrays.asList("bytes{"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=b", 0, candidates);
        assertEquals(Arrays.asList("bytes{"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes", 0, candidates);
        assertEquals(Arrays.asList("bytes{"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x31", 0, candidates);
        assertEquals(Arrays.asList("bytes{0x31,"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{+127", 0, candidates);
        assertEquals(Arrays.asList("bytes{+127,"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{127", 0, candidates);
        assertEquals(Arrays.asList("bytes{127,"), candidates);
        assertEquals(7, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{12", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x31,", 0, candidates);
        assertEquals(Collections.emptyList(), candidates);
        assertEquals(-1, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x31,0x32}", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(23, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{}", 0, candidates);
        assertEquals(Arrays.asList(","), candidates);
        assertEquals(14, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x31,0x32},", 0, candidates);
        assertEquals(Arrays.asList("prop2"), candidates);
        assertEquals(24, i);
        candidates.clear();

        i = new ValueTypeCompleter(propDescr).complete(null, "{prop1=bytes{0x31,0x32}, prop2=", 0, candidates);
        assertEquals(Arrays.asList("false","true"), candidates);
        assertEquals(31, i);
        candidates.clear();
    }

    @Test
    public void testInvalidSyntax() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(elytron_provider);
        assertTrue(propDescr.isDefined());

        final List<String> candidates = new ArrayList<>();
        int i;
        i = new ValueTypeCompleter(propDescr).complete(null,
                "[{class-names=[com.example.Class]},class-names=[{com.example.AnotherClass}",
                0, candidates);
        assertTrue(candidates.isEmpty());
        assertEquals(-1, i);
    }

    @Test
    public void testRequired() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(required_alternatives);
        assertTrue(propDescr.isDefined());
        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{", 0, candidates);
            assertEquals(Arrays.asList("class-names*", "load-services*", "module*",
                    "path*", "property-list*", "recursive", "relative-to"), candidates);
            assertEquals(1, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{class-names=[toto,titi],", 0, candidates);
            assertEquals(Arrays.asList("property-list*", "recursive"), candidates);
            assertEquals(25, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{class-names=[toto,titi],l", 0, candidates);
            assertEquals(Arrays.asList("load-services"), candidates);
            assertEquals(25, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{path=toto,", 0, candidates);
            assertEquals(Arrays.asList("recursive", "relative-to*"), candidates);
            assertEquals(11, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{property-list=[],", 0, candidates);
            assertEquals(Arrays.asList("class-names*", "load-services*", "module*", "recursive"), candidates);
            assertEquals(18, i);
        }

    }

    @Test
    public void testRequiresNotHideMultipleNotRequiredAlternatives() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(requires_alternatives1);
        assertTrue(propDescr.isDefined());

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{", 0, candidates);
            assertEquals(Arrays.asList("A*", "B", "C",
                    "D*", "E"), candidates);
            assertEquals(1, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,", 0, candidates);
            assertEquals(Arrays.asList("B*", "C*",
                    "D*", "E"), candidates);
            assertEquals(8, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,D=true,", 0, candidates);
            assertEquals(Arrays.asList("C*", "E"), candidates);
            assertEquals(15, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,B=true,E=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
            assertEquals(21, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,C=true,", 0, candidates);
            assertEquals(Arrays.asList("D*", "E"), candidates);
            assertEquals(15, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,C=true,D=true,E=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
            assertEquals(28, i);
        }
    }

    @Test
    public void testRequiresInvalidAlternatives() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(requiresInvalidAlternatives);
        assertTrue(propDescr.isDefined());

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{", 0, candidates);
            assertEquals(Arrays.asList("A*", "B*", "C"), candidates);
            assertEquals(1, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{B=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
            assertEquals(7, i);
        }
    }

    @Test
    public void testRequiresAlternatives() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(requiresAlternatives);
        assertTrue(propDescr.isDefined());

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{", 0, candidates);
            assertEquals(Arrays.asList("A*", "B*", "C"), candidates);
            assertEquals(1, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{C=true,", 0, candidates);
            assertEquals(Arrays.asList("A*", "B*"), candidates);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{C=true,A=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{C=true,B=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{B=true,", 0, candidates);
            assertEquals(Arrays.asList("C"), candidates);
            assertEquals(8, i);
        }
    }

    @Test
    public void testRequiresHideSingleNotRequiredAlternatives() throws Exception {
        final ModelNode propDescr = ModelNode.fromString(requires_alternatives2);
        assertTrue(propDescr.isDefined());

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{", 0, candidates);
            assertEquals(Arrays.asList("A*", "B", "C",
                    "D*"), candidates);
            assertEquals(1, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,", 0, candidates);
            assertEquals(Arrays.asList("B*", "C*"), candidates);
            assertEquals(8, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,B=true,", 0, candidates);
            assertEquals(Arrays.asList("C"), candidates);
            assertEquals(15,i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,C=true,", 0, candidates);
            assertEquals(Arrays.asList("B"), candidates);
            assertEquals(15, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,B=true,C=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
            assertEquals(21, i);
        }

        {
            final List<String> candidates = new ArrayList<>();
            int i = new ValueTypeCompleter(propDescr).complete(null,
                    "{A=true,D=true,C=true", 0, candidates);
            assertEquals(Arrays.asList("}"), candidates);
            assertEquals(21, i);
        }
    }
}
