/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.subsystem.test;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

import org.jboss.as.controller.Extension;
import org.jboss.as.controller.Feature;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.subsystem.test.AdditionalInitialization.ManagementAdditionalInitialization;
import org.jboss.as.version.Stability;

/**
 * A base class for subsystem parsing tests that utilize {@link SubsystemSchema}.
 * Generally, tests extending this class will be annotated with <code>@RunWith(Parameterized.class)</code>
 * and specify the schemas to test via a public static method annotated with <code>@Parameters</code>.
 * @author Paul Ferraro
 */
public abstract class AbstractSubsystemSchemaTest<S extends SubsystemSchema<S>> extends AbstractSubsystemBaseTest {
    private final S schema;
    private final boolean latest;

    /**
     * Constructs a new subsystem parsing test
     * @param subsystemName the name of the target subsystem
     * @param extension the target extension
     * @param testSchema the target schema
     * @param currentSchema the current schema
     */
    protected AbstractSubsystemSchemaTest(String subsystemName, Extension extension, S testSchema, S currentSchema) {
        this(subsystemName, extension, testSchema, Set.of(currentSchema));
    }

    /**
     * Constructs a new subsystem parsing test
     * @param subsystemName the name of the target subsystem
     * @param extension the target extension
     * @param testSchema the target schema
     * @param currentSchemas the set of current schemas
     */
    protected AbstractSubsystemSchemaTest(String subsystemName, Extension extension, S testSchema, Set<S> currentSchemas) {
        super(subsystemName, extension, testSchema.getStability());
        this.schema = testSchema;
        // Determine current schema version for the same stability
        S current = Feature.map(currentSchemas).get(testSchema.getStability());
        this.latest = testSchema.since(current);
    }

    /**
     * Returns the subsystem schema being tested.
     * @return a subsystem schema
     */
    protected S getSubsystemSchema() {
        return this.schema;
    }

    /**
     * Returns the path pattern of the subsystem test XML.
     * @return a formatter pattern
     */
    protected String getSubsystemXmlPathPattern() {
        return (this.schema.getStability() == Stability.DEFAULT) ? "%1$s-%2$d.%3$d.xml" : "%1$s-%4$s-%2$d.%3$d.xml";
    }

    /**
     * Returns the path pattern of the subsystem XSD.
     * @return a formatter pattern
     */
    protected String getSubsystemXsdPathPattern() {
        return (this.schema.getStability() == Stability.DEFAULT) ? "schema/wildfly-%1$s_%2$d_%3$d.xsd" : "schema/wildfly-%1$s_%4$s_%2$d_%3$d.xsd";
    }

    @Override
    protected String getSubsystemXml() throws IOException {
        return this.readResource(String.format(Locale.ROOT, this.getSubsystemXmlPathPattern(), this.getMainSubsystemName(), this.schema.getVersion().major(), this.schema.getVersion().minor(), this.schema.getStability()));
    }

    @Override
    protected String getSubsystemXsdPath() throws Exception {
        return String.format(Locale.ROOT, this.getSubsystemXsdPathPattern(), this.getMainSubsystemName(), this.schema.getVersion().major(), this.schema.getVersion().minor(), this.schema.getStability());
    }

    @Override
    public void testSubsystem() throws Exception {
        // Only compare XML for the latest version
        this.standardSubsystemTest(null, this.latest);
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return new ManagementAdditionalInitialization(this.schema);
    }
}
