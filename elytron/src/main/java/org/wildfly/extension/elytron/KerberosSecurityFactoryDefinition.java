/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_API_CAPABILITY;
import static org.wildfly.extension.elytron.Capabilities.SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.OPTION;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.RELATIVE_TO;
import static org.wildfly.extension.elytron.FileAttributeDefinitions.pathName;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.ietf.jgss.GSSCredential;
import org.ietf.jgss.GSSException;
import org.ietf.jgss.Oid;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.CapabilityServiceBuilder;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PropertiesAttributeDefinition;
import org.jboss.as.controller.ResourceDefinition;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.StringListAttributeDefinition;
import org.jboss.as.controller.operations.validation.StringAllowedValuesValidator;
import org.jboss.as.controller.services.path.PathManagerService;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.StartException;
import org.jboss.msc.value.InjectedValue;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.extension.elytron.capabilities.CredentialSecurityFactory;
import org.wildfly.security.asn1.OidsUtil;
import org.wildfly.security.mechanism.gssapi.GSSCredentialSecurityFactory;

/**
 * Factory class for the Kerberos security factory resource.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class KerberosSecurityFactoryDefinition {

    static final SimpleAttributeDefinition PATH = new SimpleAttributeDefinitionBuilder(FileAttributeDefinitions.PATH)
        .setRequired(true)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition PRINCIPAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.PRINCIPAL, ModelType.STRING, false)
        .setAllowExpression(true)
        .setMinSize(1)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition MINIMUM_REMAINING_LIFETIME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.MINIMUM_REMAINING_LIFETIME, ModelType.INT, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.ZERO)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition REQUEST_LIFETIME = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REQUEST_LIFETIME, ModelType.INT, true)
        .setAllowExpression(true)
        .setDefaultValue(new ModelNode(GSSCredential.INDEFINITE_LIFETIME))
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition FAIL_CACHE = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.FAIL_CACHE, ModelType.INT, true)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition SERVER = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.SERVER, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.TRUE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition OBTAIN_KERBEROS_TICKET = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.OBTAIN_KERBEROS_TICKET, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.FALSE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition DEBUG = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.DEBUG, ModelType.BOOLEAN, true)
        .setAllowExpression(true)
        .setDefaultValue(ModelNode.FALSE)
        .setRestartAllServices()
        .build();

    static final SimpleAttributeDefinition WRAP_GSS_CREDENTIAL = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.WRAP_GSS_CREDENTIAL, ModelType.BOOLEAN, true)
            .setAllowExpression(true)
            .setDefaultValue(ModelNode.FALSE)
            .setRestartAllServices()
            .build();

    static final SimpleAttributeDefinition REQUIRED = new SimpleAttributeDefinitionBuilder(ElytronDescriptionConstants.REQUIRED, ModelType.BOOLEAN, true)
            .setDefaultValue(ModelNode.FALSE)
            .setAllowExpression(true)
            .setRestartAllServices()
            .build();

    private static final ModelNode mechanismsDefault = new ModelNode();
    private static final String[] mechanismAllowedValues = new String[]{"KRB5LEGACY","GENERIC","KRB5","KRB5V2","SPNEGO"};
    static {
        mechanismsDefault.add("KRB5");
        mechanismsDefault.add("SPNEGO");
    }
    static final StringListAttributeDefinition MECHANISM_NAMES = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_NAMES)
        .setAllowExpression(true)
        .setRequired(false)
        .setDefaultValue(mechanismsDefault)
        .setAllowedValues("KRB5LEGACY","GENERIC","KRB5","KRB5V2","SPNEGO") // defined in oids.properties in wildfly-elytron
        .setMinSize(1)
        .setMaxSize(mechanismAllowedValues.length)
        .setValidator(new StringAllowedValuesValidator(mechanismAllowedValues))
        .setRestartAllServices()
        .build();

    static final StringListAttributeDefinition MECHANISM_OIDS = new StringListAttributeDefinition.Builder(ElytronDescriptionConstants.MECHANISM_OIDS)
        .setAllowExpression(true)
        .setRequired(false)
        .setRestartAllServices()
        .build();

    static final PropertiesAttributeDefinition OPTIONS = new PropertiesAttributeDefinition.Builder(ElytronDescriptionConstants.OPTIONS, true)
            .setAttributeMarshaller(new AttributeMarshallers.PropertiesAttributeMarshaller(null, OPTION, false))
            .setAttributeParser(new AttributeParsers.PropertiesParser(null, OPTION, false))
            .setRestartAllServices()
            .build();

    static ResourceDefinition getKerberosSecurityFactoryDefinition() {
        final AttributeDefinition[] attributes = new AttributeDefinition[] { PRINCIPAL, RELATIVE_TO, PATH,  MINIMUM_REMAINING_LIFETIME, REQUEST_LIFETIME, FAIL_CACHE, SERVER, OBTAIN_KERBEROS_TICKET, DEBUG, MECHANISM_NAMES, MECHANISM_OIDS, WRAP_GSS_CREDENTIAL, REQUIRED, OPTIONS };

        DoohickeyAddHandler<CredentialSecurityFactory> add = new DoohickeyAddHandler<CredentialSecurityFactory>(
                SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY, SECURITY_FACTORY_CREDENTIAL_API_CAPABILITY) {

            @Override
            protected ElytronDoohickey<CredentialSecurityFactory> createDoohickey(PathAddress resourceAddress) {
                return new ElytronDoohickey<CredentialSecurityFactory>(resourceAddress) {

                    private volatile String principal;
                    private volatile int minimumRemainingLifetime;
                    private volatile int requestLifetime;
                    private volatile int failCache;
                    private volatile boolean server;
                    private volatile boolean obtainKerberosTicket;
                    private volatile boolean debug;
                    private volatile boolean wrapGssCredential;
                    private volatile boolean required;
                    private volatile Set<Oid> mechanismOids;
                    private volatile String path;
                    private volatile String relativeTo;
                    private volatile Map<String, Object> options;

                    @Override
                    protected void resolveRuntime(ModelNode model, OperationContext context) throws OperationFailedException {
                        principal = PRINCIPAL.resolveModelAttribute(context, model).asString();
                        minimumRemainingLifetime = MINIMUM_REMAINING_LIFETIME.resolveModelAttribute(context, model).asInt();
                        requestLifetime = REQUEST_LIFETIME.resolveModelAttribute(context, model).asInt();
                        failCache = FAIL_CACHE.resolveModelAttribute(context, model).asInt(0);
                        server = SERVER.resolveModelAttribute(context, model).asBoolean();
                        obtainKerberosTicket = OBTAIN_KERBEROS_TICKET.resolveModelAttribute(context, model).asBoolean();
                        debug = DEBUG.resolveModelAttribute(context, model).asBoolean();
                        wrapGssCredential = WRAP_GSS_CREDENTIAL.resolveModelAttribute(context, model).asBoolean();
                        required = REQUIRED.resolveModelAttribute(context, model).asBoolean();

                        Stream<String> oidsFromNames = MECHANISM_NAMES.unwrap(context, model).stream()
                                .map(name -> OidsUtil.attributeNameToOid(OidsUtil.Category.GSS, name));
                        Stream<String> directOids = MECHANISM_OIDS.unwrap(context, model).stream();
                        mechanismOids = Stream.concat(oidsFromNames, directOids).map(s -> {
                            try {
                                return new Oid(s);
                            } catch (GSSException e) {
                                throw new IllegalArgumentException(e);
                            }
                        }).collect(Collectors.toSet());

                        path = PATH.resolveModelAttribute(context, model).asString();
                        relativeTo = RELATIVE_TO.resolveModelAttribute(context, model).asStringOrNull();

                        ModelNode optionsNode = OPTIONS.resolveModelAttribute(context, model);
                        if (optionsNode.isDefined()) {
                            options = new HashMap<>();
                            for (Property option : optionsNode.asPropertyList()) {
                                options.put(option.getName(), option.getValue().asString());
                            }
                        } else {
                            options = null;
                        }
                    }

                    @Override
                    protected ExceptionSupplier<CredentialSecurityFactory, StartException> prepareServiceSupplier(
                            OperationContext context, CapabilityServiceBuilder<?> serviceBuilder) throws OperationFailedException {
                        final InjectedValue<org.jboss.as.controller.services.path.PathManager> pathManagerInjector = new InjectedValue<>();
                        if (relativeTo != null) {
                            serviceBuilder.addDependency(PathManagerService.SERVICE_NAME,
                                    org.jboss.as.controller.services.path.PathManager.class, pathManagerInjector);
                            serviceBuilder.requires(pathName(relativeTo));
                        }
                        return () -> buildFactory(pathManagerInjector.getOptionalValue());
                    }

                    @Override
                    protected CredentialSecurityFactory createImmediately(OperationContext foreignContext) throws OperationFailedException {
                        // Resolve the keytab path via PathManager already running in the service registry.
                        File resolvedPath = resolveRelativeToImmediately(path, relativeTo, foreignContext);
                        try {
                            CredentialSecurityFactory factory = buildFactory(resolvedPath);
                            setValue(factory);
                            return factory;
                        } catch (StartException e) {
                            throw new OperationFailedException(e);
                        }
                    }

                    private CredentialSecurityFactory buildFactory(org.jboss.as.controller.services.path.PathManager pathManager) throws StartException {
                        File resolvedPath = resolvePathWithManager(path, relativeTo, pathManager);
                        return buildFactory(resolvedPath);
                    }

                    private CredentialSecurityFactory buildFactory(File resolvedPath) throws StartException {
                        GSSCredentialSecurityFactory.Builder builder = GSSCredentialSecurityFactory.builder()
                                .setPrincipal(principal)
                                .setKeyTab(resolvedPath)
                                .setMinimumRemainingLifetime(minimumRemainingLifetime)
                                .setRequestLifetime(requestLifetime)
                                .setFailCache(failCache)
                                .setIsServer(server)
                                .setObtainKerberosTicket(obtainKerberosTicket)
                                .setDebug(debug)
                                .setWrapGssCredential(wrapGssCredential)
                                .setCheckKeyTab(required)
                                .setOptions(options);
                        for (Oid mechanismOid : mechanismOids) {
                            builder.addMechanismOid(mechanismOid);
                        }
                        try {
                            return CredentialSecurityFactory.from(builder.build());
                        } catch (IOException e) {
                            throw new StartException(e);
                        }
                    }

                    private File resolvePathWithManager(String path, String relativeTo,
                            org.jboss.as.controller.services.path.PathManager pathManager) {
                        org.wildfly.extension.elytron.FileAttributeDefinitions.PathResolver pathResolver =
                                org.wildfly.extension.elytron.FileAttributeDefinitions.pathResolver();
                        pathResolver.path(path);
                        if (relativeTo != null && pathManager != null) {
                            pathResolver.relativeTo(relativeTo, pathManager);
                        }
                        File resolved = pathResolver.resolve();
                        pathResolver.clear();
                        return resolved;
                    }
                };
            }
        };

        return new TrivialResourceDefinition(ElytronDescriptionConstants.KERBEROS_SECURITY_FACTORY, add, attributes, SECURITY_FACTORY_CREDENTIAL_RUNTIME_CAPABILITY);
    }

}
