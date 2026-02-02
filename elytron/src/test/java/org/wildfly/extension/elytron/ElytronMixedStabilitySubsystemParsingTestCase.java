/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import mockit.Mock;
import org.jboss.as.model.test.ModelTestUtils;
import org.jboss.as.subsystem.test.AbstractSubsystemSchemaTest;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.version.Stability;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import mockit.MockUp;

import java.io.IOException;
import java.security.Security;
import java.util.EnumSet;

import static jakarta.security.auth.message.config.AuthConfigFactory.DEFAULT_FACTORY_SECURITY_PROPERTY;

@RunWith(Parameterized.class)
public class ElytronMixedStabilitySubsystemParsingTestCase extends AbstractSubsystemSchemaTest<ElytronSubsystemSchema> {

    @BeforeClass
    public static void transferSystemProperty() {
        String value = System.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
        if (value != null) {
            String securityValue = Security.getProperty(DEFAULT_FACTORY_SECURITY_PROPERTY);
            if (securityValue == null) {
                Security.setProperty(DEFAULT_FACTORY_SECURITY_PROPERTY, value);
            }
        }

    }

    private static void mockReadResourceWithValidSubsystemTestFilePaths() {
        Class<?> classToMock;
        try {
            classToMock = Class.forName("org.jboss.as.subsystem.test.AbstractSubsystemTest", true, AbstractSubsystemTest.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new NoClassDefFoundError(e.getMessage());
        }
        new MockUp<>(classToMock) {
            @Mock
            private String readResource(String name) throws IOException {
                boolean isCurrent = ElytronSubsystemSchema.CURRENT.entrySet().stream()
                        .anyMatch(entry -> {
                            Stability stability = entry.getKey();
                            String uri = entry.getValue().getNamespace().getUri();
                            String version = uri.substring(uri.lastIndexOf(':') + 1);
                            boolean versionMatches = name.contains(version + ".xml");
                            if (stability == Stability.DEFAULT) {
                                // For DEFAULT stability, version must match and name must NOT contain stability indicators
                                return versionMatches && !name.contains("-community-") && !name.contains("-preview-");
                            } else {
                                // For non-DEFAULT stability, version must match and name must contain the stability name
                                return versionMatches && name.contains("-" + stability.toString().toLowerCase() + "-");
                            }
                        });
                if (!isCurrent) {
                    // older versions use legacy-elytron-subsystem-*.xml
                    String resourceName = name.contains("community-18.0") ? name.replace("elytron", "elytron-subsystem") : name.replace("elytron", "legacy-elytron-subsystem");
                    return ModelTestUtils.readResource(getClass(), resourceName);
                } else {
                    return ModelTestUtils.readResource(getClass(), name.replace("elytron", "elytron-subsystem"));
                }
            }
        };
    }

    @BeforeClass
    public static void updatePathsForSubsystemTestFiles() {
        mockReadResourceWithValidSubsystemTestFilePaths();
    }

    @Parameters(name = "{0}")
    public static Iterable<ElytronSubsystemSchema> parameters() {
        return EnumSet.allOf(ElytronSubsystemSchema.class);
    }

    public ElytronMixedStabilitySubsystemParsingTestCase(ElytronSubsystemSchema schema) {
        // mock the method that returns path to string for all except the current
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension(), schema, ElytronSubsystemSchema.CURRENT.get(schema.getStability()) != null ? ElytronSubsystemSchema.CURRENT.get(schema.getStability()) : ElytronSubsystemSchema.CURRENT.get(Stability.DEFAULT));
    }

}
