/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.wildfly.subsystem;

import static org.mockito.Mockito.mock;

import java.util.EnumSet;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.persistence.SubsystemMarshallingContext;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Stability;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLElementWriter;
import org.jboss.staxmapper.XMLExtendedStreamReader;
import org.jboss.staxmapper.XMLExtendedStreamWriter;
import org.junit.Assert;
import org.junit.Test;

/**
 * Unit test for {@link SubsystemPersistence}.
 */
public class SubsystemPersistenceTestCase {

    static final PathElement PATH = PathElement.pathElement(ModelDescriptionConstants.SUBSYSTEM, "foo");

    @Test
    public void test() {
        XMLElementWriter<SubsystemMarshallingContext> writer = mock(XMLElementWriter.class);
        SubsystemPersistence<TestSubsystemSchema> persistence = SubsystemPersistence.of(TestSubsystemSchema.V_2_0, writer);
        for (TestSubsystemSchema schema : EnumSet.allOf(TestSubsystemSchema.class)) {
            Assert.assertSame(schema, persistence.getReader(schema));
        }
        for (Stability stability : EnumSet.allOf(Stability.class)) {
            // Writer should be the same for all stability levels
            Assert.assertSame(writer, persistence.getWriter(stability));
        }

        persistence = SubsystemPersistence.of(EnumSet.of(TestSubsystemSchema.V_2_0, TestSubsystemSchema.V_2_0_COMMUNITY, TestSubsystemSchema.V_2_0_PREVIEW, TestSubsystemSchema.V_2_0_EXPERIMENTAL).stream().collect(Collectors.toMap(Function.identity(), Function.<XMLElementWriter<SubsystemMarshallingContext>>identity())));
        for (TestSubsystemSchema schema : EnumSet.allOf(TestSubsystemSchema.class)) {
            Assert.assertSame(schema, persistence.getReader(schema));
        }
        // V_2_0 provides writer for each stability level
        Assert.assertSame(TestSubsystemSchema.V_2_0, persistence.getWriter(Stability.DEFAULT));
        Assert.assertSame(TestSubsystemSchema.V_2_0_COMMUNITY, persistence.getWriter(Stability.COMMUNITY));
        Assert.assertSame(TestSubsystemSchema.V_2_0_PREVIEW, persistence.getWriter(Stability.PREVIEW));
        Assert.assertSame(TestSubsystemSchema.V_2_0_EXPERIMENTAL, persistence.getWriter(Stability.EXPERIMENTAL));

        // V_1_1 provides writer for specific stability levels
        persistence = SubsystemPersistence.of(EnumSet.of(TestSubsystemSchema.V_1_1, TestSubsystemSchema.V_1_1_PREVIEW).stream().collect(Collectors.toMap(Function.identity(), Function.<XMLElementWriter<SubsystemMarshallingContext>>identity())));
        for (TestSubsystemSchema schema : EnumSet.allOf(TestSubsystemSchema.class)) {
            Assert.assertSame(schema, persistence.getReader(schema));
        }
        Assert.assertSame(TestSubsystemSchema.V_1_1, persistence.getWriter(Stability.DEFAULT));
        Assert.assertSame(TestSubsystemSchema.V_1_1, persistence.getWriter(Stability.COMMUNITY));
        Assert.assertSame(TestSubsystemSchema.V_1_1_PREVIEW, persistence.getWriter(Stability.PREVIEW));
        Assert.assertSame(TestSubsystemSchema.V_1_1_PREVIEW, persistence.getWriter(Stability.EXPERIMENTAL));
    }

    enum TestSubsystemSchema implements SubsystemSchema<TestSubsystemSchema>, XMLElementWriter<SubsystemMarshallingContext> {
        V_1_0(1, 0, Stability.DEFAULT),
        V_1_1(1, 1, Stability.DEFAULT),
        V_1_1_PREVIEW(1, 1, Stability.PREVIEW),
        V_2_0(2, 0, Stability.DEFAULT),
        V_2_0_COMMUNITY(2, 0, Stability.COMMUNITY),
        V_2_0_PREVIEW(2, 0, Stability.PREVIEW),
        V_2_0_EXPERIMENTAL(2, 0, Stability.EXPERIMENTAL),
        ;

        private final VersionedNamespace<IntVersion, TestSubsystemSchema> namespace;

        TestSubsystemSchema(int major, int minor, Stability stability) {
            this.namespace = SubsystemSchema.createSubsystemURN(PATH.getValue(), stability, new IntVersion(major, minor));
        }

        @Override
        public VersionedNamespace<IntVersion, TestSubsystemSchema> getNamespace() {
            return this.namespace;
        }

        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
        }

        @Override
        public void writeContent(XMLExtendedStreamWriter streamWriter, SubsystemMarshallingContext value) throws XMLStreamException {
        }
    }
}
