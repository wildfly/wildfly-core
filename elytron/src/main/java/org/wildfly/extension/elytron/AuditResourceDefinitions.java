/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2017 Red Hat, Inc., and individual contributors
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
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_EVENT_LISTENER_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SSL_CONTEXT_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERIODIC_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SIZE_ROTATING_FILE_AUDIT_LOG;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SYSLOG_AUDIT_LOG;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver;
import static org.wildfly.extension.elytron._private.ElytronSubsystemMessages.ROOT_LOGGER;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.net.ssl.SSLContext;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.ListAttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.operations.validation.IntRangeValidator;
import org.jboss.as.controller.operations.validation.ModelTypeValidator;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.logmanager.handlers.SyslogHandler;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver;
import org.wildfly.extension.elytron.TrivialService.ValueSupplier;
import org.wildfly.extension.elytron._private.ElytronSubsystemMessages;
import org.wildfly.extension.elytron.capabilities._private.SecurityEventListener;
import org.wildfly.security.audit.AuditEndpoint;
import org.wildfly.security.audit.AuditLogger;
import org.wildfly.security.audit.EventPriority;
import org.wildfly.security.audit.FileAuditEndpoint;
import org.wildfly.security.audit.JsonSecurityEventFormatter;
import org.wildfly.security.audit.PeriodicRotatingFileAuditEndpoint;
import org.wildfly.security.audit.SimpleSecurityEventFormatter;
import org.wildfly.security.audit.SizeRotatingFileAuditEndpoint;
import org.wildfly.security.audit.SyslogAuditEndpoint;
import org.wildfly.security.auth.server.event.SecurityEventVisitor;

/**
 * Resources definitions for the audit logging resources.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class AuditResourceDefinitions {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(FileAttributeDefinitions.PATH)
            .setRequired(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition AUTOFLUSH = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.AUTOFLUSH, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SYNCHRONIZED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYNCHRONIZED, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.TRUE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition FORMAT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FORMAT, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(Format.SIMPLE.toString()))
            .setAllowedValues(Format.SIMPLE.toString(), Format.JSON.toString())
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ENCODING = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ENCODING, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .setValidator(new EncodingNameValidator())
            .build();

    static final SimpleAttributeDefinition SERVER_ADDRESS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SERVER_ADDRESS, ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PORT, ModelType.INT, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition TRANSPORT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.TRANSPORT, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(Transport.TCP.toString()))
            .setAllowedValues(Transport.TCP.toString(), Transport.UDP.toString(), Transport.SSL_TCP.toString())
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition HOST_NAME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.HOST_NAME, ModelType.STRING, false)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition MAX_BACKUP_INDEX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MAX_BACKUP_INDEX, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(1))
            .setValidator(new IntRangeValidator(1, true))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ROTATE_ON_BOOT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROTATE_ON_BOOT, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition ROTATE_SIZE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.ROTATE_SIZE, ModelType.STRING, true)
            .setAllowExpression(true)
            .setValidator(new SizeValidator())
            .setDefaultValue(new ModelNode("10m"))
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SIZE_SUFFIX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SUFFIX, ModelType.STRING, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition PERIODIC_SUFFIX = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SUFFIX, ModelType.STRING)
            .setAllowExpression(true)
            .setRequired(true)
            .setValidator(new SuffixValidator())
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SSL_CONTEXT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SSL_CONTEXT, ModelType.STRING, true)
            .setAllowExpression(false)
            .setRestartAllServices()
            .setCapabilityReference(SSL_CONTEXT_CAPABILITY, SECURITY_EVENT_LISTENER_CAPABILITY)
            .build();

    static final SimpleAttributeDefinition SYSLOG_FORMAT = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SYSLOG_FORMAT, ModelType.STRING, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode("RFC5424"))
            .setAllowedValues(SyslogHandler.SyslogType.RFC3164.toString(), SyslogHandler.SyslogType.RFC5424.toString())
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition RECONNECT_ATTEMPTS = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.RECONNECT_ATTEMPTS, ModelType.INT, true)
            .setAllowExpression(true)
            .setDefaultValue(new ModelNode(0))
            .setValidator(new IntRangeValidator(-1))
            .setRestartAllServices()
            .build();

    private static final AggregateComponentDefinition<SecurityEventListener> AGGREGATE_SECURITY_EVENT_LISTENER = AggregateComponentDefinition.create(SecurityEventListener.class,
            ElytronDescriptionConstants.AGGREGATE_SECURITY_EVENT_LISTENER, ElytronDescriptionConstants.SECURITY_EVENT_LISTENERS, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY, SecurityEventListener::aggregate, false);

    static final ListAttributeDefinition REFERENCES = AGGREGATE_SECURITY_EVENT_LISTENER.getReferencesAttribute();

    static AggregateComponentDefinition<SecurityEventListener> getAggregateSecurityEventListenerDefinition() {
        return AGGREGATE_SECURITY_EVENT_LISTENER;
    }

    static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    private abstract static class EndpointClosingEventListenerSupplier implements ValueSupplier<SecurityEventListener> {
        AuditEndpoint endpoint;

        @Override
        public void dispose() {
            if (endpoint == null) return;
            try {
                endpoint.close();
            } catch (IOException e) {
                ROOT_LOGGER.trace("Unable to close audit endpoint", e);
            }
        }
    }

    static ResourceDefinition getFileAuditLogResourceDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { PATH, RELATIVE_TO, AUTOFLUSH, SYNCHRONIZED, FORMAT, ENCODING };
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityEventListener>(SecurityEventListener.class, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityEventListener> getValueSupplier(
                    ServiceBuilder<SecurityEventListener> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                final boolean synv = SYNCHRONIZED.resolveModelAttribute(context, model).asBoolean();
                final boolean autoflush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean(synv);
                final Format format = Format.valueOf(FORMAT.resolveModelAttribute(context, model).asString());

                final InjectedValue<PathManager> pathManager = new InjectedValue<PathManager>();

                final String path = PATH.resolveModelAttribute(context, model).asString();
                final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                final String encoding = ENCODING.resolveModelAttribute(context, model).asStringOrNull();

                if (relativeTo != null) {
                    serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManager);
                    serviceBuilder.requires(pathName(relativeTo));
                }

                return new EndpointClosingEventListenerSupplier() {
                    @Override
                    public SecurityEventListener get() throws StartException {
                        PathResolver pathResolver = pathResolver();
                        pathResolver.path(path);
                        if (relativeTo != null) {
                            pathResolver.relativeTo(relativeTo, pathManager.getValue());
                        }
                        File resolvedPath = pathResolver.resolve();

                        final Supplier<DateTimeFormatter> dateTimeFormatterSupplier = () -> DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());
                        final SecurityEventVisitor<?, String> formatter = Format.JSON == format ? JsonSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build() : SimpleSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build();
                        try {
                            endpoint = FileAuditEndpoint.builder().setLocation(resolvedPath.toPath())
                                    .setSyncOnAccept(synv)
                                    .setFlushOnAccept(autoflush)
                                    .setCharset(encoding != null ? Charset.forName(encoding) : null)
                                    .setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build();
                        } catch (IOException e) {
                            throw ROOT_LOGGER.unableToStartService(e);
                        }

                        return SecurityEventListener.from(AuditLogger.builder()
                                .setPriorityMapper(m -> EventPriority.WARNING)
                                .setMessageFormatter(m -> m.accept(formatter, null))
                                .setAuditEndpoint(endpoint)
                                .build());
                    }
                };
            }
        };

        return new TrivialResourceDefinition(FILE_AUDIT_LOG, add, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getPeriodicRotatingFileAuditLogResourceDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] {PATH, RELATIVE_TO, AUTOFLUSH, SYNCHRONIZED, FORMAT, ENCODING, PERIODIC_SUFFIX };
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityEventListener>(SecurityEventListener.class, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityEventListener> getValueSupplier(
                    ServiceBuilder<SecurityEventListener> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                final boolean synv = SYNCHRONIZED.resolveModelAttribute(context, model).asBoolean();
                final boolean autoflush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean(synv);
                final Format format = Format.valueOf(FORMAT.resolveModelAttribute(context, model).asString());
                final String suffix = PERIODIC_SUFFIX.resolveModelAttribute(context, model).asString();

                final InjectedValue<PathManager> pathManager = new InjectedValue<>();

                final String path = PATH.resolveModelAttribute(context, model).asString();
                final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                final String encoding = ENCODING.resolveModelAttribute(context, model).asStringOrNull();

                if (relativeTo != null) {
                    serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManager);
                    serviceBuilder.requires(pathName(relativeTo));
                }

                return new EndpointClosingEventListenerSupplier() {
                    @Override
                    public SecurityEventListener get() throws StartException {
                        PathResolver pathResolver = pathResolver();
                        pathResolver.path(path);
                        if (relativeTo != null) {
                            pathResolver.relativeTo(relativeTo, pathManager.getValue());
                        }
                        File resolvedPath = pathResolver.resolve();

                        final Supplier<DateTimeFormatter> dateTimeFormatterSupplier = () -> DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());
                        final SecurityEventVisitor<?, String> formatter = Format.JSON == format ? JsonSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build() : SimpleSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build();
                        try {
                            FileAuditEndpoint.Builder builder = PeriodicRotatingFileAuditEndpoint.builder()
                                    .setSuffix(suffix)
                                    .setLocation(resolvedPath.toPath())
                                    .setSyncOnAccept(synv)
                                    .setFlushOnAccept(autoflush)
                                    .setCharset(encoding != null ? Charset.forName(encoding) : null)
                                    .setDateTimeFormatterSupplier(dateTimeFormatterSupplier);

                            endpoint = builder.build();
                        } catch (IOException e) {
                            throw ROOT_LOGGER.unableToStartService(e);
                        }

                        return SecurityEventListener.from(AuditLogger.builder()
                                .setPriorityMapper(m -> EventPriority.WARNING)
                                .setMessageFormatter(m -> m.accept(formatter, null))
                                .setAuditEndpoint(endpoint)
                                .build());
                    }
                };
            }
        };

        return new TrivialResourceDefinition(PERIODIC_ROTATING_FILE_AUDIT_LOG, add, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getSizeRotatingFileAuditLogResourceDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { PATH, RELATIVE_TO, AUTOFLUSH, SYNCHRONIZED, FORMAT, ENCODING, MAX_BACKUP_INDEX, ROTATE_ON_BOOT, ROTATE_SIZE, SIZE_SUFFIX };
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityEventListener>(SecurityEventListener.class, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityEventListener> getValueSupplier(
                    ServiceBuilder<SecurityEventListener> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                final boolean synv = SYNCHRONIZED.resolveModelAttribute(context, model).asBoolean();
                final boolean autoflush = AUTOFLUSH.resolveModelAttribute(context, model).asBoolean(synv);
                final Format format = Format.valueOf(FORMAT.resolveModelAttribute(context, model).asString());
                final int maxBackupIndex = MAX_BACKUP_INDEX.resolveModelAttribute(context, model).asInt(0);
                final boolean rotateOnBoot = ROTATE_ON_BOOT.resolveModelAttribute(context, model).asBoolean();
                final long rotateSize = SizeValidator.parseSize(ROTATE_SIZE.resolveModelAttribute(context, model));
                final ModelNode suffix = SIZE_SUFFIX.resolveModelAttribute(context, model);

                final InjectedValue<PathManager> pathManager = new InjectedValue<>();

                final String path = PATH.resolveModelAttribute(context, model).asString();
                final String relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();
                final String encoding = ENCODING.resolveModelAttribute(context, model).asStringOrNull();

                if (relativeTo != null) {
                    serviceBuilder.addDependency(PathManagerService.SERVICE_NAME, PathManager.class, pathManager);
                    serviceBuilder.requires(pathName(relativeTo));
                }

                return new EndpointClosingEventListenerSupplier() {
                    @Override
                    public SecurityEventListener get() throws StartException {
                        PathResolver pathResolver = pathResolver();
                        pathResolver.path(path);
                        if (relativeTo != null) {
                            pathResolver.relativeTo(relativeTo, pathManager.getValue());
                        }
                        File resolvedPath = pathResolver.resolve();

                        final Supplier<DateTimeFormatter> dateTimeFormatterSupplier = () -> DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());
                        final SecurityEventVisitor<?, String> formatter = Format.JSON == format ? JsonSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build() : SimpleSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build();
                        try {
                            SizeRotatingFileAuditEndpoint.Builder builder = SizeRotatingFileAuditEndpoint.builder()
                                    .setMaxBackupIndex(maxBackupIndex)
                                    .setRotateOnBoot(rotateOnBoot)
                                    .setRotateSize(rotateSize);
                            if (suffix.isDefined()) {
                                builder.setSuffix(suffix.asString());
                            }
                            builder.setLocation(resolvedPath.toPath())
                                    .setSyncOnAccept(synv)
                                    .setFlushOnAccept(autoflush)
                                    .setCharset(encoding != null ? Charset.forName(encoding) : null)
                                    .setDateTimeFormatterSupplier(dateTimeFormatterSupplier);

                            endpoint = builder.build();
                        } catch (IOException e) {
                            throw ROOT_LOGGER.unableToStartService(e);
                        }

                        return SecurityEventListener.from(AuditLogger.builder()
                                .setPriorityMapper(m -> EventPriority.WARNING)
                                .setMessageFormatter(m -> m.accept(formatter, null))
                                .setAuditEndpoint(endpoint)
                                .build());
                    }
                };
            }
        };

        return new TrivialResourceDefinition(SIZE_ROTATING_FILE_AUDIT_LOG, add, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY);
    }

    static ResourceDefinition getSyslogAuditLogResourceDefinition() {
        AttributeDefinition[] attributes = new AttributeDefinition[] { SERVER_ADDRESS, PORT, TRANSPORT, HOST_NAME, FORMAT, SSL_CONTEXT, SYSLOG_FORMAT, RECONNECT_ATTEMPTS };
        AbstractAddStepHandler add = new TrivialAddHandler<SecurityEventListener>(SecurityEventListener.class, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY) {

            @Override
            protected ValueSupplier<SecurityEventListener> getValueSupplier(
                    ServiceBuilder<SecurityEventListener> serviceBuilder, OperationContext context, ModelNode model)
                    throws OperationFailedException {

                String address = SERVER_ADDRESS.resolveModelAttribute(context, model).asString();
                final InetAddress serverAddress;
                try {
                    serverAddress = InetAddress.getByName(address);
                } catch (UnknownHostException e) {
                    throw ROOT_LOGGER.serverNotKnown(address, e);
                }
                final int port = PORT.resolveModelAttribute(context, model).asInt();
                final Transport transport = Transport.valueOf(TRANSPORT.resolveModelAttribute(context, model).asString());
                final String hostName = HOST_NAME.resolveModelAttribute(context, model).asString();
                final Format format = Format.valueOf(FORMAT.resolveModelAttribute(context, model).asString());
                final SyslogHandler.SyslogType syslogFormat = SyslogHandler.SyslogType.valueOf(SYSLOG_FORMAT.resolveModelAttribute(context, model).asString());
                final int reconnectAttempts = RECONNECT_ATTEMPTS.resolveModelAttribute(context, model).asInt();

                final InjectedValue<SSLContext> sslContextInjector = new InjectedValue<>();
                String sslContextName = SSL_CONTEXT.resolveModelAttribute(context, model).asStringOrNull();
                if (sslContextName != null) {
                    String sslCapability = RuntimeCapability.buildDynamicCapabilityName(SSL_CONTEXT_CAPABILITY, sslContextName);
                    ServiceName sslServiceName = context.getCapabilityServiceName(sslCapability, SSLContext.class);
                    serviceBuilder.addDependency(sslServiceName, SSLContext.class, sslContextInjector);
                }

                return new EndpointClosingEventListenerSupplier() {
                    @Override
                    public SecurityEventListener get() throws StartException {
                        final Supplier<DateTimeFormatter> dateTimeFormatterSupplier = () -> DateTimeFormatter.ofPattern(DATE_FORMAT).withZone(ZoneId.systemDefault());
                        final SecurityEventVisitor<?, String> formatter = Format.JSON == format ? JsonSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build() : SimpleSecurityEventFormatter.builder().setDateTimeFormatterSupplier(dateTimeFormatterSupplier).build();
                        final SSLContext sslContext = sslContextInjector.getOptionalValue();
                        try {
                            endpoint = SyslogAuditEndpoint.builder()
                                    .setServerAddress(serverAddress)
                                    .setPort(port)
                                    .setSsl(transport == Transport.SSL_TCP)
                                    .setTcp(transport == Transport.TCP || transport == Transport.SSL_TCP)
                                    .setHostName(hostName)
                                    .setSocketFactory(transport == Transport.SSL_TCP && sslContext != null ? sslContext.getSocketFactory() : null)
                                    .setFormat(syslogFormat)
                                    .setMaxReconnectAttempts(reconnectAttempts)
                                    .build();
                        } catch (IOException e) {
                            throw ROOT_LOGGER.unableToStartService(e);
                        }
                        return SecurityEventListener.from(AuditLogger.builder()
                                .setPriorityMapper(m -> EventPriority.WARNING)
                                .setMessageFormatter(m -> m.accept(formatter, null))
                                .setAuditEndpoint(endpoint)
                                .build());
                    }
                };
            }
        };

        return new TrivialResourceDefinition(SYSLOG_AUDIT_LOG, add, attributes, SECURITY_EVENT_LISTENER_RUNTIME_CAPABILITY);
    }

    private enum Format {
        SIMPLE, JSON
    }

    private enum Transport {
        TCP, UDP, SSL_TCP
    }

    static class SizeValidator extends ModelTypeValidator {
        private static final Pattern SIZE_PATTERN = Pattern.compile("(\\d+)([kKmMgGbBtT])?");

        public SizeValidator() {
            this(false);
        }

        public SizeValidator(final boolean nullable) {
            super(ModelType.STRING, nullable);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                parseSize(value);
            }
        }

        public static long parseSize(final ModelNode value) throws OperationFailedException {
            final Matcher matcher = SIZE_PATTERN.matcher(value.asString());
            if (!matcher.matches()) {
                throw ElytronSubsystemMessages.ROOT_LOGGER.invalidSize(value.asString());
            }
            long qty = Long.parseLong(matcher.group(1), 10);
            final String chr = matcher.group(2);
            if (chr != null) {
                switch (chr.charAt(0)) {
                    case 'b':
                    case 'B':
                        break;
                    case 'k':
                    case 'K':
                        qty <<= 10L;
                        break;
                    case 'm':
                    case 'M':
                        qty <<= 20L;
                        break;
                    case 'g':
                    case 'G':
                        qty <<= 30L;
                        break;
                    case 't':
                    case 'T':
                        qty <<= 40L;
                        break;
                    default:
                        throw ElytronSubsystemMessages.ROOT_LOGGER.invalidSize(value.asString());
                }
            }
            return qty;
        }
    }

    static class SuffixValidator extends ModelTypeValidator {
        private final boolean denySeconds;

        public SuffixValidator() {
            this(false, true);
        }

        public SuffixValidator(final boolean nullable, final boolean denySeconds) {
            super(ModelType.STRING, nullable);
            this.denySeconds = denySeconds;
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                final String suffix = value.asString();
                try {
                    DateTimeFormatter.ofPattern(suffix);
                    if (denySeconds) {
                        for (int i = 0; i < suffix.length(); i++) {
                            char c = suffix.charAt(i);
                            if (c == '\'') {
                                c = suffix.charAt(++i);
                                while (c != '\'') {
                                    c = suffix.charAt(++i);
                                }
                            }
                            if (c == 's' || c == 'S') {
                                throw ElytronSubsystemMessages.ROOT_LOGGER.suffixContainsMillis(suffix);
                            }
                        }
                    }
                } catch (IllegalArgumentException e) {
                    throw ElytronSubsystemMessages.ROOT_LOGGER.invalidSuffix(suffix);
                }
            }
        }
    }

    static class EncodingNameValidator extends ModelTypeValidator {

        EncodingNameValidator() {
            super(ModelType.STRING, true, true, false);
        }

        @Override
        public void validateParameter(String parameterName, ModelNode value) throws OperationFailedException {
            super.validateParameter(parameterName, value);
            if (value.isDefined()) {
                try {
                    Charset.forName(value.asString());
                } catch (IllegalArgumentException e) {
                    throw ROOT_LOGGER.invalidEncodingName(value.asString());
                }
            }
        }
    }
}

