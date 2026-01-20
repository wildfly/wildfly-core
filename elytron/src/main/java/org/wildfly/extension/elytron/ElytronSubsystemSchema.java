/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.extension.elytron;

import org.jboss.as.controller.AttributeMarshallers;
import org.jboss.as.controller.AttributeParsers;
import org.jboss.as.controller.Feature;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.PersistentResourceXMLDescription;
import org.jboss.as.controller.PersistentSubsystemSchema;
import org.jboss.as.controller.SubsystemSchema;
import org.jboss.as.controller.xml.VersionedNamespace;
import org.jboss.as.version.Stability;
import org.jboss.staxmapper.IntVersion;

import java.util.EnumSet;
import java.util.Map;

import static org.jboss.as.controller.PersistentResourceXMLDescription.builder;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.DIR_CONTEXTS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.ENCRYPTION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.EXPRESSION;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.EXPRESSION_RESOLVER;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.PERMISSION_SETS;
import static org.wildfly.extension.elytron.ElytronDescriptionConstants.SECURITY_PROPERTY;
import static org.wildfly.extension.elytron.PermissionMapperDefinitions.PERMISSIONS;
import static org.wildfly.extension.elytron.SSLDefinitions.getDynamicClientSSLContextDefinition;

/**
 * Enumeration of elytron subsystem schema versions.
 */
public enum ElytronSubsystemSchema implements PersistentSubsystemSchema<ElytronSubsystemSchema> {
    VERSION_1_0(1),
    VERSION_1_1(1, 1),
    VERSION_1_2(1, 2),
    VERSION_2_0(2),
    VERSION_3_0(3),
    VERSION_4_0(4),
    VERSION_5_0(5),
    VERSION_6_0(6),
    VERSION_7_0(7),
    VERSION_8_0(8),
    VERSION_9_0(9),
    VERSION_10_0(10),
    VERSION_11_0(11),
    VERSION_12_0(12),
    VERSION_13_0(13),
    VERSION_14_0(14),
    VERSION_15_0(15),
    VERSION_15_1(15, 1),
    VERSION_16_0(16),
    VERSION_17_0(17),
    VERSION_18_0(18),
    VERSION_18_0_COMMUNITY(18, Stability.COMMUNITY),
    VERSION_19_0(19),
    ;
    static final Map<Stability, ElytronSubsystemSchema> CURRENT = Feature.map(EnumSet.of(VERSION_19_0, VERSION_18_0_COMMUNITY));

    private final VersionedNamespace<IntVersion, ElytronSubsystemSchema> namespace;

    ElytronSubsystemSchema(int major) {
        this.namespace = SubsystemSchema.createSubsystemURN(ElytronExtension.SUBSYSTEM_NAME, new IntVersion(major));
    }

    ElytronSubsystemSchema(int major, int minor) {
        this.namespace = SubsystemSchema.createSubsystemURN(ElytronExtension.SUBSYSTEM_NAME, new IntVersion(major, minor));
    }

    ElytronSubsystemSchema(int major, Stability stability) {
        this.namespace = SubsystemSchema.createSubsystemURN(ElytronExtension.SUBSYSTEM_NAME, stability, new IntVersion(major));
    }

    @Override
    public VersionedNamespace<IntVersion, ElytronSubsystemSchema> getNamespace() {
        return this.namespace;
    }

    @Override
    public PersistentResourceXMLDescription getXMLDescription() {
        PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder = builder(ElytronExtension.SUBSYSTEM_PATH, this.getNamespace());
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addAttribute(ElytronDefinition.DEFAULT_AUTHENTICATION_CONTEXT)
                    .addAttribute(ElytronDefinition.INITIAL_PROVIDERS)
                    .addAttribute(ElytronDefinition.FINAL_PROVIDERS)
                    .addAttribute(ElytronDefinition.DISALLOWED_PROVIDERS)
                    .addAttribute(ElytronDefinition.SECURITY_PROPERTIES, new AttributeParsers.PropertiesParser(null, SECURITY_PROPERTY, true), new AttributeMarshallers.PropertiesAttributeMarshaller(null, SECURITY_PROPERTY, true));
        }

        if (this.since(ElytronSubsystemSchema.VERSION_5_0)) {
            builder.addAttribute(ElytronDefinition.REGISTER_JASPI_FACTORY)
                    .addAttribute(ElytronDefinition.DEFAULT_SSL_CONTEXT);
        }

        addAuthenticationClientParser(builder);
        addProviderParser(builder);
        addAuditLoggingParser(builder);
        addSecurityDomainParser(builder);
        addRealmParser(builder);
        addCredentialSecurityFactoryParser(builder);
        addMapperParser(builder);
        addPermissionSetParser(builder);
        addHttpParser(builder);
        addSaslParser(builder);
        addTlsParser(builder);
        addCredentialStoreParser(builder);
        addExpressionResolverParser(builder);
        addDirContextParser(builder);
        addPolicyParser(builder);
        addJaspiConfigurationParser(builder);

        return builder.build();
    }

    private void addJaspiConfigurationParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_5_0)) {
            builder.addChild(new JaspiConfigurationParser().jaspiConfigurationParser_5_0);
        }
    }

    private void addDirContextParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(PersistentResourceXMLDescription.decorator(DIR_CONTEXTS)
                    .addChild(builder(PathElement.pathElement(ElytronDescriptionConstants.DIR_CONTEXT))
                            .addAttributes(DirContextDefinition.ATTRIBUTES))
                    .build());
        }
    }

    private void addExpressionResolverParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_13_0)) {
            builder.addChild(PersistentResourceXMLDescription.builder(
                            PathElement.pathElement(EXPRESSION, ENCRYPTION))
                    .setXmlElementName(EXPRESSION_RESOLVER)
                    .addAttribute(ExpressionResolverResourceDefinition.RESOLVERS)
                    .addAttribute(ExpressionResolverResourceDefinition.DEFAULT_RESOLVER)
                    .addAttribute(ExpressionResolverResourceDefinition.PREFIX)
                    .build());
        }
    }

    private void addSaslParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(new SaslParser().parser);
        }
    }

    private void addHttpParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(new HttpParser().parser);
        }
    }

    private void addPermissionSetParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_3_0)) {
            builder.addChild(PersistentResourceXMLDescription.builder(PermissionSetDefinition.getPermissionSet().getPathElement())
                    .setXmlWrapperElement(PERMISSION_SETS)
                    .addAttribute(PERMISSIONS)
                    .build());
        }
    }

    private void addCredentialSecurityFactoryParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(new CredentialSecurityFactoryParser().parser);
        }
    }

    private void addProviderParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(new ProviderParser().parser);
        }
    }

    private void addPolicyParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        PolicyParser policyParser = new PolicyParser();
        if (this.since(ElytronSubsystemSchema.VERSION_1_2)) {
            builder.addChild(policyParser.parser_1_2);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(policyParser.parser_1_0);
        }
    }

    private void addCredentialStoreParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        CredentialStoreParser credentialStoreParser = new CredentialStoreParser();
        if (this.since(ElytronSubsystemSchema.VERSION_13_0)) {
            builder.addChild(credentialStoreParser.getCredentialStoresParser_13().build());
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(credentialStoreParser.getCredentialStoresParser().build());
        }
    }

    private void addTlsParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        TlsParser tlsParser = new TlsParser();
        if (this.since(ElytronSubsystemSchema.VERSION_18_0_COMMUNITY) && this.enables(getDynamicClientSSLContextDefinition())) {
            builder.addChild(tlsParser.tlsParserCommunity_18_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_14_0)) {
            builder.addChild(tlsParser.tlsParser_14_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_12_0)) {
            builder.addChild(tlsParser.tlsParser_12_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_9_0)) {
            builder.addChild(tlsParser.tlsParser_9_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_8_0)) {
            builder.addChild(tlsParser.tlsParser_8_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_5_0)) {
            builder.addChild(tlsParser.tlsParser_5_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_4_0)) {
            builder.addChild(tlsParser.tlsParser_4_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(tlsParser.tlsParser_1_0);
        }
    }

    private void addMapperParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        if (this.since(ElytronSubsystemSchema.VERSION_12_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_12_0).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_10_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_10_0).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_8_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_8_0).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_4_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_4_0).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_3_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_3_0).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_1)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_1_1).getParser());
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(new MapperParser(MapperParser.Version.VERSION_1_0).getParser());
        }
    }

    private void addRealmParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        RealmParser realmParser = new RealmParser();
        if (this.since(ElytronSubsystemSchema.VERSION_18_0)) {
            builder.addChild(realmParser.realmParser_18);
        } else if (this.since(ElytronSubsystemSchema.VERSION_16_0)) {
            builder.addChild(realmParser.realmParser_16);
        } else if (this.since(ElytronSubsystemSchema.VERSION_15_1)) {
            builder.addChild(realmParser.realmParser_15_1);
        } else if (this.since(ElytronSubsystemSchema.VERSION_15_0)) {
            builder.addChild(realmParser.realmParser_15_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_14_0)) {
            builder.addChild(realmParser.realmParser_14_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_11_0)) {
            builder.addChild(realmParser.realmParser_11_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_8_0)) {
            builder.addChild(realmParser.realmParser_8_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_7_0)) {
            builder.addChild(realmParser.realmParser_7_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(realmParser.realmParser);
        }
    }

    private void addSecurityDomainParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        SecurityDomainParser securityDomainParser = new SecurityDomainParser();
        if (this.since(ElytronSubsystemSchema.VERSION_17_0)) {
            builder.addChild(securityDomainParser.parser_17_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_10_0)) {
            builder.addChild(securityDomainParser.parser_10_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_8_0)) {
            builder.addChild(securityDomainParser.parser_8_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(securityDomainParser.parser_1_0);
        }
    }

    private void addAuditLoggingParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        AuditLoggingParser auditLoggingParser = new AuditLoggingParser();
        if (this.since(ElytronSubsystemSchema.VERSION_18_0)) {
            builder.addChild(auditLoggingParser.parser18_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_8_0)) {
            builder.addChild(auditLoggingParser.parser8_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_5_0)) {
            builder.addChild(auditLoggingParser.parser5_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_4_0)) {
            builder.addChild(auditLoggingParser.parser4_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(auditLoggingParser.parser);
        }
    }

    private void addAuthenticationClientParser(PersistentResourceXMLDescription.PersistentResourceXMLBuilder builder) {
        AuthenticationClientParser authenticationClientParser = new AuthenticationClientParser();
        if (this.since(ElytronSubsystemSchema.VERSION_9_0)) {
            builder.addChild(authenticationClientParser.parser_9_0);
        } else if (this.since(ElytronSubsystemSchema.VERSION_1_0)) {
            builder.addChild(authenticationClientParser.parser);
        }
    }
}
