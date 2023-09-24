/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.as.controller.remote;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.net.InetAddress;
import java.security.Principal;
import java.security.spec.AlgorithmParameterSpec;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.jboss.as.controller.client.impl.ModelControllerProtocol;
import org.jboss.as.controller.logging.ControllerLogger;
import org.jboss.as.protocol.mgmt.ProtocolUtils;
import org.wildfly.security.auth.SupportLevel;
import org.wildfly.security.auth.permission.LoginPermission;
import org.wildfly.security.auth.principal.NamePrincipal;
import org.wildfly.security.auth.server.RealmIdentity;
import org.wildfly.security.auth.server.RealmUnavailableException;
import org.wildfly.security.auth.server.SecurityDomain;
import org.wildfly.security.auth.server.SecurityIdentity;
import org.wildfly.security.auth.server.SecurityRealm;
import org.wildfly.security.auth.server.ServerAuthenticationContext;
import org.wildfly.security.authz.AuthorizationIdentity;
import org.wildfly.security.authz.MapAttributes;
import org.wildfly.security.authz.RoleDecoder;
import org.wildfly.security.credential.Credential;
import org.wildfly.security.evidence.Evidence;

/**
 * Utility for writing and reading SecurityIdentities and InetAddresses.
 *
 * Note: Although this implementation operates on a {@link SecurityIdentity} and {@link InetAddress} it is reusing the protocol
 * established in WildFly 8 to propagate the contents of a Subject to different processes, this has the advantage that older
 * slaves will still understand the message and be able to interpret it correctly but it does mean any subsequent deviations
 * from the protocol would require a management protocol version bump and a check to decide which form to send.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class IdentityAddressProtocolUtil {

    private static final SecurityDomain INFLOW_SECURITY_DOMAIN = createSecurityDomain();

    private static final byte USER = 0x01;
    private static final byte GROUP = 0x02;
    private static final byte ROLE = 0x03;
    private static final byte INET_ADDRESS = 0x04;

    private static final byte ITEMS_PARAM = 0x05;

    private static final byte REALM_PARAM = 0x06;
    private static final byte NAME_PARAM = 0x07;
    private static final byte HOST_PARAM = 0x08;
    private static final byte ADDR_PARAM = 0x09;

    static void write(final DataOutput output, final SecurityIdentity securityIdentity, final InetAddress sourceAddress) throws IOException {
        final Principal principal;
        final Set<String> roles;
        if (securityIdentity != null) {
            principal = securityIdentity.getPrincipal();
            roles = StreamSupport.stream(securityIdentity.getRoles().spliterator(), false).collect(Collectors.toSet());
        } else {
            principal = null;
            roles = Collections.emptySet();
        }

        int itemsToSend = (principal != null ? 1 : 0) + roles.size() + (sourceAddress != null ? 1 : 0);

        output.writeByte(ModelControllerProtocol.PARAM_IDENTITY_LENGTH);
        if (itemsToSend == 0) {
            output.writeInt(0);
            return;
        }

        output.writeInt(1); // We have one batch of identity related items to send.
        output.write(ITEMS_PARAM);
        output.writeInt(itemsToSend); // Number of items being written.

        if (principal != null) {
            output.write(USER);
            output.write(NAME_PARAM);
            output.writeUTF(principal.getName());
        }

        // We send all the SI roles as groups as previous versions would map from group to access control role.
        for (String roleName : roles) {
            output.write(GROUP);
            output.write(NAME_PARAM);
            output.writeUTF(roleName);

            // We could also send each one as ROLE but for now don't.
            // If we later send each as a role - do not include the realm as older clients do not expect a realm to be associated with a role.
        }

        if (sourceAddress != null) {
            output.write(INET_ADDRESS);

            String host = sourceAddress.getHostName();
            byte[] addr = sourceAddress.getAddress();

            output.write(HOST_PARAM);
            output.writeUTF(host);
            output.write(ADDR_PARAM);
            output.writeInt(addr.length);
            output.write(addr);
        }
    }

    static PropagatedIdentity read(final DataInput input) throws IOException {
        ProtocolUtils.expectHeader(input, ModelControllerProtocol.PARAM_IDENTITY_LENGTH);
        final int size = input.readInt();
        if (size == 0) {
            return null;
        }

        ProtocolUtils.expectHeader(input, ITEMS_PARAM);
        final int itemCount = input.readInt();

        Principal principal = null;
        Set<String> roles = new HashSet<>(Math.max(itemCount - 2, 0));
        InetAddress sourceAddress = null;
        for (int i = 0; i < itemCount; i++) {
            byte type = input.readByte();
            switch (type) {
                case USER: {
                    byte paramType = input.readByte();
                    String name = null;
                    if (paramType == REALM_PARAM) {
                        input.readUTF(); // Drop it - no longer used.
                        paramType = input.readByte();
                    }
                    if (paramType == NAME_PARAM) {
                        name = input.readUTF();
                    } else {
                        throw ControllerLogger.ROOT_LOGGER.unsupportedIdentityParameter(paramType, USER);
                    }

                    principal = new NamePrincipal(name);
                    break;
                }
                case GROUP:
                case ROLE: {
                    byte paramType = input.readByte();
                    String name = null;
                    if (paramType == REALM_PARAM) {
                        input.readUTF(); // Drop it - we no longer use group specific realms.
                        paramType = input.readByte();
                    }
                    if (paramType == NAME_PARAM) {
                        name = input.readUTF();
                    } else {
                        throw ControllerLogger.ROOT_LOGGER.unsupportedIdentityParameter(paramType, GROUP);
                    }

                    // Silently ignore any duplicates received.
                    roles.add(name);
                    break;
                }
                case INET_ADDRESS: {
                    byte paramType = input.readByte();
                    String host;
                    byte[] addr;
                    if (paramType == HOST_PARAM) {
                        host = input.readUTF();
                    } else {
                        throw ControllerLogger.ROOT_LOGGER.unsupportedIdentityParameter(paramType, INET_ADDRESS);
                    }

                    paramType = input.readByte();
                    if (paramType == ADDR_PARAM) {
                        int length = input.readInt();
                        addr = new byte[length];
                        input.readFully(addr);
                    } else {
                        throw ControllerLogger.ROOT_LOGGER.unsupportedIdentityParameter(paramType, INET_ADDRESS);
                    }

                    sourceAddress = InetAddress.getByAddress(host, addr);
                    break;
                }
                default:
                    throw ControllerLogger.ROOT_LOGGER.unsupportedIdentityType(type);
            }
        }

        return principal != null || sourceAddress != null
                ? new PropagatedIdentity(principal != null ? createSecurityIdentity(principal, roles) : null, sourceAddress)
                : null;
    }

    private static SecurityIdentity createSecurityIdentity(Principal principal, Set<String> roles) {
        ServerAuthenticationContext serverAuthenticationContext = INFLOW_SECURITY_DOMAIN.createNewAuthenticationContext();
        try {
            serverAuthenticationContext.verifyEvidence(new EvidenceWithRoles(principal, roles));
            serverAuthenticationContext.authorize();
        } catch (RealmUnavailableException e) {
            // As the domain is backed by a dummy realm that never throws this Exception it is impossible for it to be thrown.
            throw new IllegalStateException(e);
        }

        return serverAuthenticationContext.getAuthorizedIdentity();
    }

    private static SecurityDomain createSecurityDomain() {
        return SecurityDomain.builder().setDefaultRealmName("Empty").addRealm("Empty", new SecurityRealm() {

            @Override
            public RealmIdentity getRealmIdentity(final Principal principal) throws RealmUnavailableException {
                return new RealmIdentity() {

                    private volatile Set<String> roles = null;

                    @Override
                    public boolean verifyEvidence(Evidence evidence) throws RealmUnavailableException {
                        this.roles = ((EvidenceWithRoles) evidence).roles;
                        return true;
                    }

                    @Override
                    public Principal getRealmIdentityPrincipal() {
                        return principal;
                    }

                    @Override
                    public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                            throws RealmUnavailableException {
                        return SupportLevel.UNSUPPORTED;
                    }

                    public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType,
                            String algorithmName) throws RealmUnavailableException {
                        return SupportLevel.UNSUPPORTED;
                    }

                    public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType,
                            String algorithmName, AlgorithmParameterSpec parameterSpec) throws RealmUnavailableException {
                        return SupportLevel.UNSUPPORTED;
                    }

                    @Override
                    public <C extends Credential> C getCredential(Class<C> credentialType) throws RealmUnavailableException {
                        return null;
                    }

                    @Override
                    public boolean exists() throws RealmUnavailableException {
                        return true;
                    }

                    @Override
                    public AuthorizationIdentity getAuthorizationIdentity() throws RealmUnavailableException {
                        MapAttributes mapAttributes = new MapAttributes();
                        mapAttributes.addAll("GROUPS", roles);
                        return AuthorizationIdentity.basicIdentity(mapAttributes);
                    }

                };
            }

            @Override
            public SupportLevel getEvidenceVerifySupport(Class<? extends Evidence> evidenceType, String algorithmName)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }

            public SupportLevel getCredentialAcquireSupport(Class<? extends Credential> credentialType, String algorithmName, AlgorithmParameterSpec parameterSpec)
                    throws RealmUnavailableException {
                return SupportLevel.UNSUPPORTED;
            }
        }).setRoleDecoder(RoleDecoder.simple("GROUPS")).build().setPermissionMapper((permissionMappable, roles) -> LoginPermission.getInstance()).build();
    }

    static final class PropagatedIdentity {
        final SecurityIdentity securityIdentity;
        final InetAddress inetAddress;

        public PropagatedIdentity(SecurityIdentity securityIdentity, InetAddress inetAddress) {
            this.securityIdentity = securityIdentity;
            this.inetAddress = inetAddress;
        }
    }

    static final class EvidenceWithRoles implements Evidence {

        final Principal principal;
        final Set<String> roles;

        EvidenceWithRoles(Principal principal, Set<String> roles) {
            this.principal = principal;
            this.roles = roles;
        }

        @Override
        public Principal getPrincipal() {
            return principal;
        }

    }

}
