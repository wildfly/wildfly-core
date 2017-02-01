/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.remote;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.security.Principal;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jboss.as.controller.remote.IdentityAddressProtocolUtil.PropagatedIdentity;
import org.jboss.as.core.security.RealmUser;
import org.jboss.as.core.security.api.RealmPrincipal;
import org.junit.Test;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.realm.SimpleMapBackedSecurityRealm;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.password.interfaces.ClearPassword;

/**
 * Test case for the utility to write and read identities and inet addresses.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
public class IdentityAddressProtocolUtilTestCase {

    @Test
    public void testSupportedTypes() throws Exception {
        Set<String> groups = new HashSet<>();
        groups.add("GroupOne");
        groups.add("GroupTwo");

        MapAttributes mapAttributes = new MapAttributes();
        mapAttributes.addAll("GROUPS", groups);

        SimpleMapBackedSecurityRealm smbsr = new SimpleMapBackedSecurityRealm();
        smbsr.setPasswordMap("TestUser", ClearPassword.createRaw(ClearPassword.ALGORITHM_CLEAR, "password".toCharArray()), mapAttributes);

        SecurityDomain testDomain = SecurityDomain.builder()
                                        .setDefaultRealmName("Test")
                                        .addRealm("Test", smbsr)
                                            .setRoleDecoder(RoleDecoder.simple("GROUPS"))
                                            .setPrincipalRewriter(p -> new NamePrincipal(p.getName()))
                                            .build()
                                        .setPreRealmRewriter((Function<Principal, Principal>) p -> new RealmUser("TestRealm", p.getName()))
                                        .setPermissionMapper((permissionMappable, roles) -> LoginPermission.getInstance())
                                        .build();

        InetAddress testAddress = InetAddress.getByAddress("localhost", new byte[] { 0x7F, 0x00, 0x00, 0x01 });

        ServerAuthenticationContext serverAuthenticationContext = testDomain.createNewAuthenticationContext();
        serverAuthenticationContext.setAuthenticationName("TestUser");
        serverAuthenticationContext.authorize();

        SecurityIdentity securityIdentity = serverAuthenticationContext.getAuthorizedIdentity();

        PropagatedIdentity propagated = writeAndRead(securityIdentity, testAddress);
        securityIdentity = propagated.securityIdentity;

        Principal principal = securityIdentity.getPrincipal();
        assertEquals("Principal Name", "TestUser", principal.getName());
        assertEquals("Principal Realm", "TestRealm", ((RealmPrincipal)principal).getRealm());
        Set<String> identityRoles =  StreamSupport.stream(securityIdentity.getRoles().spliterator(), false).collect(Collectors.toSet());
        assertEquals("Roles Count", 2, identityRoles.size());
        assertTrue("GroupOne Membership", identityRoles.contains("GroupOne"));
        assertTrue("GroupTwo Membership", identityRoles.contains("GroupTwo"));

        assertEquals("Propagated Address", testAddress, propagated.inetAddress);
    }

    private PropagatedIdentity writeAndRead(SecurityIdentity identity, InetAddress inetAddress) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        IdentityAddressProtocolUtil.write(dos, identity, inetAddress);
        dos.close();
        baos.close();

        byte[] sent = baos.toByteArray();

        ByteArrayInputStream bais = new ByteArrayInputStream(sent);
        DataInputStream dis = new DataInputStream(bais);

        try {
            return IdentityAddressProtocolUtil.read(dis);
        } finally {
            dis.close();
            bais.close();
        }
    }

}
