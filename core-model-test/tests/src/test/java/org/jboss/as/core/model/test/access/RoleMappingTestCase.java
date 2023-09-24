/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.jboss.as.core.model.test.access;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ACCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.AUTHORIZATION;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.CORE_SERVICE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.FAILED;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.INCLUDE_ALL;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.MANAGEMENT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.NAME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OP_ADDR;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.OUTCOME;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REALM;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.RESULT;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ROLE_MAPPING;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUCCESS;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.TYPE;
import static org.jboss.as.domain.management.ModelDescriptionConstants.IS_CALLER_IN_ROLE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.security.Principal;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.jboss.as.controller.AccessAuditContext;
import org.jboss.as.controller.access.rbac.StandardRole;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.core.model.test.AbstractCoreModelTest;
import org.jboss.as.core.model.test.KernelServices;
import org.jboss.as.core.model.test.TestModelType;
import org.jboss.dmr.ModelNode;
import org.junit.Before;
import org.junit.Test;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.realm.SimpleRealmEntry;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.authz.RoleDecoder;

/**
 * Test case to test the role mapping behaviour (model and runtime mapping).
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class RoleMappingTestCase extends AbstractCoreModelTest {

    private static final String TEST_REALM = "TestRealm";
    private static final String OTHER_REALM = "OtherRealm";
    private static final String OTHER_USER = "OtherUser";

    private KernelServices kernelServices;
    private int uniqueCount = 0;

    @Before
    public void setUp() throws Exception {
        kernelServices = createKernelServicesBuilder(TestModelType.STANDALONE)
                .setXmlResource("constraints.xml")
                .validateDescription()
                .build();
    }

    /**
     * Test that a user is assigned a role based on their username (not realm specific).
     *
     * Also verify that assignment of a group with the same name does not result in role assignment.
     */
    @Test
    public void testIncludeByUsername() {
        final String roleName = "Deployer";
        final String userName = "UserOne";
        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null);
        assertIsCallerInRole(roleName, false, OTHER_USER, TEST_REALM, null, userName);

        removeRole(roleName);
    }

    /**
     * Test that a user is assigned a role based on their group membership (not realm specific).
     *
     * Also verify that a user account with the same name does not result in role assignment.
     */
    @Test
    public void testIncludeByGroup() {
        final String roleName = "Deployer";
        final String userName = "UserThree";
        final String groupName = "GroupThree";
        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.GROUP, groupName, null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null, groupName);
        assertIsCallerInRole(roleName, true, userName, OTHER_REALM, null, groupName);
        assertIsCallerInRole(roleName, false, groupName, TEST_REALM, null, userName);

        removeRole(roleName);
    }

    /**
     * Test that a user matched to a role by group is not assigned the role if their username is in the exclude list.
     */
    @Test
    public void testExcludeByUsername() {
        final String roleName = "Deployer";
        final String userName = "UserFive";
        final String groupName = "GroupFive";
        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.GROUP, groupName, null);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, userName, null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, OTHER_USER, TEST_REALM, null, groupName);
        assertIsCallerInRole(roleName, false, userName, TEST_REALM, null, groupName);

        removeRole(roleName);
    }

    /**
     * Test that a user assigned a role due to group membership is excluded based on the membership of another group.
     */
    @Test
    public void testExcludeByGroup() {
        final String roleName = "Deployer";
        final String userName = "UserSix";
        final String inGroupName = "GroupSix_In";
        final String outGroupName = "GroupSix_Out";
        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.GROUP, inGroupName, null);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.GROUP, outGroupName, null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null, inGroupName);
        assertIsCallerInRole(roleName, false, userName, TEST_REALM, null, inGroupName, outGroupName);

        removeRole(roleName);
    }

    /**
     * Test that user assigned the SUPERUSER role can actually request a different role.
     *
     * On requesting the different role the user should not be assigned the SUPERUSER role anymore.
     */
    @Test
    public void testSuperUserAs() {
        final String roleName = "SuperUser";
        final String otherRole = "Deployer";
        final String userName = "UserThirteen";
        // TODO Elytron The SuperUser mapping was added to constraints.xml to allow the remainder of the tests to run.
        //addRole(roleName, false);
        ModelNode addedAddress = addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, null);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null);
        assertIsCallerInRole(otherRole, true, userName, TEST_REALM, otherRole);
        assertIsCallerInRole(roleName, false, userName, TEST_REALM, otherRole);

        removePrincipal(addedAddress);
        // TODO Elytron The SuperUser mapping was added to constraints.xml to allow the remainder of the tests to run.
        //removeRole(roleName);
    }

    /**
     * Test that user assigned the Deployer role can NOT request a different role.
     */
    @Test
    public void testDeployerAs() {
        final String roleName = "Deployer";
        final String otherRole = "MONITOR";
        final String userName = "UserFourteen";
        addRole(roleName, false);
        ModelNode addedAddress = addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, null);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null);
        assertIsCallerInRole(otherRole, false, userName, TEST_REALM, otherRole);
        assertIsCallerInRole(roleName, true, userName, TEST_REALM, otherRole);

        removePrincipal(addedAddress);
        removeRole(roleName);
    }

    /**
     * Test that an authenticated user is assigned a role where include-all = true.
     */
    @Test
    public void testIncludeAll() {
        final String roleName = "Deployer";
        final String userName = "UserEight";
        addRole(roleName, true);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null);

        removeRole(roleName);
    }

    /**
     * Test that a user matched to a role by include-all is not assigned the role if their username is in the exclude list.
     */
    @Test
    public void testIncludeAll_ExcludeByUsername() {
        final String roleName = "Deployer";
        final String userName = "UserNine";
        final String groupName = "GroupNine";
        addRole(roleName, true);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, userName, null);
        // TODO Elytron Hack to also exclude the default user 'anonymous'.
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, "anonymous", null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, OTHER_USER, TEST_REALM, null, groupName);
        assertIsCallerInRole(roleName, false, userName, TEST_REALM, null, groupName);

        removeRole(roleName);
    }

    /**
     * Test that a user matched to a role by include-all is not assigned the role if their group is in the exclude list.
     */
    @Test
    public void testIncludeAll_ExcludeByGroup() {
        final String roleName = "Deployer";
        final String userName = "UserTen";
        final String groupName = "GroupTen";
        addRole(roleName, true);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.GROUP, groupName, null);
        // TODO Elytron Hack to also exclude the default user 'anonymous'.
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, "anonymous", null);
        assertIsCallerInRole(roleName, null, false);

        assertIsCallerInRole(roleName, true, userName, TEST_REALM, null);
        assertIsCallerInRole(roleName, false, userName, TEST_REALM, null, groupName);

        removeRole(roleName);
    }

    /*
     * Duplicate Handling
     *
     * Tests to verify that the add operations successfully detect duplicate include/exclude definitions.
     */

    @Test
    public void testDuplicateUserComplete() {
        final String roleName = "Deployer";
        final String userName = "UserEleven";

        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, TEST_REALM);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, TEST_REALM, true);

        removeRole(roleName);
    }

    @Test
    public void testDuplicateUserRealmLess() {
        final String roleName = "Deployer";
        final String userName = "UserTwelve";

        addRole(roleName, false);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, null);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, TEST_REALM);
        addPrincipal(roleName, MappingType.INCLUDE, PrincipalType.USER, userName, null, true);

        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, userName, TEST_REALM);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, userName, null);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.USER, userName, null, true);

        removeRole(roleName);
    }

    @Test
    public void testDuplicateGroupComplete() {
        final String roleName = "Deployer";
        final String groupName = "UserThirteen";

        addRole(roleName, false);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.GROUP, groupName, TEST_REALM);
        addPrincipal(roleName, MappingType.EXCLUDE, PrincipalType.GROUP, groupName, TEST_REALM, true);

        removeRole(roleName);
    }

    private void addRole(final String roleName, boolean includeAll) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION).add(ROLE_MAPPING, roleName);
        operation.get(OP).set(ADD);
        if (includeAll) {
            operation.get(INCLUDE_ALL).set(true);
        }

        ModelNode response = kernelServices.executeOperation(operation);
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
    }

    private ModelNode addPrincipal(final String roleName, final MappingType mappingType, final PrincipalType principalType, final String name, final String realm) {
        return addPrincipal(roleName, mappingType, principalType, name, realm, false);
    }

    private ModelNode addPrincipal(final String roleName, final MappingType mappingType, final PrincipalType principalType, final String name, final String realm, boolean expectFailure) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION).add(ROLE_MAPPING, roleName).add(mappingType.toString(), uniqueCount++);
        operation.get(OP).set(ADD);
        operation.get(TYPE).set(principalType.toString());
        operation.get(NAME).set(name);
        if (realm != null) {
            operation.get(REALM).set(realm);
        }

        ModelNode response = kernelServices.executeOperation(operation);
        if (expectFailure) {
            assertEquals(FAILED, response.get(OUTCOME).asString());
        } else {
            assertEquals(SUCCESS, response.get(OUTCOME).asString());
        }

        return operation.get(OP_ADDR);
    }

    private void removePrincipal(final ModelNode address) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).set(address);
        operation.get(OP).set(REMOVE);

        ModelNode response = kernelServices.executeOperation(operation);
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
    }

    private void removeRole(final String roleName) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION).add(ROLE_MAPPING, roleName);
        operation.get(OP).set(REMOVE);

        ModelNode response = kernelServices.executeOperation(operation);
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
    }

    private void assertIsCallerInRole(final String roleName, final String runAsRole, final boolean expectedOutcome) {
        ModelNode operation = new ModelNode();
        operation.get(OP_ADDR).add(CORE_SERVICE, MANAGEMENT).add(ACCESS, AUTHORIZATION).add(ROLE_MAPPING, roleName);
        operation.get(OP).set(IS_CALLER_IN_ROLE);
        if (runAsRole != null) {
            ModelNode headers = operation.get(ModelDescriptionConstants.OPERATION_HEADERS);
            headers.get("roles").set(runAsRole);
        }

        ModelNode response = kernelServices.executeOperation(operation);
        assertEquals(SUCCESS, response.get(OUTCOME).asString());
        assertEquals(expectedOutcome, response.get(RESULT).asBoolean());
    }

    private void assertIsCallerInRole(final String roleName, final boolean expectedOutcome, final String userName,
            final String realm, final String runAsRole, final String... groups)  {
        MapAttributes testAttributes = new MapAttributes();
        testAttributes.addAll("groups", Arrays.asList(groups));

        Map<String, SimpleRealmEntry> entries = new HashMap<>(StandardRole.values().length);
        entries.put(userName, new SimpleRealmEntry(Collections.emptyList(), testAttributes));

        SimpleMapBackedSecurityRealm securityRealm = new SimpleMapBackedSecurityRealm() {

            @Override
            public RealmIdentity getRealmIdentity(Principal principal) {
                return super.getRealmIdentity(new NamePrincipal(principal.getName()));
            }

        };
        securityRealm.setPasswordMap(entries);

        SecurityDomain testDomain = SecurityDomain.builder()
                .setDefaultRealmName("Default")
                //.setPreRealmRewriter((Function<Principal, Principal>) p -> new RealmUser(realm, p.getName()))
                .addRealm("Default", securityRealm)
                    .setRoleDecoder(RoleDecoder.simple("groups"))
                    .build()
                .setPermissionMapper((p,r) -> new LoginPermission())
                .build();

        SecurityIdentity securityIdentity;
        try {
            ServerAuthenticationContext authenticationContext = testDomain.createNewAuthenticationContext();
            authenticationContext.setAuthenticationName(userName);
            assertTrue("Authorized", authenticationContext.authorize());
            securityIdentity = authenticationContext.getAuthorizedIdentity();
        } catch (RealmUnavailableException e) {
            // Should not be possible
            throw new IllegalStateException(e);
        }

        AccessAuditContext.doAs(securityIdentity, null, new PrivilegedAction<Void>() {

            @Override
            public Void run() {
                assertIsCallerInRole(roleName, runAsRole, expectedOutcome);
                return null;
            }
        });
    }

    private enum PrincipalType {
        GROUP, USER;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }
    }

    private enum MappingType {
        EXCLUDE, INCLUDE;

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

    }

    private static class User {

        private final String realm;
        private final String name;

        private User(final String name, final String realm) {
            this.name = name;
            this.realm = realm;
        }

        public String getName() {
            return name;
        }

        public String getRealm() {
            return realm;
        }

    }

}
