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

package org.jboss.as.domain.management.connections.ldap;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.security.CredentialReference.handleCredentialReferenceUpdate;
import static org.jboss.as.controller.security.CredentialReference.rollbackCredentialStoreUpdate;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.ALWAYS_SEND_CLIENT_CERT;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.HANDLES_REFERRALS_FOR;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.INITIAL_CONTEXT_FACTORY;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.REFERRALS;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.SEARCH_CREDENTIAL;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.SEARCH_DN;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.SECURITY_REALM;
import static org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.URL;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.jboss.as.controller.AbstractAddStepHandler;
import org.jboss.as.controller.AttributeDefinition;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.domain.management.SecurityRealm;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionManagerService.Config;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition.ReferralHandling;
import org.jboss.as.domain.management.security.SSLContextService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.wildfly.common.function.ExceptionSupplier;
import org.wildfly.security.credential.source.CredentialSource;

import javax.net.ssl.SSLContext;

/**
 * Handler for adding ldap management connections.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 * @author <a href="mailto:ropalka@redhat.com">Richard Opalka</a>
 */
public class LdapConnectionAddHandler extends AbstractAddStepHandler {

    private final LdapConnectionManagerRegistry connectionManagerRegistry = new LdapConnectionManagerRegistry();

    static LdapConnectionAddHandler newInstance() {
        return new LdapConnectionAddHandler();
    }

    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
        for (AttributeDefinition attr : LdapConnectionResourceDefinition.ATTRIBUTE_DEFINITIONS) {
            attr.validateAndSet(operation, model);
        }
    }

    protected void populateModel(final OperationContext context, final ModelNode operation, final Resource resource) throws  OperationFailedException {
        super.populateModel(context, operation, resource);
        final ModelNode model = resource.getModel();
        handleCredentialReferenceUpdate(context, model.get(SEARCH_CREDENTIAL_REFERENCE.getName()), SEARCH_CREDENTIAL_REFERENCE.getName());
    }

    @Override
    protected boolean requiresRuntime(OperationContext context) {
        return true;
    }

    @Override
    protected void performRuntime(final OperationContext context, final ModelNode operation, final ModelNode model) throws OperationFailedException {
        final PathAddress address = PathAddress.pathAddress(operation.get(OP_ADDR));
        final String name = address.getLastElement().getValue();

        final ServiceTarget serviceTarget = context.getServiceTarget();
        final ServiceName ldapConMgrName = LdapConnectionManagerService.ServiceUtil.createServiceName(name);
        final ServiceBuilder<?> sb = serviceTarget.addService(ldapConMgrName);
        final Consumer<LdapConnectionManager> lcmConsumer = sb.provides(ldapConMgrName);

        final ModelNode securityRealm = SECURITY_REALM.resolveModelAttribute(context, model);
        Supplier<SSLContext> fullSSLContextSupplier = null;
        Supplier<SSLContext> trustSSLContextSupplier = null;
        if (securityRealm.isDefined()) {
            String realmName = securityRealm.asString();
            fullSSLContextSupplier = SSLContextService.ServiceUtil.requires(sb, SecurityRealm.ServiceUtil.createServiceName(realmName), false);
            trustSSLContextSupplier = SSLContextService.ServiceUtil.requires(sb, SecurityRealm.ServiceUtil.createServiceName(realmName), true);
        }
        ExceptionSupplier<CredentialSource, Exception> credentialSourceSupplier = null;
        if (LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE.resolveModelAttribute(context, model).isDefined()) {
            credentialSourceSupplier = CredentialReference.getCredentialSourceSupplier(context, LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE, model, sb);
        }
        final LdapConnectionManagerService connectionManagerService = new LdapConnectionManagerService(
                lcmConsumer, fullSSLContextSupplier, trustSSLContextSupplier, credentialSourceSupplier, name, connectionManagerRegistry);
        updateRuntime(context, model, connectionManagerService);
        sb.setInstance(connectionManagerService);
        sb.install();
    }

    @Override
    protected void rollbackRuntime(OperationContext context, final ModelNode operation, final Resource resource) {
        rollbackCredentialStoreUpdate(LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE, context, resource);
    }


    static Config updateRuntime(final OperationContext context, final ModelNode model, final LdapConnectionManagerService connectionManagerService) throws OperationFailedException {
        String initialContextFactory = INITIAL_CONTEXT_FACTORY.resolveModelAttribute(context, model).asString();
        String url = URL.resolveModelAttribute(context, model).asString();
        ModelNode searchDnNode = SEARCH_DN.resolveModelAttribute(context, model);
        String searchDn = searchDnNode.isDefined() ? searchDnNode.asString() : null;
        ModelNode searchCredentialNode = SEARCH_CREDENTIAL.resolveModelAttribute(context, model);
        String searchCredential = searchCredentialNode.isDefined() ? searchCredentialNode.asString() : null;
        ReferralHandling referralHandling = ReferralHandling.valueOf(REFERRALS.resolveModelAttribute(context, model).asString());
        final Set<URI> handlesReferralsForSet;
        ModelNode handlesReferralsFor = HANDLES_REFERRALS_FOR.resolveModelAttribute(context, model);
        if (handlesReferralsFor.isDefined()) {
            List<ModelNode> list = handlesReferralsFor.asList();
            handlesReferralsForSet = new HashSet<URI>(list.size());
            for (ModelNode current : list) {
                try {
                    handlesReferralsForSet.add(new URI(current.asString()));
                } catch (URISyntaxException e) {
                    // TODO - Add an error but this should not be possible as the attribute was previously validated.
                    throw new OperationFailedException(e);
                }
            }
        } else {
            handlesReferralsForSet = Collections.emptySet();
        }

        final boolean alwaysSendClientCert = ALWAYS_SEND_CLIENT_CERT.resolveModelAttribute(context, model).asBoolean();
        return connectionManagerService.setConfiguration(initialContextFactory, url, searchDn, searchCredential, referralHandling, handlesReferralsForSet, alwaysSendClientCert);
    }

}
