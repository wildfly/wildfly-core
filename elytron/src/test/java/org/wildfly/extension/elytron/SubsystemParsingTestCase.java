/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2014 Red Hat, Inc., and individual contributors
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.LDAP_CONNECTION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import org.jboss.as.controller.CapabilityServiceTarget;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationDefinition;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.OperationStepHandler;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.ProcessType;
import org.jboss.as.controller.RunningMode;
import org.jboss.as.controller.access.Action;
import org.jboss.as.controller.access.AuthorizationResult;
import org.jboss.as.controller.access.Caller;
import org.jboss.as.controller.access.Environment;
import org.jboss.as.controller.access.ResourceAuthorization;
import org.jboss.as.controller.capability.CapabilityServiceSupport;
import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.client.MessageSeverity;
import org.jboss.as.controller.notification.Notification;
import org.jboss.as.controller.registry.ImmutableManagementResourceRegistration;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.Resource;
import org.jboss.as.controller.security.CredentialReference;
import org.jboss.as.domain.management.connections.ldap.LdapConnectionResourceDefinition;
import org.jboss.as.subsystem.test.AbstractSubsystemBaseTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceContainer;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceNotFoundException;
import org.jboss.msc.service.ServiceRegistry;
import org.jboss.msc.service.ServiceTarget;
import org.junit.Assert;
import org.junit.Test;
import org.wildfly.security.auth.server.SecurityIdentity;

/**
 * Tests all management expects for subsystem, parsing, marshaling, model definition and other
 * Here is an example that allows you a fine grained controller over what is tested and how. So it can give you ideas what can be done and tested.
 * If you have no need for advanced testing of subsystem you look at {@link ElytronSubsystem20TestCase} that tests same stuff but most of the code
 * is hidden inside of test harness
 *
 * @author <a href="kabir.khan@jboss.com">Kabir Khan</a>
 */
public class SubsystemParsingTestCase extends AbstractSubsystemBaseTest {

    public SubsystemParsingTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }


    @Override
    protected String getSubsystemXml() throws IOException {
        return readResource("domain-test.xml");
    }

    @Override
    protected String getComparisonXml(String configId) throws IOException {
        // Mappers utilizes a boolean with a parameter corrector
        return "mappers.xml".equals(configId) ? readResource("compare-mappers.xml") : null;
    }

    @Test
    public void testParseAndMarshalModel_AuditLogging() throws Exception {
        standardSubsystemTest("audit-logging.xml");
    }

    @Test
    public void testParseAndMarshalModel_AuthenticationClient() throws Exception {
        standardSubsystemTest("authentication-client.xml");
    }

    @Test
    public void testParseAndMarshalModel_Domain() throws Exception {
        standardSubsystemTest("domain.xml");
    }

    @Test
    public void testParseAndMarshalModel_TLS() throws Exception {
        standardSubsystemTest("tls.xml");
    }

    @Test
    public void testParseAndMarshalModel_ProviderLoader() throws Exception {
        standardSubsystemTest("providers.xml");
    }

    @Test
    public void testDisallowedProviders() throws Exception {
        KernelServices services = standardSubsystemTest("providers.xml", true);
        List<ModelNode> disallowedProviders = services.readWholeModel().get("subsystem", "elytron", "disallowed-providers").asList();
        Assert.assertNotNull(disallowedProviders);
        Assert.assertEquals(3, disallowedProviders.size());
    }

    @Test
    public void testParseAndMarshalModel_CredentialSecurityFactories() throws Exception {
        standardSubsystemTest("credential-security-factories.xml");
    }

    @Test
    public void testParseAndMarshalModel_Mappers() throws Exception {
        standardSubsystemTest("mappers.xml");
    }

    @Test
    public void testParseAndMarshalModel_Http() throws Exception {
        standardSubsystemTest("http.xml");
    }

    @Test
    public void testParseAndMarshalModel_Sasl() throws Exception {
        standardSubsystemTest("sasl.xml");
    }

    @Test
    public void testParseAndMarshalModel_Realms() throws Exception {
        standardSubsystemTest("security-realms.xml");
    }

    @Test
    public void testParseAndMarshalModel_SecurityProperties() throws Exception {
        standardSubsystemTest("security-properties.xml");
    }

    @Test
    public void testParseAndMarshalModel_Ldap() throws Exception {
        standardSubsystemTest("ldap.xml");
    }

    @Test
    public void testParseAndMarshalModel_IdentityManagement() throws Exception {
        standardSubsystemTest("identity-management.xml");
    }

    @Test
    public void testParseAndMarshalModel_CredentialStores() throws Exception {
        standardSubsystemTest("credential-stores.xml");
    }

    public void testParseAndMarshalModel_JASPI() throws Exception {
        standardSubsystemTest("jaspi.xml");
    }

    @Test
    public void testGetCredentialSourceSupplier() throws Exception {
        final KernelServices services = super.createKernelServicesBuilder(createAdditionalInitialization()).setSubsystemXml(getSubsystemXml("credential-stores.xml")).build();
        Assert.assertTrue("Subsystem boot failed!", services.isSuccessfulBoot());
        ServiceContainer container = services.getContainer();
        assert container != null;
        ServiceBuilder builder = container.addService(ServiceName.JBOSS.append("test", "credential-ref"));
        ModelNode ref = new ModelNode();
        ref.get(CredentialReference.STORE).set("test1");
        ref.get(CredentialReference.ALIAS).set("alias");
        ModelNode model = new ModelNode();
        model.get(LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE.getName()).set(ref);
        OperationContext context = new OperationContext() {
            private final Map<AttachmentKey<?>, Object> valueAttachments = new HashMap<AttachmentKey<?>, Object>();

            @Override
            public void addStep(OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addStep(OperationStepHandler step, OperationContext.Stage stage, boolean addFirst) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addStep(ModelNode operation, OperationStepHandler step, OperationContext.Stage stage, boolean addFirst) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addStep(ModelNode response, ModelNode operation, OperationStepHandler step, OperationContext.Stage stage) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addStep(ModelNode response, ModelNode operation, OperationStepHandler step, OperationContext.Stage stage, boolean addFirst) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addModelStep(OperationDefinition stepDefinition, OperationStepHandler stepHandler, boolean addFirst) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addModelStep(ModelNode response, ModelNode operation, OperationDefinition stepDefinition, OperationStepHandler stepHandler, boolean addFirst) throws IllegalArgumentException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addResponseWarning(Level level, String warning) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addResponseWarning(Level level, ModelNode warning) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public InputStream getAttachmentStream(int index) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public int getAttachmentStreamCount() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ModelNode getResult() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean hasResult() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public String attachResultStream(String mimeType, InputStream stream) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void attachResultStream(String uuid, String mimeType, InputStream stream) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ModelNode getFailureDescription() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean hasFailureDescription() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ModelNode getServerResults() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ModelNode getResponseHeaders() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void completeStep(OperationContext.RollbackHandler rollbackHandler) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void completeStep(OperationContext.ResultHandler resultHandler) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void stepCompleted() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ProcessType getProcessType() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public RunningMode getRunningMode() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isBooting() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isNormalServer() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isRollbackOnly() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void setRollbackOnly() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isRollbackOnRuntimeFailure() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isResourceServiceRestartAllowed() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void reloadRequired() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void restartRequired() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void revertReloadRequired() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void revertRestartRequired() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void runtimeUpdateSkipped() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public PathAddress getCurrentAddress() {
                return PathAddress.pathAddress(CORE_SERVICE, MANAGEMENT).append(LDAP_CONNECTION, "myLdapConnection");
            }

            @Override
            public String getCurrentAddressValue() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ImmutableManagementResourceRegistration getResourceRegistration() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ManagementResourceRegistration getResourceRegistrationForUpdate() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ImmutableManagementResourceRegistration getRootResourceRegistration() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ServiceRegistry getServiceRegistry(boolean modify) throws UnsupportedOperationException {
                return new ServiceRegistry() {
                    @Override
                    public ServiceController<?> getRequiredService(ServiceName serviceName) throws ServiceNotFoundException {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                    @Override
                    public ServiceController<?> getService(ServiceName serviceName) {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }

                    @Override
                    public List<ServiceName> getServiceNames() {
                        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
                    }
                };
            }

            @Override
            public ServiceController<?> removeService(ServiceName name) throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void removeService(ServiceController<?> controller) throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ServiceTarget getServiceTarget() throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public CapabilityServiceTarget getCapabilityServiceTarget() throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void acquireControllerLock() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource createResource(PathAddress address) throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addResource(PathAddress address, Resource toAdd) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void addResource(PathAddress address, int index, Resource toAdd) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource readResource(PathAddress relativeAddress) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource readResource(PathAddress relativeAddress, boolean recursive) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource readResourceFromRoot(PathAddress address) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource readResourceFromRoot(PathAddress address, boolean recursive) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource readResourceForUpdate(PathAddress relativeAddress) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource removeResource(PathAddress relativeAddress) throws UnsupportedOperationException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Resource getOriginalRootResource() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isModelAffected() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isResourceRegistryAffected() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isRuntimeAffected() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public OperationContext.Stage getCurrentStage() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void report(MessageSeverity severity, String message) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean markResourceRestarted(PathAddress resource, Object owner) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean revertResourceRestarted(PathAddress resource, Object owner) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ModelNode resolveExpressions(ModelNode node) throws OperationFailedException {
                return node;
            }

            @Override
            public <T> T getAttachment(OperationContext.AttachmentKey<T> key) {
                return key.cast(valueAttachments.get(key));
            }

            @Override
            public <T> T attach(OperationContext.AttachmentKey<T> key, T value) {
                return key.cast(valueAttachments.put(key, value));
            }

            @Override
            public <T> T attachIfAbsent(OperationContext.AttachmentKey<T> key, T value) {
                return key.cast(valueAttachments.put(key, value));
            }


            @Override
            public <T> T detach(OperationContext.AttachmentKey<T> key) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public AuthorizationResult authorize(ModelNode operation) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public AuthorizationResult authorize(ModelNode operation, Set<Action.ActionEffect> effects) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ResourceAuthorization authorizeResource(boolean attributes, boolean isDefaultResource) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public AuthorizationResult authorize(ModelNode operation, String attribute, ModelNode currentValue, Set<Action.ActionEffect> effects) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public AuthorizationResult authorizeOperation(ModelNode operation) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Caller getCaller() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public SecurityIdentity getSecurityIdentity() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void emit(Notification notification) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public Environment getCallEnvironment() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void registerCapability(RuntimeCapability capability) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void registerAdditionalCapabilityRequirement(String required, String dependent, String attribute) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean hasOptionalCapability(String requested, String dependent, String attribute) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void requireOptionalCapability(String required, String dependent, String attribute) throws OperationFailedException {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void deregisterCapabilityRequirement(String required, String dependent) {
                deregisterCapabilityRequirement(required, dependent, null);
            }

            @Override
            public void deregisterCapabilityRequirement(String required, String dependent, String attribute) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void deregisterCapability(String capabilityName) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public <T> T getCapabilityRuntimeAPI(String capabilityName, Class<T> apiType) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public <T> T getCapabilityRuntimeAPI(String capabilityBaseName, String dynamicPart, Class<T> apiType) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ServiceName getCapabilityServiceName(String capabilityName, Class<?> serviceType) {
               return ServiceName.parse(capabilityName);
            }

            @Override
            public ServiceName getCapabilityServiceName(String capabilityBaseName, String dynamicPart, Class<?> serviceType) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public ServiceName getCapabilityServiceName(String capabilityBaseName, Class<?> serviceType, String... dynamicParts) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public CapabilityServiceSupport getCapabilityServiceSupport() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean isDefaultRequiresRuntime() {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
        CredentialReference.getCredentialSourceSupplier(context, LdapConnectionResourceDefinition.SEARCH_CREDENTIAL_REFERENCE, model, builder);
        services.shutdown();
    }

    @Override
    protected AdditionalInitialization createAdditionalInitialization() {
        return AdditionalInitialization.MANAGEMENT;
    }


}
