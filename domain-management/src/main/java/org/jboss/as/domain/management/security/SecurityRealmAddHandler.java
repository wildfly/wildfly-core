/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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
package org.jboss.as.domain.management.security;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADVANCED_FILTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHENTICATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_SEARCH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.GROUP_TO_PRINCIPAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LOCAL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PATH;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PRINCIPAL_TO_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROPERTIES;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SECRET;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_IDENTITY;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SSL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TRUSTSTORE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_FILTER;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_IS_DN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERNAME_TO_DN;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.USERS;
import static org.jboss.as.controller.security.CredentialReference.KEY_DELIMITER;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_ACCESS_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.BY_SEARCH_TIME;
import static org.jboss.as.domain.management.ModelDescriptionConstants.CACHE;
import static org.jboss.as.domain.management.ModelDescriptionConstants.JAAS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.JKS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KERBEROS;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KEYSTORE_PATH;
import static org.jboss.as.domain.management.ModelDescriptionConstants.KEYTAB;
import static org.jboss.as.domain.management.ModelDescriptionConstants.PASSWORD;
import static org.jboss.as.domain.management.ModelDescriptionConstants.PLUG_IN;
import static org.jboss.as.domain.management.ModelDescriptionConstants.PROPERTY;
import static org.jboss.msc.service.ServiceController.Mode.ON_DEMAND;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;

import org.jboss.as.controller.AbstractAddStepHandler;

import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.controller.services.path.PathManager;
import org.jboss.as.core.security.ServerSecurityManager;
import org.jboss.as.domain.management.AuthMechanism;
import org.jboss.as.domain.management.CallbackHandlerFactory;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionManager;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionManagerService;
import org.jboss.as.domain.management.security.BaseLdapGroupSearchResource.GroupName;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;
import org.jboss.dmr.Property;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceController.Mode;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;

/**
 * Handler to add security realm definitions and register the service.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class SecurityRealmAddHandler extends AbstractAddStepHandler {

    private static final String ELYTRON_CAPABILITY = "org.wildfly.security.elytron";
    private static final String PATH_MANAGER_CAPABILITY = "org.wildfly.management.path-manager";

    public static final SecurityRealmAddHandler INSTANCE = new SecurityRealmAddHandler();

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }


    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        super.populateModel(operation, model);
        SecurityRealmResourceDefinition.MAP_GROUPS_TO_ROLES.validateAndSet(operation, model);
    }

    @Override
    protected void performRuntime(OperationContext context, ModelNode operation, Resource resource) throws OperationFailedException {
        // Install another RUNTIME handler to actually install the services. This will run after the
        // RUNTIME handler for any child resources. Doing this will ensure that child resource handlers don't
        // see the installed services and can just ignore doing any RUNTIME stage work
        if(!context.isBooting() && context.getProcessType() == ProcessType.EMBEDDED_SERVER && context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            context.reloadRequired();
        } else {
            context.addStep(ServiceInstallStepHandler.INSTANCE, OperationContext.Stage.RUNTIME);
        }
    }

    @Override
    protected void rollbackRuntime(OperationContext context, ModelNode operation, Resource resource) {
        if (!context.isBooting() && context.getProcessType() == ProcessType.EMBEDDED_SERVER && context.getRunningMode() == RunningMode.ADMIN_ONLY) {
            context.revertReloadRequired();
        }
    }

    @Override
    public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
        super.execute(context, operation);
// Add a step validating that we have the correct authentication and authorization child resources
        ModelNode validationOp = AuthenticationValidatingHandler.createOperation(operation);
        context.addStep(validationOp, AuthenticationValidatingHandler.INSTANCE, OperationContext.Stage.MODEL);
        validationOp = AuthorizationValidatingHandler.createOperation(operation);
        context.addStep(validationOp, AuthorizationValidatingHandler.INSTANCE, OperationContext.Stage.MODEL);
    }


    protected void installServices(final OperationContext context, final String realmName, final ModelNode model)
            throws OperationFailedException {
        final ModelNode plugIns = model.hasDefined(PLUG_IN) ? model.get(PLUG_IN) : null;
        final ModelNode authentication = model.hasDefined(AUTHENTICATION) ? model.get(AUTHENTICATION) : null;
        final ModelNode authorization = model.hasDefined(AUTHORIZATION) ? model.get(AUTHORIZATION) : null;
        final ModelNode serverIdentities = model.hasDefined(SERVER_IDENTITY) ? model.get(SERVER_IDENTITY) : null;

        final ServiceTarget serviceTarget = context.getServiceTarget();

        final boolean mapGroupsToRoles = SecurityRealmResourceDefinition.MAP_GROUPS_TO_ROLES.resolveModelAttribute(context, model).asBoolean();
        final ServiceName realmServiceName = SecurityRealm.ServiceUtil.createServiceName(realmName);
        final ServiceBuilder<?> realmBuilder = serviceTarget.addService(realmServiceName);
        final Set<Supplier<CallbackHandlerService>> callbackHandlerServices = new HashSet<>();
        final Consumer<SecurityRealm> securityRealmConsumer = realmBuilder.provides(realmServiceName, SecurityRealm.ServiceUtil.createLegacyServiceName(realmName));
        final ServiceName tmpDirPath = ServiceName.JBOSS.append("server", "path", "jboss.controller.temp.dir");

        final boolean shareLdapConnections = shareLdapConnection(context, authentication, authorization);
        ModelNode authTruststore = null;
        if (plugIns != null) {
            addPlugInLoaderService(realmName, plugIns, serviceTarget);
        }

        if (! context.getProcessType().isServer()) {
            final Supplier<CallbackHandlerService> chsSupplier = addDomainManagedServersService(context, realmBuilder);
            if (chsSupplier != null) {
                callbackHandlerServices.add(chsSupplier);
            }
        }

        if (authentication != null) {
            // Authentication can have a truststore defined at the same time as a username/password based mechanism.
            //
            // In this case it is expected certificate based authentication will first occur with a fallback to username/password
            // based authentication.

            if (authentication.hasDefined(TRUSTSTORE)) {
                authTruststore = authentication.require(TRUSTSTORE);
                callbackHandlerServices.add(addClientCertService(realmName, serviceTarget, realmBuilder));
            }
            if (authentication.hasDefined(LOCAL)) {
                callbackHandlerServices.add(addLocalService(context, authentication.require(LOCAL), realmName, serviceTarget, realmBuilder));
            }
            if (authentication.hasDefined(KERBEROS)) {
                callbackHandlerServices.add(addKerberosService(context, authentication.require(KERBEROS), realmName, serviceTarget, realmBuilder));
            }

            if (authentication.hasDefined(JAAS)) {
                callbackHandlerServices.add(addJaasService(context, authentication.require(JAAS), realmName, serviceTarget, context.isNormalServer(), realmBuilder));
            } else if (authentication.hasDefined(LDAP)) {
                callbackHandlerServices.add(addLdapService(context, authentication.require(LDAP), realmName, serviceTarget, realmBuilder, shareLdapConnections));
            } else if (authentication.hasDefined(PLUG_IN)) {
                callbackHandlerServices.add(addPlugInAuthenticationService(context, authentication.require(PLUG_IN), realmName, realmName, serviceTarget, realmBuilder));
            } else if (authentication.hasDefined(PROPERTIES)) {
                callbackHandlerServices.add(addPropertiesAuthenticationService(context, authentication.require(PROPERTIES), realmName, serviceTarget, realmBuilder));
            } else if (authentication.hasDefined(USERS)) {
                callbackHandlerServices.add(addUsersService(context, authentication.require(USERS), realmName, serviceTarget, realmBuilder));
            }
        }
        Supplier<SubjectSupplementalService> subjectSupplementalSupplier = null;
        if (authorization != null) {
            if (authorization.hasDefined(PROPERTIES)) {
                subjectSupplementalSupplier = addPropertiesAuthorizationService(context, authorization.require(PROPERTIES), realmName, serviceTarget, realmBuilder);
            } else if (authorization.hasDefined(PLUG_IN)) {
                subjectSupplementalSupplier = addPlugInAuthorizationService(context, authorization.require(PLUG_IN), realmName, serviceTarget, realmBuilder);
            } else if (authorization.hasDefined(LDAP)) {
                subjectSupplementalSupplier = addLdapAuthorizationService(context, authorization.require(LDAP), realmName, serviceTarget, realmBuilder, shareLdapConnections);
            }
        }

        ModelNode ssl = null;
        Supplier<CallbackHandlerFactory> secretCallbackFactorySupplier = null;
        Supplier<KeytabIdentityFactoryService> keytabFactorySupplier = null;
        if (serverIdentities != null) {
            if (serverIdentities.hasDefined(SSL)) {
                ssl = serverIdentities.require(SSL);
            }
            if (serverIdentities.hasDefined(SECRET)) {
                secretCallbackFactorySupplier = addSecretService(context, serverIdentities.require(SECRET), realmName,serviceTarget, realmBuilder);
            }
            if (serverIdentities.hasDefined(KERBEROS)) {
                keytabFactorySupplier = addKerberosIdentityServices(context, serverIdentities.require(KERBEROS), realmName, serviceTarget, realmBuilder);
            }
        }

        Supplier<SSLContext> sslContextSupplier = null;
        if (ssl != null || authTruststore != null) {
            sslContextSupplier = addSSLServices(context, ssl, authTruststore, realmName, serviceTarget, realmBuilder);
        }

        final Supplier<String> tmpDirPathSupplier = realmBuilder.requires(tmpDirPath);

        final SecurityRealmService securityRealmService = new SecurityRealmService(
                securityRealmConsumer, subjectSupplementalSupplier, secretCallbackFactorySupplier,
                keytabFactorySupplier, sslContextSupplier, tmpDirPathSupplier, callbackHandlerServices, realmName, mapGroupsToRoles);
        realmBuilder.setInstance(securityRealmService);
        realmBuilder.install();
    }

    private boolean shareLdapConnection(final OperationContext context, final ModelNode authentication,
            final ModelNode authorization) throws OperationFailedException {
        if (authentication == null || authorization == null || authentication.hasDefined(LDAP) == false
                || authorization.hasDefined(LDAP) == false) {
            return false;
        }

        String authConnectionManager = LdapAuthenticationResourceDefinition.CONNECTION.resolveModelAttribute(context,
                authentication.require(LDAP)).asString();
        String authzConnectionManager = LdapAuthorizationResourceDefinition.CONNECTION.resolveModelAttribute(context,
                authorization.require(LDAP)).asString();

        return authConnectionManager.equals(authzConnectionManager);
    }

    private void addPlugInLoaderService(String realmName, ModelNode plugInModel, ServiceTarget serviceTarget) {
        final ServiceName plugInLoaderName = PlugInLoaderService.ServiceUtil.createServiceName(realmName);
        final List<Property> plugIns = plugInModel.asPropertyList();
        final ArrayList<String> knownNames = new ArrayList<String>(plugIns.size());
        for (Property current : plugIns) {
            knownNames.add(current.getName());
        }
        final ServiceBuilder<?> builder = serviceTarget.addService(plugInLoaderName);
        final Consumer<PlugInLoaderService> pilsConsumer = builder.provides(plugInLoaderName);
        builder.setInstance(new PlugInLoaderService(pilsConsumer, Collections.unmodifiableList(knownNames)));
        builder.setInitialMode(Mode.ON_DEMAND);
        builder.install();
    }

    private Supplier<CallbackHandlerService> addClientCertService(String realmName, ServiceTarget serviceTarget, ServiceBuilder<?> realmBuilder) {
        final ServiceName clientCertServiceName = ClientCertCallbackHandler.ServiceUtil.createServiceName(realmName);
        final ServiceBuilder<?> builder = serviceTarget.addService(clientCertServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(clientCertServiceName);
        builder.setInstance(new ClientCertCallbackHandler(chsConsumer));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, clientCertServiceName);
    }

    private Supplier<CallbackHandlerService> addKerberosService(OperationContext context, ModelNode kerberos, String realmName, ServiceTarget serviceTarget,
            ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName kerberosServiceName = KerberosCallbackHandler.ServiceUtil.createServiceName(realmName);
        final boolean removeRealm = KerberosAuthenticationResourceDefinition.REMOVE_REALM.resolveModelAttribute(context, kerberos).asBoolean();
        final ServiceBuilder<?> builder = serviceTarget.addService(kerberosServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(kerberosServiceName);
        builder.setInstance(new KerberosCallbackHandler(chsConsumer, removeRealm));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, kerberosServiceName);
    }

    private Supplier<CallbackHandlerService> addJaasService(OperationContext context, ModelNode jaas, String realmName, ServiceTarget serviceTarget,
                                boolean injectServerManager, ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName jaasServiceName = JaasCallbackHandler.ServiceUtil.createServiceName(realmName);
        final String name = JaasAuthenticationResourceDefinition.NAME.resolveModelAttribute(context, jaas).asString();
        final boolean assignGroups = JaasAuthenticationResourceDefinition.ASSIGN_GROUPS.resolveModelAttribute(context, jaas).asBoolean();
        final ServiceBuilder<?> builder = serviceTarget.addService(jaasServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(jaasServiceName);
        final Supplier<ServerSecurityManager> smSupplier = injectServerManager ? builder.requires(ServiceName.JBOSS.append("security", "simple-security-manager")) : null;
        builder.setInstance(new JaasCallbackHandler(chsConsumer, smSupplier, realmName, name, assignGroups));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, jaasServiceName);
    }

    private <R, K> LdapCacheService<R, K> createCacheService(final Consumer<LdapSearcherCache<R, K>> ldapSearchCacheConsumer,
            final OperationContext context, final LdapSearcher<R, K> searcher, final ModelNode cache) throws OperationFailedException {
        if (cache != null && cache.isDefined()) {
            ModelNode cacheDefinition = null;
            boolean byAccessTime = false;
            if (cache.hasDefined(BY_ACCESS_TIME)) {
                cacheDefinition = cache.require(BY_ACCESS_TIME);
                byAccessTime = true;
            } else if (cache.hasDefined(BY_SEARCH_TIME)) {
                cacheDefinition = cache.require(BY_SEARCH_TIME);
            }
            if (cacheDefinition != null) {
                int evictionTime = LdapCacheResourceDefinition.EVICTION_TIME.resolveModelAttribute(context, cacheDefinition).asInt();
                boolean cacheFailures = LdapCacheResourceDefinition.CACHE_FAILURES.resolveModelAttribute(context, cacheDefinition)
                        .asBoolean();
                int maxSize = LdapCacheResourceDefinition.MAX_CACHE_SIZE.resolveModelAttribute(context, cacheDefinition).asInt();

                return byAccessTime ? LdapCacheService.createByAccessCacheService(ldapSearchCacheConsumer, searcher, evictionTime, cacheFailures,
                        maxSize) : LdapCacheService.createBySearchCacheService(ldapSearchCacheConsumer, searcher, evictionTime, cacheFailures, maxSize);
            }
        }

        return LdapCacheService.createNoCacheService(ldapSearchCacheConsumer, searcher);
    }

    private Supplier<CallbackHandlerService> addLdapService(OperationContext context, ModelNode ldap, String realmName, ServiceTarget serviceTarget,
                                ServiceBuilder<?> realmBuilder, boolean shareConnection) throws OperationFailedException {
        ServiceName ldapServiceName = UserLdapCallbackHandler.ServiceUtil.createServiceName(realmName);

        final String baseDn = LdapAuthenticationResourceDefinition.BASE_DN.resolveModelAttribute(context, ldap).asString();
        ModelNode node = LdapAuthenticationResourceDefinition.USERNAME_FILTER.resolveModelAttribute(context, ldap);
        final String usernameAttribute = node.isDefined() ? node.asString() : null;
        node = LdapAuthenticationResourceDefinition.ADVANCED_FILTER.resolveModelAttribute(context, ldap);
        final String advancedFilter = node.isDefined() ? node.asString() : null;
        node = LdapAuthenticationResourceDefinition.USERNAME_LOAD.resolveModelAttribute(context, ldap);
        final String usernameLoad = node.isDefined() ? node.asString() : null;
        final boolean recursive = LdapAuthenticationResourceDefinition.RECURSIVE.resolveModelAttribute(context, ldap).asBoolean();
        final boolean allowEmptyPasswords = LdapAuthenticationResourceDefinition.ALLOW_EMPTY_PASSWORDS.resolveModelAttribute(context, ldap).asBoolean();
        final String userDn = LdapAuthenticationResourceDefinition.USER_DN.resolveModelAttribute(context, ldap).asString();

        final LdapSearcher<LdapEntry, String> userSearcher;
        if (usernameAttribute != null) {
            userSearcher = LdapUserSearcherFactory.createForUsernameFilter(baseDn, recursive, userDn, usernameAttribute, usernameLoad);
        } else {
            userSearcher = LdapUserSearcherFactory.createForAdvancedFilter(baseDn, recursive, userDn, advancedFilter, usernameLoad);
        }

        final ServiceName userSearcherCacheName = LdapSearcherCache.ServiceUtil.createServiceName(true, true, realmName);
        final ServiceBuilder<?> userSearchCacheBuilder = serviceTarget.addService(userSearcherCacheName);
        final Consumer<LdapSearcherCache<LdapEntry, String>> lscConsumer = userSearchCacheBuilder.provides(userSearcherCacheName);
        userSearchCacheBuilder.setInstance(createCacheService(lscConsumer, context, userSearcher, ldap.get(CACHE)));
        userSearchCacheBuilder.setInitialMode(ON_DEMAND);
        userSearchCacheBuilder.install();

        final ServiceBuilder<?> builder = serviceTarget.addService(ldapServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(ldapServiceName);
        final String connectionManager = LdapAuthenticationResourceDefinition.CONNECTION.resolveModelAttribute(context, ldap).asString();
        final Supplier<LdapConnectionManager> lcmSupplier = LdapConnectionManagerService.ServiceUtil.requires(builder, connectionManager);
        final Supplier<LdapSearcherCache<LdapEntry, String>> uscSupplier = LdapSearcherCache.ServiceUtil.requires(builder, true, true, realmName);
        builder.setInstance(new UserLdapCallbackHandler(chsConsumer, lcmSupplier, uscSupplier, allowEmptyPasswords, shareConnection));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, ldapServiceName);
    }

    private Supplier<CallbackHandlerService> addLocalService(OperationContext context, ModelNode local, String realmName, ServiceTarget serviceTarget,
                                 ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        ServiceName localServiceName = LocalCallbackHandlerService.ServiceUtil.createServiceName(realmName);
        ModelNode node = LocalAuthenticationResourceDefinition.DEFAULT_USER.resolveModelAttribute(context, local);
        String defaultUser = node.isDefined() ? node.asString() : null;
        node = LocalAuthenticationResourceDefinition.ALLOWED_USERS.resolveModelAttribute(context, local);
        String allowedUsers = node.isDefined() ? node.asString() : null;
        node = LocalAuthenticationResourceDefinition.SKIP_GROUP_LOADING.resolveModelAttribute(context, local);
        boolean skipGroupLoading = node.asBoolean();

        final ServiceBuilder<?> builder = serviceTarget.addService(localServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(localServiceName);
        builder.setInstance(new LocalCallbackHandlerService(chsConsumer, defaultUser, allowedUsers, skipGroupLoading));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, localServiceName);
    }

    private Supplier<CallbackHandlerService> addDomainManagedServersService(final OperationContext context, final ServiceBuilder<?> realmBuilder) {
        final ServiceRegistry registry = context.getServiceRegistry(false);
        ServiceController serviceController = registry.getService(DomainManagedServerCallbackHandler.SERVICE_NAME);
        if (serviceController != null) {
            return CallbackHandlerService.ServiceUtil.requires(realmBuilder, DomainManagedServerCallbackHandler.SERVICE_NAME);
        } else {
            return null;
        }
    }

    private Supplier<CallbackHandlerService> addPlugInAuthenticationService(OperationContext context, ModelNode model, String realmName,
                                                String registryName, ServiceTarget serviceTarget,
                                                ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName plugInServiceName = PlugInAuthenticationCallbackHandler.ServiceUtil.createServiceName(realmName);
        final String pluginName = PlugInAuthorizationResourceDefinition.NAME.resolveModelAttribute(context, model).asString();
        final Map<String, String> properties = resolveProperties(context, model);
        final String mechanismName = PlugInAuthenticationResourceDefinition.MECHANISM.resolveModelAttribute(context, model).asString();
        final AuthMechanism mechanism = AuthMechanism.valueOf(mechanismName);

        final ServiceBuilder<?> plugInBuilder = serviceTarget.addService(plugInServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = plugInBuilder.provides(plugInServiceName);
        final Supplier<PlugInLoaderService> pilSupplier = PlugInLoaderService.ServiceUtil.requires(plugInBuilder, realmName);
        plugInBuilder.setInstance(new PlugInAuthenticationCallbackHandler(chsConsumer, pilSupplier, registryName, pluginName, properties, mechanism));
        plugInBuilder.setInitialMode(ON_DEMAND);
        plugInBuilder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, plugInServiceName);
    }

    private Supplier<CallbackHandlerService> addPropertiesAuthenticationService(OperationContext context, ModelNode properties, String realmName,
                                                    ServiceTarget serviceTarget, ServiceBuilder<?> realmBuilder) throws OperationFailedException {

        final ServiceName propsServiceName = PropertiesCallbackHandler.ServiceUtil.createServiceName(realmName);
        final String path = PropertiesAuthenticationResourceDefinition.PATH.resolveModelAttribute(context, properties).asString();
        final ModelNode relativeToNode = PropertiesAuthenticationResourceDefinition.RELATIVE_TO.resolveModelAttribute(context, properties);
        final boolean plainText = PropertiesAuthenticationResourceDefinition.PLAIN_TEXT.resolveModelAttribute(context, properties).asBoolean();
        final String relativeTo = relativeToNode.isDefined() ? relativeToNode.asString() : null;

        final ServiceBuilder<?> builder = serviceTarget.addService(propsServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(propsServiceName);
        Supplier<PathManager> pmSupplier = null;
        if (relativeTo != null) {
            pmSupplier = builder.requires(context.getCapabilityServiceName(PATH_MANAGER_CAPABILITY, PathManager.class));
            builder.requires(pathName(relativeTo));
        }
        builder.setInstance(new PropertiesCallbackHandler(chsConsumer, pmSupplier, realmName, path, relativeTo, plainText));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, propsServiceName);
    }

    private Supplier<SubjectSupplementalService> addPropertiesAuthorizationService(OperationContext context, ModelNode properties,
                                                   String realmName, ServiceTarget serviceTarget, ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName propsServiceName = PropertiesSubjectSupplemental.ServiceUtil.createServiceName(realmName);
        final String path = PropertiesAuthorizationResourceDefinition.PATH.resolveModelAttribute(context, properties).asString();
        final ModelNode relativeToNode = PropertiesAuthorizationResourceDefinition.RELATIVE_TO.resolveModelAttribute(context, properties);
        final String relativeTo = relativeToNode.isDefined() ? relativeToNode.asString() : null;

        final ServiceBuilder<?> builder = serviceTarget.addService(propsServiceName);
        final Consumer<SubjectSupplementalService> sssConsumer = builder.provides(propsServiceName);
        Supplier<PathManager> pmSupplier = null;
        if (relativeTo != null) {
            pmSupplier = builder.requires(context.getCapabilityServiceName(PATH_MANAGER_CAPABILITY, PathManager.class));
            builder.requires(pathName(relativeTo));
        }
        builder.setInstance(new PropertiesSubjectSupplemental(sssConsumer, pmSupplier, realmName, path, relativeTo));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return SubjectSupplementalService.ServiceUtil.requires(realmBuilder, propsServiceName);
    }

    private Supplier<SubjectSupplementalService> addPlugInAuthorizationService(OperationContext context, ModelNode model, String realmName,
                                               ServiceTarget serviceTarget, ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName plugInServiceName = PlugInSubjectSupplemental.ServiceUtil.createServiceName(realmName);
        final String pluginName = PlugInAuthorizationResourceDefinition.NAME.resolveModelAttribute(context, model).asString();
        final Map<String, String> properties = resolveProperties(context, model);

        final ServiceBuilder<?> builder = serviceTarget.addService(plugInServiceName);
        final Consumer<SubjectSupplementalService> sssConsumer = builder.provides(plugInServiceName);
        final Supplier<PlugInLoaderService> pilSupplier = PlugInLoaderService.ServiceUtil.requires(builder, realmName);
        builder.setInstance(new PlugInSubjectSupplemental(sssConsumer, pilSupplier, realmName, pluginName, properties));
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return SubjectSupplementalService.ServiceUtil.requires(realmBuilder, plugInServiceName);
    }

    private Supplier<SubjectSupplementalService> addLdapAuthorizationService(OperationContext context, ModelNode ldap, String realmName, ServiceTarget serviceTarget,
                                             ServiceBuilder<?> realmBuilder, boolean shareConnection) throws OperationFailedException {

        ServiceName ldapName = LdapSubjectSupplementalService.ServiceUtil.createServiceName(realmName);

        LdapSearcher<LdapEntry, String> userSearcher = null;
        boolean forceUserDnSearch = false;
        ModelNode userCache = null;

        if (ldap.hasDefined(USERNAME_TO_DN)) {
            ModelNode usernameToDn = ldap.require(USERNAME_TO_DN);
            if (usernameToDn.hasDefined(USERNAME_IS_DN)) {
                ModelNode usernameIsDn = usernameToDn.require(USERNAME_IS_DN);
                userCache = usernameIsDn.get(CACHE);
                forceUserDnSearch = UserIsDnResourceDefintion.FORCE.resolveModelAttribute(context, usernameIsDn).asBoolean();

                userSearcher = LdapUserSearcherFactory.createForUsernameIsDn();
            } else if (usernameToDn.hasDefined(USERNAME_FILTER)) {
                ModelNode usernameFilter = usernameToDn.require(USERNAME_FILTER);
                userCache = usernameFilter.get(CACHE);
                forceUserDnSearch = UserSearchResourceDefintion.FORCE.resolveModelAttribute(context, usernameFilter).asBoolean();
                String baseDn = UserSearchResourceDefintion.BASE_DN.resolveModelAttribute(context, usernameFilter).asString();
                boolean recursive =  UserSearchResourceDefintion.RECURSIVE.resolveModelAttribute(context, usernameFilter).asBoolean();
                String userDnAttribute = UserSearchResourceDefintion.USER_DN_ATTRIBUTE.resolveModelAttribute(context, usernameFilter).asString();
                String usernameAttribute = UserSearchResourceDefintion.ATTRIBUTE.resolveModelAttribute(context, usernameFilter).asString();

                userSearcher = LdapUserSearcherFactory.createForUsernameFilter(baseDn, recursive, userDnAttribute, usernameAttribute, null);
            } else if (usernameToDn.hasDefined(ADVANCED_FILTER)) {
                ModelNode advancedFilter = usernameToDn.require(ADVANCED_FILTER);
                userCache = advancedFilter.get(CACHE);
                forceUserDnSearch = AdvancedUserSearchResourceDefintion.FORCE.resolveModelAttribute(context, advancedFilter).asBoolean();
                String baseDn = AdvancedUserSearchResourceDefintion.BASE_DN.resolveModelAttribute(context, advancedFilter).asString();
                boolean recursive =  AdvancedUserSearchResourceDefintion.RECURSIVE.resolveModelAttribute(context, advancedFilter).asBoolean();
                String userDnAttribute = AdvancedUserSearchResourceDefintion.USER_DN_ATTRIBUTE.resolveModelAttribute(context, advancedFilter).asString();
                String filter = AdvancedUserSearchResourceDefintion.FILTER.resolveModelAttribute(context, advancedFilter).asString();

                userSearcher = LdapUserSearcherFactory.createForAdvancedFilter(baseDn, recursive, userDnAttribute, filter, null);
            }
        }

        if (userSearcher != null) {
            final ServiceName userSearcherCacheName = LdapSearcherCache.ServiceUtil.createServiceName(false, true, realmName);
            final ServiceBuilder<?> userSearchCacheBuilder = serviceTarget.addService(userSearcherCacheName);
            final Consumer<LdapSearcherCache<LdapEntry, String>> lscConsumer = userSearchCacheBuilder.provides(userSearcherCacheName);
            userSearchCacheBuilder.setInstance(createCacheService(lscConsumer, context, userSearcher, userCache));
            userSearchCacheBuilder.setInitialMode(ON_DEMAND);
            userSearchCacheBuilder.install();
        }

        ModelNode groupSearch = ldap.require(GROUP_SEARCH);
        LdapSearcher<LdapEntry[], LdapEntry> groupSearcher;
        boolean iterative = false;
        GroupName groupName = GroupName.DISTINGUISHED_NAME;
        ModelNode groupCache = null;
        if (groupSearch.hasDefined(GROUP_TO_PRINCIPAL)) {
            ModelNode groupToPrincipal = groupSearch.require(GROUP_TO_PRINCIPAL);
            groupCache = groupToPrincipal.get(CACHE);
            String baseDn = GroupToPrincipalResourceDefinition.BASE_DN.resolveModelAttribute(context, groupToPrincipal).asString();
            String groupDnAttribute = GroupToPrincipalResourceDefinition.GROUP_DN_ATTRIBUTE.resolveModelAttribute(context, groupToPrincipal).asString();
            groupName = GroupName.valueOf(GroupToPrincipalResourceDefinition.GROUP_NAME.resolveModelAttribute(context, groupToPrincipal).asString());
            String groupNameAttribute = GroupToPrincipalResourceDefinition.GROUP_NAME_ATTRIBUTE.resolveModelAttribute(context, groupToPrincipal).asString();
            iterative = GroupToPrincipalResourceDefinition.ITERATIVE.resolveModelAttribute(context, groupToPrincipal).asBoolean();
            String principalAttribute = GroupToPrincipalResourceDefinition.PRINCIPAL_ATTRIBUTE.resolveModelAttribute(context, groupToPrincipal).asString();
            boolean recursive = GroupToPrincipalResourceDefinition.RECURSIVE.resolveModelAttribute(context, groupToPrincipal).asBoolean();
            GroupName searchBy = GroupName.valueOf(GroupToPrincipalResourceDefinition.SEARCH_BY.resolveModelAttribute(context, groupToPrincipal).asString());
            boolean preferOriginalConnection = GroupToPrincipalResourceDefinition.PREFER_ORIGINAL_CONNECTION.resolveModelAttribute(context, groupToPrincipal).asBoolean();

            groupSearcher = LdapGroupSearcherFactory.createForGroupToPrincipal(baseDn, groupDnAttribute, groupNameAttribute, principalAttribute, recursive, searchBy, preferOriginalConnection);
        } else {
            ModelNode principalToGroup = groupSearch.require(PRINCIPAL_TO_GROUP);
            groupCache = principalToGroup.get(CACHE);
            String groupAttribute = PrincipalToGroupResourceDefinition.GROUP_ATTRIBUTE.resolveModelAttribute(context, principalToGroup).asString();
            boolean preferOriginalConnection = PrincipalToGroupResourceDefinition.PREFER_ORIGINAL_CONNECTION.resolveModelAttribute(context, principalToGroup).asBoolean();
            // TODO - Why was this never used?
            String groupDnAttribute = PrincipalToGroupResourceDefinition.GROUP_DN_ATTRIBUTE.resolveModelAttribute(context, principalToGroup).asString();
            groupName = GroupName.valueOf(PrincipalToGroupResourceDefinition.GROUP_NAME.resolveModelAttribute(context, principalToGroup).asString());
            String groupNameAttribute = PrincipalToGroupResourceDefinition.GROUP_NAME_ATTRIBUTE.resolveModelAttribute(context, principalToGroup).asString();
            iterative = PrincipalToGroupResourceDefinition.ITERATIVE.resolveModelAttribute(context, principalToGroup).asBoolean();
            boolean skipMissingGroups = PrincipalToGroupResourceDefinition.SKIP_MISSING_GROUPS.resolveModelAttribute(context, principalToGroup).asBoolean();
            boolean shouldParseGroupFromDN = PrincipalToGroupResourceDefinition.PARSE_ROLES_FROM_DN.resolveModelAttribute(context, principalToGroup).asBoolean();
            groupSearcher = LdapGroupSearcherFactory.createForPrincipalToGroup(groupAttribute, groupNameAttribute, preferOriginalConnection, skipMissingGroups, GroupName.SIMPLE == groupName, shouldParseGroupFromDN);
        }

        final ServiceName groupCacheServiceName = LdapSearcherCache.ServiceUtil.createServiceName(false, false, realmName);
        final ServiceBuilder<?> groupCacheBuilder = serviceTarget.addService(groupCacheServiceName);
        final Consumer<LdapSearcherCache<LdapEntry[], LdapEntry>> lscConsumer = groupCacheBuilder.provides(groupCacheServiceName);
        groupCacheBuilder.setInstance(createCacheService(lscConsumer, context, groupSearcher, groupCache));
        groupCacheBuilder.setInitialMode(ON_DEMAND);
        groupCacheBuilder.install();

        String connectionName = LdapAuthorizationResourceDefinition.CONNECTION.resolveModelAttribute(context, ldap).asString();

        final ServiceBuilder<?> ldapBuilder = serviceTarget.addService(ldapName);
        final Consumer<SubjectSupplementalService> sssConsumer = ldapBuilder.provides(ldapName);
        final Supplier<LdapConnectionManager> lcmSupplier = LdapConnectionManagerService.ServiceUtil.requires(ldapBuilder, connectionName);
        Supplier<LdapSearcherCache<LdapEntry, String>> usSupplier = null;
        if (userSearcher != null) {
            usSupplier = LdapSearcherCache.ServiceUtil.requires(ldapBuilder, false, true, realmName);
        }
        final Supplier<LdapSearcherCache<LdapEntry[], LdapEntry>> gsSupplier = LdapSearcherCache.ServiceUtil.requires(ldapBuilder, false, false, realmName);
        ldapBuilder.setInstance(new LdapSubjectSupplementalService(sssConsumer, lcmSupplier, usSupplier, gsSupplier, realmName, shareConnection, forceUserDnSearch, iterative, groupName));
        ldapBuilder.setInitialMode(ON_DEMAND);
        ldapBuilder.install();

        return SubjectSupplementalService.ServiceUtil.requires(realmBuilder, ldapName);
    }

    private Supplier<SSLContext> addSSLServices(OperationContext context, ModelNode ssl, ModelNode trustStore, String realmName,
                                ServiceTarget serviceTarget, ServiceBuilder<?> realmBuilder) throws OperationFailedException {

        // Use undefined structures for null ssl model
        ssl = (ssl == null) ? new ModelNode() : ssl;

        ServiceName keyManagerServiceName = null;

        final String provider = KeystoreAttributes.KEYSTORE_PROVIDER.resolveModelAttribute(context, ssl).asString();
        if (ssl.hasDefined(KEYSTORE_PATH) || !JKS.equalsIgnoreCase(provider)) {
            keyManagerServiceName = AbstractKeyManagerService.ServiceUtil.createServiceName(SecurityRealm.ServiceUtil.createServiceName(realmName));
            addKeyManagerService(context, ssl, keyManagerServiceName, serviceTarget);
        }

        ServiceName trustManagerServiceName = null;
        if (trustStore != null) {
            trustManagerServiceName = AbstractTrustManagerService.ServiceUtil.createServiceName(SecurityRealm.ServiceUtil.createServiceName(realmName));
            addTrustManagerService(context, trustStore, trustManagerServiceName, serviceTarget);
        }

        String protocol = SSLServerIdentityResourceDefinition.PROTOCOL.resolveModelAttribute(context, ssl).asString();

        // Enabled Cipher Suites
        final Set<String> enabledCipherSuites = new HashSet<String>();
        ModelNode suitesNode = SSLServerIdentityResourceDefinition.ENABLED_CIPHER_SUITES.resolveModelAttribute(context, ssl);
        if (suitesNode.isDefined()) {
            List<ModelNode> list = suitesNode.asList();
            for (ModelNode current : list) {
                enabledCipherSuites.add(current.asString());
            }
        }

        // Enabled Protocols
        final Set<String> enabledProtocols = new HashSet<String>();
        ModelNode protocolsNode = SSLServerIdentityResourceDefinition.ENABLED_PROTOCOLS.resolveModelAttribute(context, ssl);
        if (protocolsNode.isDefined()) {
            List<ModelNode> list = protocolsNode.asList();
            for (ModelNode current : list) {
                enabledProtocols.add(current.asString());
            }
        }

        /*
         * At this point we register two SSLContextService instances, one linked to both the key and trust store and the other just for trust.
         *
         * Subsequent dependencies will trigger which (or both) are actually started.
         */
        ServiceName fullServiceName = SSLContextService.ServiceUtil.createServiceName(SecurityRealm.ServiceUtil.createServiceName(realmName), false);
        ServiceName trustOnlyServiceName = SSLContextService.ServiceUtil.createServiceName(SecurityRealm.ServiceUtil.createServiceName(realmName), true);

        Consumer<ServiceBuilder> serviceBuilderConsumer = s -> {};
        try {
            serviceBuilderConsumer = context.getCapabilityRuntimeAPI(ELYTRON_CAPABILITY, Consumer.class);
        } catch (IllegalStateException e) {}

        if (keyManagerServiceName != null) {
            // An alias will not be set on the trust based SSLContext.
            final ServiceBuilder<?> fullBuilder = serviceTarget.addService(fullServiceName);
            final Consumer<SSLContext> sslContextConsumer = fullBuilder.provides(fullServiceName);
            final Supplier<AbstractKeyManagerService> keyManagersSupplier = AbstractKeyManagerService.ServiceUtil.requires(fullBuilder, SecurityRealm.ServiceUtil.createServiceName(realmName));
            final Supplier<TrustManager[]> trustManagersSupplier = trustManagerServiceName != null ? AbstractTrustManagerService.ServiceUtil.requires(fullBuilder, SecurityRealm.ServiceUtil.createServiceName(realmName)) : null;
            fullBuilder.setInstance(new SSLContextService(sslContextConsumer, keyManagersSupplier, trustManagersSupplier, protocol, enabledCipherSuites, enabledProtocols));
            serviceBuilderConsumer.accept(fullBuilder);
            fullBuilder.setInitialMode(ON_DEMAND);
            fullBuilder.install();
        }

        // Always register this one - if no KeyStore is defined we can add an alias to this.
        final ServiceBuilder<?> trustBuilder = serviceTarget.addService(trustOnlyServiceName);
        final Consumer<SSLContext> sslContextConsumer;
        if (keyManagerServiceName != null) {
            sslContextConsumer = trustBuilder.provides(trustOnlyServiceName);
        } else {
            // No KeyStore so just alias to this.
            sslContextConsumer = trustBuilder.provides(trustOnlyServiceName, fullServiceName);
        }
        final Supplier<TrustManager[]> trustManagersSupplier = trustManagerServiceName != null ? AbstractTrustManagerService.ServiceUtil.requires(trustBuilder, SecurityRealm.ServiceUtil.createServiceName(realmName)) : null;
        trustBuilder.setInstance(new SSLContextService(sslContextConsumer, null, trustManagersSupplier, protocol, enabledCipherSuites, enabledProtocols));
        serviceBuilderConsumer.accept(trustBuilder);
        trustBuilder.setInitialMode(ON_DEMAND);
        trustBuilder.install();

        return SSLContextService.ServiceUtil.requires(realmBuilder, SecurityRealm.ServiceUtil.createServiceName(realmName), false);
    }

    private void addKeyManagerService(OperationContext context, ModelNode ssl, ServiceName serviceName,
                                      ServiceTarget serviceTarget) throws OperationFailedException {
        ModelNode keystorePasswordNode = KeystoreAttributes.KEYSTORE_PASSWORD.resolveModelAttribute(context, ssl);
        char[] keystorePassword = keystorePasswordNode.isDefined() ? keystorePasswordNode.asString().toCharArray() : null;

        ModelNode providerNode = KeystoreAttributes.KEYSTORE_PROVIDER.resolveModelAttribute(context, ssl);
        String provider = providerNode.isDefined() ? providerNode.asString() : null;

        String autoGenerateCertHostName = null;
        ModelNode autoGenerateCertHostNode = KeystoreAttributes.GENERATE_SELF_SIGNED_CERTIFICATE_HOST.resolveModelAttribute(context, ssl);
        if(autoGenerateCertHostNode.isDefined()) {
            autoGenerateCertHostName = autoGenerateCertHostNode.asString();
        }

        ModelNode pathNode = KeystoreAttributes.KEYSTORE_PATH.resolveModelAttribute(context, ssl);
        final ServiceBuilder<?> serviceBuilder;
        if (pathNode.isDefined() == false) {
            serviceBuilder = serviceTarget.addService(serviceName);
            final Consumer<AbstractKeyManagerService> kmsConsumer = serviceBuilder.provides(serviceName);
            serviceBuilder.setInstance(new ProviderKeyManagerService(kmsConsumer, null, null, provider, keystorePassword));
        } else {
            String path = pathNode.asString();

            ModelNode keyPasswordNode = KeystoreAttributes.KEY_PASSWORD.resolveModelAttribute(context, ssl);
            final char[] keyPassword = keyPasswordNode.isDefined() ? keyPasswordNode.asString().toCharArray() : null;

            ModelNode relativeToNode = KeystoreAttributes.KEYSTORE_RELATIVE_TO.resolveModelAttribute(context, ssl);
            String relativeTo = relativeToNode.isDefined() ? relativeToNode.asString() : null;

            ModelNode aliasNode = KeystoreAttributes.ALIAS.resolveModelAttribute(context, ssl);
            String alias = aliasNode.isDefined() ? aliasNode.asString() : null;

            serviceBuilder = serviceTarget.addService(serviceName);
            final Consumer<AbstractKeyManagerService> kmsConsumer = serviceBuilder.provides(serviceName);
            Supplier<PathManager> pathManagerSupplier = null;
            if (relativeTo != null) {
                pathManagerSupplier = serviceBuilder.requires(context.getCapabilityServiceName(PATH_MANAGER_CAPABILITY, PathManager.class));
                serviceBuilder.requires(pathName(relativeTo));
            }
            ExceptionSupplier<CredentialSource, Exception> keyCredentialSourceSupplier = null;
            ExceptionSupplier<CredentialSource, Exception> keystoreCredentialSourceSupplier = null;
            final String keySuffix = SERVER_IDENTITY + KEY_DELIMITER + SSL;
            if (ssl.hasDefined(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                keyCredentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, ssl, serviceBuilder, keySuffix);
            }
            if (ssl.hasDefined(KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                keystoreCredentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, KeystoreAttributes.KEY_PASSWORD_CREDENTIAL_REFERENCE, ssl, serviceBuilder, keySuffix);
            }
            serviceBuilder.setInstance(new FileKeyManagerService(kmsConsumer, pathManagerSupplier, keyCredentialSourceSupplier, keystoreCredentialSourceSupplier, provider, path, relativeTo, keystorePassword, keyPassword, alias, autoGenerateCertHostName));
        }

        serviceBuilder.setInitialMode(ON_DEMAND);
        serviceBuilder.install();
    }

    private void addTrustManagerService(OperationContext context, ModelNode ssl, ServiceName serviceName,
                                        ServiceTarget serviceTarget) throws OperationFailedException {

        final ServiceBuilder<?> serviceBuilder;

        ModelNode keystorePasswordNode = KeystoreAttributes.KEYSTORE_PASSWORD.resolveModelAttribute(context, ssl);
        char[] keystorePassword = keystorePasswordNode.isDefined() ? keystorePasswordNode.asString().toCharArray() : null;
        final String provider = KeystoreAttributes.KEYSTORE_PROVIDER.resolveModelAttribute(context, ssl).asString();
        String keySuffix = AUTHENTICATION + KEY_DELIMITER + TRUSTSTORE;

        if (!JKS.equalsIgnoreCase(provider)) {
            serviceBuilder = serviceTarget.addService(serviceName);
            final Consumer<TrustManager[]> trustManagersConsumer = serviceBuilder.provides(serviceName);
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            if (ssl.hasDefined(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, ssl, serviceBuilder, keySuffix);
            }
            serviceBuilder.setInstance(new ProviderTrustManagerService(trustManagersConsumer, credentialSourceSupplier, provider, keystorePassword));
        } else {
            String path = KeystoreAttributes.KEYSTORE_PATH.resolveModelAttribute(context, ssl).asString();
            ModelNode relativeToNode = KeystoreAttributes.KEYSTORE_RELATIVE_TO.resolveModelAttribute(context, ssl);
            String relativeTo = relativeToNode.isDefined() ? relativeToNode.asString() : null;

            serviceBuilder = serviceTarget.addService(serviceName);
            final Consumer<TrustManager[]> trustManagersConsumer = serviceBuilder.provides(serviceName);
            Supplier<PathManager> pathManagerSupplier = null;
            if (relativeTo != null) {
                pathManagerSupplier = serviceBuilder.requires(context.getCapabilityServiceName(PATH_MANAGER_CAPABILITY, PathManager.class));
                serviceBuilder.requires(pathName(relativeTo));
            }
            ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
            if (ssl.hasDefined(KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE_NAME)) {
                credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, KeystoreAttributes.KEYSTORE_PASSWORD_CREDENTIAL_REFERENCE, ssl, serviceBuilder, keySuffix);
            }
            serviceBuilder.setInstance(new FileTrustManagerService(trustManagersConsumer, pathManagerSupplier, credentialSourceSupplier, provider, path, relativeTo, keystorePassword));
        }

        serviceBuilder.setInitialMode(ON_DEMAND);
        serviceBuilder.install();
    }

    private Supplier<CallbackHandlerFactory> addSecretService(OperationContext context, ModelNode secret, String realmName, ServiceTarget serviceTarget,
                                  ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName secretServiceName = SecretIdentityService.ServiceUtil.createServiceName(realmName);
        final ModelNode resolvedValueNode = SecretServerIdentityResourceDefinition.VALUE.resolveModelAttribute(context, secret);
        boolean base64 = secret.get(SecretServerIdentityResourceDefinition.VALUE.getName()).getType() != ModelType.EXPRESSION;
        final ServiceBuilder<?> builder = serviceTarget.addService(secretServiceName);
        final Consumer<CallbackHandlerFactory> chfConsumer = builder.provides(secretServiceName);
        ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
        if (secret.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)) {
            String keySuffix = SERVER_IDENTITY + KEY_DELIMITER + SECRET;
            credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, SecretServerIdentityResourceDefinition.CREDENTIAL_REFERENCE, secret, builder, keySuffix);
        }
        SecretIdentityService sis;
        if (secret.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)) {
            sis = new SecretIdentityService(chfConsumer, credentialSourceSupplier, resolvedValueNode.asString(), false);
        } else {
            sis = new SecretIdentityService(chfConsumer, credentialSourceSupplier, resolvedValueNode.asString(), base64);
        }
        builder.setInstance(sis);
        builder.setInitialMode(ON_DEMAND);
        builder.install();

        return CallbackHandlerFactory.ServiceUtil.requires(realmBuilder, secretServiceName);
    }

    private Supplier<KeytabIdentityFactoryService> addKerberosIdentityServices(OperationContext context, ModelNode kerberos, String realmName, ServiceTarget serviceTarget,
        ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        ServiceName keyIdentityName = KeytabIdentityFactoryService.ServiceUtil.createServiceName(realmName);
        final ServiceBuilder<?> kifsBuilder = serviceTarget.addService(keyIdentityName);
        final Consumer<KeytabIdentityFactoryService> kifsConsumer = kifsBuilder.provides(keyIdentityName);
        final KeytabIdentityFactoryService kifs = new KeytabIdentityFactoryService(kifsConsumer);
        kifsBuilder.setInstance(kifs);
        kifsBuilder.setInitialMode(ON_DEMAND);

        if (kerberos.hasDefined(KEYTAB)) {
            List<Property> keytabList = kerberos.get(KEYTAB).asPropertyList();
            for (Property current : keytabList) {
                String principal = current.getName();
                ModelNode keytab = current.getValue();
                String path = KeytabResourceDefinition.PATH.resolveModelAttribute(context, keytab).asString();
                ModelNode relativeToNode = KeytabResourceDefinition.RELATIVE_TO.resolveModelAttribute(context, keytab);
                String relativeTo = relativeToNode.isDefined() ? relativeToNode.asString() : null;
                boolean debug = KeytabResourceDefinition.DEBUG.resolveModelAttribute(context, keytab).asBoolean();
                final String[] forHostsValues;
                ModelNode forHosts = KeytabResourceDefinition.FOR_HOSTS.resolveModelAttribute(context, keytab);
                if (forHosts.isDefined()) {
                    List<ModelNode> list = forHosts.asList();
                    forHostsValues = new String[list.size()];
                    for (int i=0;i<list.size();i++) {
                        forHostsValues[i] = list.get(i).asString();
                    }
                } else {
                    forHostsValues = new String[0];
                }

                ServiceName keytabName = KeytabService.ServiceUtil.createServiceName(realmName, principal);

                final ServiceBuilder<?> keytabBuilder = serviceTarget.addService(keytabName);
                final Consumer<KeytabService> ksConsumer = keytabBuilder.provides(keytabName);
                Supplier<PathManager> pathManagerSupplier = null;

                if (relativeTo != null) {
                    pathManagerSupplier = keytabBuilder.requires(context.getCapabilityServiceName(PATH_MANAGER_CAPABILITY, PathManager.class));
                    keytabBuilder.requires(pathName(relativeTo));
                }
                keytabBuilder.setInstance(new KeytabService(ksConsumer, pathManagerSupplier, principal, path, relativeTo, forHostsValues, debug));
                keytabBuilder.setInitialMode(ON_DEMAND);
                keytabBuilder.install();
                kifs.addKeytabSupplier(KeytabService.ServiceUtil.requires(kifsBuilder, realmName, principal));
             }
         }

         kifsBuilder.install();

         return KeytabIdentityFactoryService.ServiceUtil.requires(realmBuilder, realmName);
    }

    private Supplier<CallbackHandlerService> addUsersService(OperationContext context, ModelNode users, String realmName, ServiceTarget serviceTarget,
                                 ServiceBuilder<?> realmBuilder) throws OperationFailedException {
        final ServiceName usersServiceName = UserDomainCallbackHandler.ServiceUtil.createServiceName(realmName);
        final ServiceBuilder<?> builder = serviceTarget.addService(usersServiceName);
        final Consumer<CallbackHandlerService> chsConsumer = builder.provides(usersServiceName);
        builder.setInstance(new UserDomainCallbackHandler(chsConsumer, unmaskUsersCredentials(context, builder, users.clone()), realmName, unmaskUsersPasswords(context, users)));
        builder.setInitialMode(ServiceController.Mode.ON_DEMAND);
        builder.install();

        return CallbackHandlerService.ServiceUtil.requires(realmBuilder, usersServiceName);
    }

    private static ServiceName pathName(String relativeTo) {
        return ServiceName.JBOSS.append(SERVER, PATH, relativeTo);
    }

    private ModelNode unmaskUsersPasswords(OperationContext context, ModelNode users) throws OperationFailedException {
        users = users.clone();
        for (Property property : users.get(USER).asPropertyList()) {
            // Don't use the value from property as it is a clone and does not update the returned users ModelNode.
            ModelNode user = users.get(USER, property.getName());
            if (user.hasDefined(PASSWORD)) {
                //TODO This will be cleaned up once it uses attribute definitions
                user.set(PASSWORD, context.resolveExpressions(user.get(PASSWORD)).asString());
            }
        }
        return users;
    }

    private Map<String, ExceptionSupplier<CredentialSource, Exception>> unmaskUsersCredentials(OperationContext context, ServiceBuilder<?> serviceBuilder, ModelNode users) throws OperationFailedException {
        Map<String, ExceptionSupplier<CredentialSource, Exception>> suppliers = new HashMap<>();
        for (Property property : users.get(USER).asPropertyList()) {
            // Don't use the value from property as it is a clone and does not update the returned users ModelNode.
            ModelNode user = users.get(USER, property.getName());
            if (user.hasDefined(CredentialReference.CREDENTIAL_REFERENCE)) {
                String keySuffix = AUTHENTICATION + KEY_DELIMITER + USERS + KEY_DELIMITER + USER + KEY_DELIMITER + property.getName();
                suppliers.put(property.getName(), CredentialReference.getCredentialSourceSupplier(context, UserResourceDefinition.CREDENTIAL_REFERENCE, user, serviceBuilder, keySuffix));
            }
        }
        return suppliers;
    }

    private static Map<String, String> resolveProperties( final OperationContext context, final ModelNode model) throws OperationFailedException {
        Map<String, String> configurationProperties;
        if (model.hasDefined(PROPERTY)) {
            List<Property> propertyList = model.require(PROPERTY).asPropertyList();
            configurationProperties = new HashMap<String, String>(propertyList.size());

            for (Property current : propertyList) {
                String propertyName = current.getName();
                ModelNode valueNode = PropertyResourceDefinition.VALUE.resolveModelAttribute(context, current.getValue());
                String value = valueNode.isDefined() ? valueNode.asString() : null;
                configurationProperties.put(propertyName, value);
            }
            configurationProperties = Collections.unmodifiableMap(configurationProperties);
        } else {
            configurationProperties = Collections.emptyMap();
        }
        return configurationProperties;
    }

    private static class ServiceInstallStepHandler implements OperationStepHandler {

        private static final ServiceInstallStepHandler INSTANCE = new ServiceInstallStepHandler();

        @Override
        public void execute(OperationContext context, ModelNode operation) throws OperationFailedException {
            final ModelNode model = Resource.Tools.readModel(context.readResource(PathAddress.EMPTY_ADDRESS));
            SecurityRealmAddHandler.INSTANCE.installServices(context, context.getCurrentAddressValue(), model);
        }
    }
}
