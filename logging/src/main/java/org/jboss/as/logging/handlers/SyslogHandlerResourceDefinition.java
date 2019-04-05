/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2018 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.logging.handlers;

import static org.jboss.as.logging.CommonAttributes.ENABLED;
import static org.jboss.as.logging.CommonAttributes.LEVEL;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.DefaultAttributeMarshaller;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.operations.validation.EnumValidator;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.transform.description.DiscardAttributeChecker;
import org.jboss.as.controller.transform.description.RejectAttributeChecker;
import org.jboss.as.controller.transform.description.ResourceTransformationDescriptionBuilder;
import org.jboss.as.logging.Attribute;
import org.jboss.as.logging.ElementAttributeMarshaller;
import org.jboss.as.logging.KnownModelVersion;
import org.jboss.as.logging.LoggingExtension;
import org.jboss.as.logging.PropertyAttributeDefinition;
import org.jboss.as.logging.TransformerResourceDefinition;
import org.jboss.as.logging.capabilities.Capabilities;
import org.jboss.as.logging.handlers.HandlerOperations.HandlerAddOperationStepHandler;
import org.jboss.as.logging.handlers.HandlerOperations.LogHandlerWriteAttributeHandler;
import org.jboss.as.logging.resolvers.ModelNodeResolver;
import org.jboss.as.logging.validators.Validators;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.jboss.logmanager.handlers.SyslogHandler.Facility;
import org.jboss.logmanager.handlers.SyslogHandler.SyslogType;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
public class SyslogHandlerResourceDefinition extends TransformerResourceDefinition {

    public static final String NAME = "syslog-handler";
    private static final PathElement SYSLOG_HANDLER_PATH = PathElement.pathElement(NAME);

    public static final PropertyAttributeDefinition APP_NAME = PropertyAttributeDefinition.Builder.of("app-name", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setPropertyName("appName")
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    public static final PropertyAttributeDefinition FACILITY = PropertyAttributeDefinition.Builder.of("facility", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(FacilityAttribute.USER_LEVEL.toString()))
            .setResolver(FacilityResolver.INSTANCE)
            .setValidator(EnumValidator.create(FacilityAttribute.class, EnumSet.allOf(FacilityAttribute.class)))
            .build();

    public static final PropertyAttributeDefinition HOSTNAME = PropertyAttributeDefinition.Builder.of("hostname", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    public static final PropertyAttributeDefinition PORT = PropertyAttributeDefinition.Builder.of("port", ModelType.INT, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode(514))
            .setValidator(new IntRangeValidator(0, 65535, true, true))
            .build();

    public static final PropertyAttributeDefinition SERVER_ADDRESS = PropertyAttributeDefinition.Builder.of("server-address", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(ElementAttributeMarshaller.VALUE_ATTRIBUTE_MARSHALLER)
            .setDefaultValue(new ModelNode("localhost"))
            .setPropertyName("serverHostname")
            .setValidator(Validators.NOT_EMPTY_NULLABLE_STRING_VALIDATOR)
            .build();

    public static final PropertyAttributeDefinition SYSLOG_FORMATTER = PropertyAttributeDefinition.Builder.of("syslog-format", ModelType.STRING, true)
            .setAllowExpression(true)
            .setAttributeMarshaller(new DefaultAttributeMarshaller() {
                @Override
                public void marshallAsElement(final AttributeDefinition attribute, final ModelNode resourceModel, final boolean marshallDefault, final XMLStreamWriter writer) throws XMLStreamException {
                    if (isMarshallable(attribute, resourceModel, marshallDefault)) {
                        writer.writeStartElement(attribute.getXmlName());
                        final String content = resourceModel.get(attribute.getName()).asString();
                        writer.writeAttribute(Attribute.SYSLOG_TYPE.getLocalName(), content);
                        writer.writeEndElement();
                    }
                }
            })
            .setDefaultValue(new ModelNode(SyslogType.RFC5424.name()))
            .setPropertyName("syslogType")
            .setValidator(EnumValidator.create(SyslogType.class, EnumSet.allOf(SyslogType.class)))
            .build();

    // This is being redefined here to clear the "formatter" from the alternatives and redefine the how the attribute is
    // persisted to the configuration file.
    public static final SimpleAttributeDefinition NAMED_FORMATTER = SimpleAttributeDefinitionBuilder.create(AbstractHandlerDefinition.NAMED_FORMATTER)
            .setAlternatives()
            .setAttributeMarshaller(ElementAttributeMarshaller.NAME_ATTRIBUTE_MARSHALLER)
            .build();

    /*
     * Attributes
     */
    private static final AttributeDefinition[] ATTRIBUTES = {
            APP_NAME,
            ENABLED,
            FACILITY,
            HOSTNAME,
            LEVEL,
            PORT,
            SERVER_ADDRESS,
            SYSLOG_FORMATTER,
            NAMED_FORMATTER
    };

    private static final HandlerAddOperationStepHandler ADD_HANDLER = new HandlerAddOperationStepHandler(SyslogHandler.class, ATTRIBUTES);
    private static final LogHandlerWriteAttributeHandler WRITE_HANDLER = new LogHandlerWriteAttributeHandler(ATTRIBUTES);

    public static final SyslogHandlerResourceDefinition INSTANCE = new SyslogHandlerResourceDefinition();

    private SyslogHandlerResourceDefinition() {
        super(new Parameters(SYSLOG_HANDLER_PATH, LoggingExtension.getResourceDescriptionResolver(NAME))
                .setAddHandler(ADD_HANDLER)
                .setRemoveHandler(HandlerOperations.REMOVE_HANDLER)
                .setCapabilities(Capabilities.HANDLER_CAPABILITY));
    }

    @Override
    public void registerAttributes(final ManagementResourceRegistration resourceRegistration) {
        for (AttributeDefinition def : ATTRIBUTES) {
            resourceRegistration.registerReadWriteAttribute(def, null, WRITE_HANDLER);
        }
    }

    @Override
    public void registerTransformers(final KnownModelVersion modelVersion, final ResourceTransformationDescriptionBuilder rootResourceBuilder, final ResourceTransformationDescriptionBuilder loggingProfileBuilder) {
        switch (modelVersion) {
            case VERSION_7_0_0: {
                final ResourceTransformationDescriptionBuilder resourceBuilder = rootResourceBuilder.addChildResource(SYSLOG_HANDLER_PATH);
                final ResourceTransformationDescriptionBuilder loggingProfileResourceBuilder = loggingProfileBuilder.addChildResource(SYSLOG_HANDLER_PATH);
                resourceBuilder.getAttributeBuilder()
                        .setDiscard(DiscardAttributeChecker.UNDEFINED, NAMED_FORMATTER)
                        .addRejectCheck(RejectAttributeChecker.DEFINED, NAMED_FORMATTER)
                        .end();
                loggingProfileResourceBuilder.getAttributeBuilder()
                        .setDiscard(DiscardAttributeChecker.UNDEFINED, NAMED_FORMATTER)
                        .addRejectCheck(RejectAttributeChecker.DEFINED, NAMED_FORMATTER)
                        .end();
                break;
            }
        }
    }

    public enum FacilityAttribute {
        KERNEL("kernel"),
        USER_LEVEL("user-level"),
        MAIL_SYSTEM("mail-system"),
        SYSTEM_DAEMONS("system-daemons"),
        SECURITY("security"),
        SYSLOGD("syslogd"),
        LINE_PRINTER("line-printer"),
        NETWORK_NEWS("network-news"),
        UUCP("uucp"),
        CLOCK_DAEMON("clock-daemon"),
        SECURITY2("security2"),
        FTP_DAEMON("ftp-daemon"),
        NTP("ntp"),
        LOG_AUDIT("log-audit"),
        LOG_ALERT("log-alert"),
        CLOCK_DAEMON2("clock-daemon2"),
        LOCAL_USE_0("local-use-0"),
        LOCAL_USE_1("local-use-1"),
        LOCAL_USE_2("local-use-2"),
        LOCAL_USE_3("local-use-3"),
        LOCAL_USE_4("local-use-4"),
        LOCAL_USE_5("local-use-5"),
        LOCAL_USE_6("local-use-6"),
        LOCAL_USE_7("local-use-7");

        private static final Map<String, FacilityAttribute> MAP;

        static {
            MAP = new HashMap<>();
            for (FacilityAttribute facilityAttribute : values()) {
                MAP.put(facilityAttribute.toString(), facilityAttribute);
            }
        }

        private final Facility facility;
        private final String value;

        FacilityAttribute(final String value) {
            this.value = value;
            this.facility = Facility.valueOf(value.replace("-", "_").toUpperCase(Locale.ENGLISH));
        }

        public Facility getFacility() {
            return facility;
        }

        public String getValue() {
            return value;
        }

        @Override
        public String toString() {
            return value;
        }

        public static FacilityAttribute fromString(final String value) {
            return MAP.get(value);
        }
    }

    static class FacilityResolver implements ModelNodeResolver<String> {
        static final FacilityResolver INSTANCE = new FacilityResolver();

        @Override
        public String resolveValue(final OperationContext context, final ModelNode value) {
            return FacilityAttribute.fromString(value.asString()).getFacility().name();
        }
    }
}
