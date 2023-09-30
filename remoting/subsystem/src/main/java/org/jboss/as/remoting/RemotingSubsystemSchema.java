/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.remoting;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;

import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.dmr.ModelNode;
import org.jboss.staxmapper.IntVersion;
import org.jboss.staxmapper.XMLExtendedStreamReader;

/**
 * Enumerates the supported schemas of the remoting subsystem
 */
public enum RemotingSubsystemSchema implements PersistentSubsystemSchema<RemotingSubsystemSchema> {
    VERSION_1_0(1, 0) {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem10Parser().readElement(reader, value);
        }
    },
    VERSION_1_1(1, 1) {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem11Parser().readElement(reader, value);
        }
    },
    VERSION_1_2(1, 2) {
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem12Parser().readElement(reader, value);
        }
    },
    VERSION_2_0(2, 0) { // WildFly 8
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem20Parser().readElement(reader, value);
        }
    },
    VERSION_3_0(3, 0) { // WildFly 9 - 10
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem30Parser().readElement(reader, value);
        }
    },
    VERSION_4_0(4, 0) { // WildFly 11 - 26
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem40Parser().readElement(reader, value);
        }
    },
    VERSION_5_0(5, 0) { // WildFly 27 - 29
        @Override
        public void readElement(XMLExtendedStreamReader reader, List<ModelNode> value) throws XMLStreamException {
            new RemotingSubsystem40Parser().readElement(reader, value);
        }
    },
    VERSION_6_0(6, 0), // WildFly 30 - present
    ;
    static final RemotingSubsystemSchema CURRENT = VERSION_6_0;

    private VersionedNamespace<IntVersion, RemotingSubsystemSchema> namespace;

    RemotingSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createLegacySubsystemURN(RemotingExtension.SUBSYSTEM_NAME, new IntVersion(major, minor));
    }

    @Override
    public VersionedNamespace<IntVersion, RemotingSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.PersistentResourceXMLBuilder propertiesBuilder = builder(PropertyResource.PATH).setXmlWrapperElement(CommonAttributes.PROPERTIES).addAttributes(PropertyResource.ATTRIBUTES.stream());

        return builder(RemotingSubsystemRootResource.PATH, this.namespace).addAttributes(RemotingSubsystemRootResource.ATTRIBUTES.stream())
            .addChild(builder(ConnectorResource.PATH).addAttributes(ConnectorResource.ATTRIBUTES.stream())
                    .addChild(propertiesBuilder)
                    .addChild(builder(SaslResource.SASL_CONFIG_PATH).addAttributes(SaslResource.ATTRIBUTES.stream())
                            .addChild(builder(SaslPolicyResource.SASL_POLICY_CONFIG_PATH).addAttributes(SaslPolicyResource.ATTRIBUTES.stream()))
                            .addChild(propertiesBuilder)))
            .addChild(builder(HttpConnectorResource.PATH).addAttributes(HttpConnectorResource.ATTRIBUTES.stream())
                    .addChild(propertiesBuilder)
                    .addChild(builder(SaslResource.SASL_CONFIG_PATH).addAttributes(SaslResource.ATTRIBUTES.stream())
                            .addChild(builder(SaslPolicyResource.SASL_POLICY_CONFIG_PATH).addAttributes(SaslPolicyResource.ATTRIBUTES.stream()))
                            .addChild(propertiesBuilder)))
            .addChild(PersistentResourceXMLDescription.decorator(Element.OUTBOUND_CONNECTIONS.getLocalName())
                    .addChild(builder(LocalOutboundConnectionResourceDefinition.PATH).addAttributes(LocalOutboundConnectionResourceDefinition.ATTRIBUTES.stream())
                            .addChild(propertiesBuilder))
                    .addChild(builder(RemoteOutboundConnectionResourceDefinition.PATH).addAttributes(RemoteOutboundConnectionResourceDefinition.ATTRIBUTES.stream())
                            .addChild(propertiesBuilder))
                    .addChild(builder(GenericOutboundConnectionResourceDefinition.PATH).addAttributes(GenericOutboundConnectionResourceDefinition.ATTRIBUTES.stream())
                            .addChild(propertiesBuilder)))
            .build();
    }
}
