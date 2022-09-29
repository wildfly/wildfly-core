/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2016, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

import org.jboss.as.controller.capability.RuntimeCapability;
import org.jboss.as.controller.capability.registry.CapabilityScope;
import org.jboss.as.controller.capability.registry.RegistrationPoint;
import org.jboss.as.controller.capability.registry.RuntimeCapabilityRegistration;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.PROFILE;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_CONFIG;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SERVER_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SOCKET_BINDING_GROUP;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.SUBSYSTEM;
import org.jboss.as.controller.registry.Resource;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;

/**
 * Test that the capabilities that are reachable from the dependent scope are
 * actually reachable and suggested as possible capability.Test emulate an Host
 * controller context. This is the more complex config.
 * global               capabilities are accessible from any capability
 * server-group         capabilities are accessible from server-config
 * server-config        capabilities are accessible from server-config
 * host                 capabilities are accessible from host and global
 * s-binding-grp        capabilities are accessible from socket-binding, server-config and server-group
 * s-binding-grp-child  capabilities are accessible from same child scope, from included scope or from any scope !profile and !server-groups
 * profiles             capabilities are accessible from profiles or server-group
 * profiles-child       capabilities are accessible from same child scope or from included profile scope
 *
 * @author jdenise@redhat.com
 */
public class SuggestCapabilitiesTestCase {

    private static final String HOST = "host";

    private static final PathElement PRIMARY_HOST = PathElement.pathElement(HOST, "primary");

    // global
    private static final String GLOBAL = "global";
    private static final String GLOBAL_CAPABILITY_STATIC_NAME = "org.wildfly.global";
    private static final PathAddress GLOBAL_ALL = PathAddress.pathAddress(GLOBAL, "*");

    // server group
    private static final String SG_CAPABILITY_STATIC_NAME = "org.wildfly.sg";
    private static final PathAddress SG_ALL = PathAddress.pathAddress(SERVER_GROUP, "*");

    // server config
    private static final String SC_CAPABILITY_STATIC_NAME = "org.wildfly.sc";
    private static final PathAddress SC_ALL = PathAddress.pathAddress(PRIMARY_HOST,
            PathElement.pathElement(SERVER_CONFIG, "*"));

    // host
    private static final String HOST_CAPABILITY_STATIC_NAME = "org.wildfly.host";
    private static final PathAddress HOST_ALL = PathAddress.pathAddress(PRIMARY_HOST,
            PathElement.pathElement(SUBSYSTEM, "*"));

    // socket-binding-group
    private static final String SBG_CAPABILITY_STATIC_NAME = "org.wildfly.sbg";
    private static final PathAddress SBG_ALL
            = PathAddress.pathAddress(SOCKET_BINDING_GROUP, "*");

    // socket-binding-group-child
    private static final String SBG_CHILD_CAPABILITY_STATIC_NAME = "org.wildfly.sbg.child";
    private static final PathAddress SBG_CHILD_ALL
            = PathAddress.pathAddress(SBG_ALL, PathElement.pathElement("somewhere", "*"));

    // profile
    private static final String PROFILE_CAPABILITY_STATIC_NAME = "org.wildfly.profile";
    private static final PathAddress PROFILE_ALL
            = PathAddress.pathAddress(PROFILE, "*");

    // profile-child
    private static final String PROFILE_CHILD_CAPABILITY_STATIC_NAME = "org.wildfly.profile.child";
    private static final PathAddress PROFILE_CHILD_ALL
            = PathAddress.pathAddress(PROFILE_ALL, PathElement.pathElement("somewhere", "*"));

    private Set<String> globals;
    private Set<String> sgs;
    private Set<String> scs;
    private Set<String> hosts;
    private Set<String> sbgs;
    private Set<String> sbgsChild;
    private Set<String> profiles;
    private Set<String> profilesChild;

    private CapabilityRegistry reg = new CapabilityRegistry(false);

    private void registerPossible(CapabilityRegistry reg, String cap, PathAddress address) {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(cap, true).build();
        reg.registerPossibleCapability(capability, address);
    }

    private void registerCapability(CapabilityRegistry reg, String baseName, String dynamicPart, PathAddress address) {
        RuntimeCapability<Void> capability = RuntimeCapability.Builder.of(baseName, true).build();
        capability = capability.fromBaseCapability(dynamicPart);
        CapabilityScope scope = CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER, address);
        RegistrationPoint rp = new RegistrationPoint(address, null);
        reg.registerCapability(new RuntimeCapabilityRegistration(capability, scope, rp));
    }

    @Before
    public void setup() {
        reg.clear();
        // Register all possibles.
        registerPossible(reg, GLOBAL_CAPABILITY_STATIC_NAME, GLOBAL_ALL);
        registerPossible(reg, SG_CAPABILITY_STATIC_NAME, SG_ALL);
        registerPossible(reg, SC_CAPABILITY_STATIC_NAME, SC_ALL);
        registerPossible(reg, HOST_CAPABILITY_STATIC_NAME, HOST_ALL);
        registerPossible(reg, SBG_CAPABILITY_STATIC_NAME, SBG_ALL);
        registerPossible(reg, SBG_CHILD_CAPABILITY_STATIC_NAME, SBG_CHILD_ALL);
        registerPossible(reg, PROFILE_CAPABILITY_STATIC_NAME, PROFILE_ALL);
        registerPossible(reg, PROFILE_CHILD_CAPABILITY_STATIC_NAME, PROFILE_CHILD_ALL);

        // Register some concrete ones.
        globals = registerMultipleCapabilities(reg, GLOBAL_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(GLOBAL, "somewhere" + i));
        sgs = registerMultipleCapabilities(reg, SG_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(SERVER_GROUP, "ser:ver" + i));
        scs = registerMultipleCapabilities(reg, SC_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(PRIMARY_HOST,
                        PathElement.pathElement(SERVER_CONFIG, "conf" + i)));
        hosts = registerMultipleCapabilities(reg, HOST_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(PRIMARY_HOST,
                        PathElement.pathElement(SUBSYSTEM, "susbsystem" + i)));
        sbgs = registerMultipleCapabilities(reg, SBG_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(SOCKET_BINDING_GROUP, "socket" + i));
        sbgsChild = registerMultipleCapabilities(reg, SBG_CHILD_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "grp" + i),
                        PathElement.pathElement("somewhere", "child" + i)));
        profiles = registerMultipleCapabilities(reg, PROFILE_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(PROFILE, "profile" + i));
        profilesChild = registerMultipleCapabilities(reg, PROFILE_CHILD_CAPABILITY_STATIC_NAME,
                (i) -> PathAddress.pathAddress(PathAddress.pathAddress(PROFILE, "profile" + i),
                        PathElement.pathElement("somewhere", "ch:ild" + i)));

        reg.resolveCapabilities(Resource.Factory.create(), false);

    }

    private Set<String> registerMultipleCapabilities(CapabilityRegistry reg,
            String cap, Function<Integer, PathAddress> address) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i < 3; i++) {
            String name = "cap-" + i;
            registerCapability(reg, cap, name, address.apply(i));
            set.add(name);
        }
        return set;
    }

    private Set<String> suggestFromGlobal(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress("somewhere", "toto")));
    }

    private Set<String> suggestFromServerGroup(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(SERVER_GROUP, "toto")));
    }

    private Set<String> suggestFromServerConfig(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PRIMARY_HOST,
                                PathElement.pathElement(ModelDescriptionConstants.SERVER_CONFIG, "toto"))));
    }

    private Set<String> suggestFromHost(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PRIMARY_HOST,
                                PathElement.pathElement(SUBSYSTEM, "toto"))));
    }

    private Set<String> suggestFromSocketBindingGroup(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(SOCKET_BINDING_GROUP, "toto")));
    }

    private Set<String> suggestFromSocketBindingGroupChild(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "b1"),
                                PathElement.pathElement("somewhere", "toto"))));
    }

    private Set<String> suggestFromProfile(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PROFILE, "toto")));
    }

    private Set<String> suggestFromProfileChild(String cap) {
        return reg.getDynamicCapabilityNames(cap,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PathAddress.pathAddress(PROFILE, "toto"),
                                PathElement.pathElement("somewhere", "there"))));
    }

    @Test
    public void testGlobalCapability() {
        {
            Set<String> ret = suggestFromGlobal(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }
        {
            Set<String> ret = suggestFromServerGroup(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);

        }
        {
            Set<String> ret = suggestFromServerConfig(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }
        {
            Set<String> ret = suggestFromHost(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }

        {
            Set<String> ret = suggestFromProfile(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }

        {
            Set<String> ret = suggestFromProfileChild(GLOBAL_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(globals, ret);
        }
    }

    @Test
    public void testServerGroupCapability() {
        {
            Set<String> ret = suggestFromGlobal(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromServerGroup(SG_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sgs, ret);

        }
        {
            Set<String> ret = suggestFromServerConfig(SG_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sgs, ret);
        }
        {
            Set<String> ret = suggestFromHost(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfileChild(SG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
    }

    @Test
    public void testServerConfigCapability() {
        {
            Set<String> ret = suggestFromGlobal(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromServerGroup(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());

        }
        {
            Set<String> ret = suggestFromServerConfig(SC_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(scs, ret);
        }
        {
            Set<String> ret = suggestFromHost(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfileChild(SC_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
    }

    @Test
    public void testHostCapability() {
        {
            Set<String> ret = suggestFromGlobal(HOST_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(hosts, ret);
        }
        {
            Set<String> ret = suggestFromServerGroup(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());

        }
        {
            Set<String> ret = suggestFromServerConfig(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromHost(HOST_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(hosts, ret);
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfileChild(HOST_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
    }

    @Test
    public void testSBGCapability() {
        {
            Set<String> ret = suggestFromGlobal(SBG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromServerGroup(SBG_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgs, ret);

        }
        {
            Set<String> ret = suggestFromServerConfig(SBG_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgs, ret);
        }
        {
            Set<String> ret = suggestFromHost(SBG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(SBG_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgs, ret);
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(SBG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(SBG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfileChild(SBG_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
    }

    @Test
    public void testSBGChildCapability() {
        {
            Set<String> ret = suggestFromGlobal(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgsChild, ret);
        }
        {
            Set<String> ret = suggestFromServerGroup(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());

        }
        {
            Set<String> ret = suggestFromServerConfig(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgsChild, ret);
        }
        {
            Set<String> ret = suggestFromHost(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgsChild, ret);
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgsChild, ret);
        }

        {
            // Because not called from same binding group
            Set<String> ret = suggestFromSocketBindingGroupChild(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = reg.getDynamicCapabilityNames(SBG_CHILD_CAPABILITY_STATIC_NAME,
                    CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                            PathAddress.pathAddress(PathAddress.pathAddress(SOCKET_BINDING_GROUP, "grp1"),
                                    PathElement.pathElement("somewhere", "toto"))));
            assertTrue(ret.size() == 1);
            assertTrue(ret.toString(), ret.contains("cap-1"));
        }

        {
            Set<String> ret = suggestFromProfile(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfileChild(SBG_CHILD_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(sbgsChild, ret);
        }
    }

    @Test
    public void testProfileCapability() {
        {
            Set<String> ret = suggestFromGlobal(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromServerGroup(PROFILE_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(profiles, ret);

        }
        {
            Set<String> ret = suggestFromServerConfig(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromHost(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(PROFILE_CAPABILITY_STATIC_NAME);
            assertFalse(ret.isEmpty());
            assertEquals(profiles, ret);
        }

        {
            Set<String> ret = suggestFromProfileChild(PROFILE_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
    }

    @Test
    public void testProfileChildCapability() {
        {
            Set<String> ret = suggestFromGlobal(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromServerGroup(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());

        }
        {
            Set<String> ret = suggestFromServerConfig(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromHost(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }
        {
            Set<String> ret = suggestFromSocketBindingGroup(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromSocketBindingGroupChild(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = suggestFromProfile(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            // Because not called from same child
            Set<String> ret = suggestFromProfileChild(PROFILE_CHILD_CAPABILITY_STATIC_NAME);
            assertTrue(ret.isEmpty());
        }

        {
            Set<String> ret = reg.getDynamicCapabilityNames(PROFILE_CHILD_CAPABILITY_STATIC_NAME,
                CapabilityScope.Factory.create(ProcessType.HOST_CONTROLLER,
                        PathAddress.pathAddress(PathAddress.pathAddress(PROFILE, "profile2"),
                                PathElement.pathElement("somewhere", "there"))));
            assertTrue(ret.size() == 1);
            assertTrue(ret.toString(), ret.contains("cap-2"));
        }
    }
}
