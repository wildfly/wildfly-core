/*
 * JBoss, Home of Professional Open Source
 *
 * Copyright 2015 Red Hat, Inc. and/or its affiliates.
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

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ALGORITHM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ATTRIBUTES;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.NAME;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.REALM;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ROLES;

import java.util.concurrent.ThreadLocalRandom;

import org.jboss.as.controller.ObjectTypeAttributeDefinition;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.SimpleOperationDefinitionBuilder;
import org.jboss.as.subsystem.test.AbstractSubsystemTest;
import org.jboss.as.subsystem.test.AdditionalInitialization;
import org.jboss.as.subsystem.test.KernelServices;
import org.jboss.as.subsystem.test.KernelServicesBuilder;
import org.jboss.as.subsystem.test.SubsystemOperations;
import org.jboss.dmr.ModelNode;
import org.junit.Test;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.authz.PermissionMappable;
import org.wildfly.security.authz.PermissionMapper;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.authz.Roles;
import org.wildfly.security.password.interfaces.BCryptPassword;
import org.wildfly.security.password.interfaces.DigestPassword;
import org.wildfly.security.password.interfaces.OneTimePassword;
import org.wildfly.security.password.interfaces.SaltedSimpleDigestPassword;
import org.wildfly.security.password.interfaces.ScramDigestPassword;
import org.wildfly.security.password.interfaces.SimpleDigestPassword;
import org.wildfly.security.permission.PermissionVerifier;

/**
 * @author <a href="mailto:psilva@redhat.com">Pedro Igor</a>
 */
public class IdentityOperationsTestCase extends AbstractSubsystemTest {

    public IdentityOperationsTestCase() {
        super(ElytronExtension.SUBSYSTEM_NAME, new ElytronExtension());
    }

    @Override
    protected KernelServicesBuilder createKernelServicesBuilder(AdditionalInitialization additionalInit) {
        return super.createKernelServicesBuilder(new TestEnvironment());
    }

    @Test
    public void testCreateIdentity() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        ModelNode operation = createAddIdentityOperation(getSecurityRealmAddress("FileSystemRealm"), principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testDeleteIdentity() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createRemoveIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertFail(result);
    }

    @Test
    public void testReadSecurityDomainIdentity() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();

        TestEnvironment.activateService(services, Capabilities.SECURITY_DOMAIN_RUNTIME_CAPABILITY, "FileSystemDomain");

        PathAddress securityDomainAddress = getSecurityDomainAddress("FileSystemDomain");
        String principalName = "plainUser";
        ModelNode operation = createAddIdentityOperation(getSecurityRealmAddress("FileSystemRealm"), principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadSecurityDomainIdentityOperation(securityDomainAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        ModelNode resultNode = result.get(RESULT);

        assertEquals(principalName, resultNode.get(NAME).asString());
        assertFalse(resultNode.get(ATTRIBUTES).isDefined());
        assertFalse(resultNode.get(ROLES).isDefined());
    }

    @Test
    public void testReadIdentity() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        ModelNode resultNode = result.get(RESULT);

        assertEquals(principalName, resultNode.get(NAME).asString());
        assertFalse(resultNode.get(ATTRIBUTES).isDefined());
        assertFalse(resultNode.get(ROLES).isDefined());
    }

    @Test
    public void testAddAttribute() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);

        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "firstName", "John");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "lastName", "Smith");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, RoleDecoder.KEY_ROLES, "Admin", "Manager", "Employee");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        ModelNode resultNode = result.get(RESULT);
        ModelNode attributesNode = resultNode.get(ATTRIBUTES);

        assertTrue(attributesNode.isDefined());
        assertAttributeValue(attributesNode, "firstName", "John");
        assertAttributeValue(attributesNode, "lastName", "Smith");
        assertAttributeValue(attributesNode, RoleDecoder.KEY_ROLES, "Admin", "Manager", "Employee");

        operation = createAddAttributeOperation(realmAddress, principalName, "lastName", "Silva");
        result = services.executeOperation(operation);
        assertSuccessful(result);
        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        resultNode = result.get(RESULT);
        attributesNode = resultNode.get(ATTRIBUTES);

        assertTrue(attributesNode.isDefined());
        assertAttributeValue(attributesNode, "lastName", "Smith", "Silva");

    }

    @Test
    public void testAddEmptyAttributeValue() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);

        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "name", "John Smith");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "phoneNumber", "");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        ModelNode resultNode = result.get(RESULT);
        ModelNode attributesNode = resultNode.get(ATTRIBUTES);

        assertTrue(attributesNode.isDefined());
        assertAttributeValue(attributesNode, "name", "John Smith");
        assertAttributeValue(attributesNode, "phoneNumber", "");
    }

    @Test
    public void testRemoveAttribute() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);

        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "firstName", "John");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, "lastName", "Smith");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createAddAttributeOperation(realmAddress, principalName, RoleDecoder.KEY_ROLES, "Admin", "Manager", "Employee");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        ModelNode resultNode = result.get(RESULT);
        ModelNode attributesNode = resultNode.get(ATTRIBUTES);

        assertTrue(attributesNode.isDefined());
        assertAttributeValue(attributesNode, "firstName", "John");
        assertAttributeValue(attributesNode, "lastName", "Smith");
        assertAttributeValue(attributesNode, RoleDecoder.KEY_ROLES, "Admin", "Manager", "Employee");

        operation = createRemoveAttributeOperation(realmAddress, principalName, "lastName");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createRemoveAttributeOperation(realmAddress, principalName, RoleDecoder.KEY_ROLES, "Employee", "Manager");
        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createReadIdentityOperation(realmAddress, principalName);
        result = services.executeOperation(operation);
        assertSuccessful(result);

        resultNode = result.get(RESULT);
        attributesNode = resultNode.get(ATTRIBUTES);

        assertTrue(attributesNode.isDefined());
        assertAttributeValue(attributesNode, "firstName", "John");
        assertFalse(attributesNode.get("lastName").isDefined());
        assertAttributeValue(attributesNode, RoleDecoder.KEY_ROLES, "Admin");
    }

    @Test
    public void testBcryptPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        byte[] salt = generateRandomSalt(BCryptPassword.BCRYPT_SALT_SIZE);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Bcrypt.OBJECT_DEFINITION, "bcryptPassword", salt, 10, null, null, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testClearPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Clear.OBJECT_DEFINITION, "clearPassword", null, null, null, null, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testSimpleDigestPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.SimpleDigest.OBJECT_DEFINITION, "simpleDigest", null, null, null, SimpleDigestPassword.ALGORITHM_SIMPLE_DIGEST_SHA_1, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testSaltedSimpleDigestPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.SaltedSimpleDigest.OBJECT_DEFINITION, "saltedSimpleDigest", generateRandomSalt(16), null, null, SaltedSimpleDigestPassword.ALGORITHM_PASSWORD_SALT_DIGEST_SHA_256, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testScramDigestPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        byte[] salt = generateRandomSalt(ScramDigestPassword.DEFAULT_SALT_SIZE);
        int iterationCount = ScramDigestPassword.DEFAULT_ITERATION_COUNT;

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.ScramDigest.OBJECT_DEFINITION, "scramPassword", salt, iterationCount, null, null, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testDigestPassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Digest.OBJECT_DEFINITION, "digestPassword", null, null, "Elytron Realm", DigestPassword.ALGORITHM_DIGEST_MD5, null, null);

        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testOneTimePassword() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.OTPassword.OBJECT_DEFINITION, "pass123", null, null, "Elytron Realm", OneTimePassword.ALGORITHM_OTP_MD5, "fghi", 123);

        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testSetMultipleCredentials() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Digest.OBJECT_DEFINITION, "digestPassword", null, null, "Elytron Realm", DigestPassword.ALGORITHM_DIGEST_MD5, null, null);

        result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Clear.OBJECT_DEFINITION, "clearPassword", null, null, null, null, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    @Test
    public void testUnsetCredentials() throws Exception {
        KernelServices services = createKernelServicesBuilder(null)
                .setSubsystemXmlResource("identity-management.xml")
                .build();
        String principalName = "plainUser";
        PathAddress realmAddress = getSecurityRealmAddress("FileSystemRealm");
        ModelNode operation = createAddIdentityOperation(realmAddress, principalName);
        ModelNode result = services.executeOperation(operation);
        assertSuccessful(result);

        operation = createSetPasswordOperation("default", realmAddress, principalName,
                ModifiableRealmDecorator.SetPasswordHandler.Clear.OBJECT_DEFINITION, "clearPassword", null, null, null, null, null, null);
        result = services.executeOperation(operation);
        assertSuccessful(result);
    }

    private void assertSuccessful(ModelNode result) {
        assertEquals(SUCCESS, result.get(OUTCOME).asString());
    }

    private void assertFail(ModelNode result) {
        assertEquals(FAILED, result.get(OUTCOME).asString());
    }

    private void assertAttributeValue(ModelNode modelNode, String name, String... expectedValues) {
        for (String expectedValue : expectedValues) {
            boolean hasValue = false;

            for (ModelNode value : modelNode.get(name).asList()) {
                if (value.asString().equals(expectedValue)) {
                    hasValue = true;
                    break;
                }
            }

            assertTrue(hasValue);
        }
    }

    private ModelNode createAddIdentityOperation(PathAddress parentAddress, String principalName) {
        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.ADD_IDENTITY, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.AddIdentityHandler.IDENTITY, principalName)
                .build();
    }

    private ModelNode createRemoveIdentityOperation(PathAddress parentAddress, String principalName) {
        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.REMOVE_IDENTITY, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.RemoveIdentityHandler.IDENTITY, principalName)
                .build();
    }

    private ModelNode createReadIdentityOperation(PathAddress parentAddress, String principalName) {
        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.READ_IDENTITY, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.RemoveIdentityHandler.IDENTITY, principalName)
                .build();
    }

    private ModelNode createAddAttributeOperation(PathAddress parentAddress, String principalName, String key, String... values) {
        ModelNode valuesNode = new ModelNode();

        for (String value : values) {
            valuesNode.add(value);
        }

        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.ADD_IDENTITY_ATTRIBUTE, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.AddIdentityAttributeHandler.IDENTITY, principalName)
                .addAttribute(ModifiableRealmDecorator.AddIdentityAttributeHandler.NAME, key)
                .addAttribute(ModifiableRealmDecorator.AddIdentityAttributeHandler.VALUE, valuesNode)
                .build();
    }

    private ModelNode createRemoveAttributeOperation(PathAddress parentAddress, String principalName, String key, String... values) {
        ModelNode valuesNode = new ModelNode();

        for (String value : values) {
            valuesNode.add(value);
        }

        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.REMOVE_IDENTITY_ATTRIBUTE, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.RemoveIdentityAttributeHandler.IDENTITY, principalName)
                .addAttribute(ModifiableRealmDecorator.RemoveIdentityAttributeHandler.NAME, key)
                .addAttribute(ModifiableRealmDecorator.RemoveIdentityAttributeHandler.VALUE, valuesNode)
                .build();
    }

    private ModelNode createSetPasswordOperation(String credentialName, PathAddress parentAddress, String principalName, ObjectTypeAttributeDefinition passwordDefinition, String password, byte[] salt, Integer iterationCount, String realm, String algorithm, String seed, Integer sequence) {
        ModelNode passwordNode = new ModelNode();

        passwordNode.get(ElytronDescriptionConstants.NAME).set(credentialName);
        passwordNode.get(ElytronDescriptionConstants.PASSWORD).set(password);

        if (salt != null) {
            passwordNode.get(ElytronDescriptionConstants.SALT).set(salt);
        }

        if (iterationCount != null) {
            passwordNode.get(ElytronDescriptionConstants.ITERATION_COUNT).set(iterationCount);
        }

        if (algorithm != null) {
            passwordNode.get(ALGORITHM).set(algorithm);
        }

        if (realm != null) {
            passwordNode.get(REALM).set(realm);
        }

        if (seed != null) {
            passwordNode.get(ElytronDescriptionConstants.SEED).set(seed);
        }

        if (sequence != null) {
            passwordNode.get(ElytronDescriptionConstants.SEQUENCE).set(sequence);
        }

        return SubsystemOperations.OperationBuilder.create(ElytronDescriptionConstants.SET_PASSWORD, parentAddress.toModelNode())
                .addAttribute(ModifiableRealmDecorator.SetPasswordHandler.IDENTITY, principalName)
                .addAttribute(passwordDefinition, passwordNode)
                .build();
    }

    private ModelNode createReadSecurityDomainIdentityOperation(PathAddress parentAddress, String principalName) {
        return SubsystemOperations.OperationBuilder.create(SimpleOperationDefinitionBuilder.of(ElytronDescriptionConstants.READ_IDENTITY, ElytronExtension.getResourceDescriptionResolver(ElytronDescriptionConstants.SECURITY_DOMAIN)).build(),
                parentAddress.toModelNode())
                .addAttribute(DomainDefinition.ReadSecurityDomainIdentityHandler.NAME, principalName)
                .build();
    }

    private PathAddress getSecurityDomainAddress(String securityDomain) {
        return PathAddress.pathAddress(ElytronExtension.SUBSYSTEM_PATH,
                PathElement.pathElement(ElytronDescriptionConstants.SECURITY_DOMAIN, securityDomain));
    }

    private PathAddress getSecurityRealmAddress(String securityRealm) {
        return PathAddress.pathAddress(ElytronExtension.SUBSYSTEM_PATH, PathElement.pathElement(ElytronDescriptionConstants.FILESYSTEM_REALM, securityRealm));
    }

    public static class LoginPermissionMapper implements PermissionMapper {

        @Override
        public PermissionVerifier mapPermissions(PermissionMappable permissionMappable, Roles roles) {
            return PermissionVerifier.from(new LoginPermission());
        }
    }

    private static byte[] generateRandomSalt(int saltSize) {
        byte[] randomSalt = new byte[saltSize];
        ThreadLocalRandom.current().nextBytes(randomSalt);
        return randomSalt;
    }
}
