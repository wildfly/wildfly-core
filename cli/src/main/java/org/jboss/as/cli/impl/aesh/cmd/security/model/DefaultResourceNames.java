/*
Copyright 2017 Red Hat, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
 */
package org.jboss.as.cli.impl.aesh.cmd.security.model;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.jboss.as.cli.CommandContext;
import org.jboss.as.cli.Util;
import org.jboss.as.cli.operation.OperationFormatException;

/**
 * Class that exposes all default values.
 *
 * @author jdenise@redhat.com
 */
public class DefaultResourceNames {

    private static final String SECURITY_DOMAIN_NAME = "security-domain";
    public static final String ROLE_MAPPER_NAME = "role-mapper";
    private static final String FACTORY_NAME = "factory";

    public static String buildDefaultKeyStoreName(String name, CommandContext ctx) throws OperationFormatException, IOException {
        int i = 1;
        String computedName = name;
        while (ElytronUtil.keyStoreExists(ctx, computedName)) {
            computedName = name + "_" + i;
            i += 1;
        }
        return computedName;
    }

    public static String buildDefaultKeyStoreName(File path, CommandContext ctx) throws OperationFormatException, IOException {
        return buildDefaultKeyStoreName(path.getName(), ctx);
    }

    public static String buildDefaultKeyStorePath(File path, CommandContext ctx) throws OperationFormatException, IOException {
        return buildDefaultKeyStoreName(path.getName(), ctx);
    }

    public static String buildDefaultKeyStoreAlias(String dn, CommandContext ctx) throws OperationFormatException, IOException {
        return dn.substring(0, dn.indexOf("="));
    }

    static String buildDefaultKeyStoreType(String type, CommandContext ctx) {
        return ElytronUtil.JKS;
    }

    static String buildDefaultKeyManagerName(CommandContext ctx, String keystoreName) throws IOException, OperationFormatException {
        int i = 1;
        String computedName = "key-manager-" + keystoreName;
        while (ElytronUtil.keyManagerExists(ctx, computedName)) {
            computedName = "key-manager-" + keystoreName + "_" + i;
            i += 1;
        }
        return computedName;
    }

    static String buildDefaultSSLContextName(CommandContext ctx, String keystoreName) throws IOException, OperationFormatException {
        int i = 1;
        String computedName = "ssl-context-" + keystoreName;
        while (ElytronUtil.serverSSLContextExists(ctx, computedName)) {
            computedName = "ssl-context-" + keystoreName + "_" + i;
            i += 1;
        }
        return computedName;
    }

    public static String getDefaultManagementInterfaceName(CommandContext ctx) {
        return Util.HTTP_INTERFACE;
    }

    static List<String> getDefaultProtocols(CommandContext ctx) {
        return Arrays.asList(ElytronUtil.TLS_V1_2);
    }

    static String getDefaultHttpSecureSocketBindingName(String managementInterface, CommandContext ctx) {
        return Util.MANAGEMENT_HTTPS;
    }

    public static String getDefaultServerName(CommandContext context) {
        return HTTPServer.DEFAULT_SERVER;
    }

    static String getDefaultApplicationLegacyRealm() {
        return Util.APPLICATION_REALM;
    }

    public static String buildUserPropertiesDefaultRealmName(CommandContext ctx,
            PropertiesRealmConfiguration config) throws OperationFormatException, IOException {
        String name = new File(config.getUserPropertiesFile()).getName();
        int i = 1;
        String computedName = name;
        while (ElytronUtil.serverPropertiesRealmExists(ctx, computedName)) {
            computedName = name + "_" + i;
            i += 1;
        }
        return computedName;
    }

    public static String buildDefaultSecurityDomainName(Realm realm, CommandContext ctx) throws OperationFormatException, IOException {
        int i = 1;
        String computedName = SECURITY_DOMAIN_NAME;
        while (ElytronUtil.securityDomainExists(ctx, computedName)) {
            computedName = SECURITY_DOMAIN_NAME + "_" + i;
            i += 1;
        }
        return computedName;
    }

    public static String buildConstantRoleMapperName(Realm realm, CommandContext ctx) throws OperationFormatException, IOException {
        int i = 1;
        String computedName = ROLE_MAPPER_NAME;
        while (ElytronUtil.constantRoleMapperExists(ctx, computedName)) {
            computedName = ROLE_MAPPER_NAME + "_" + i;
            i += 1;
        }
        return computedName;
    }

    public static String buildDefaultAuthFactoryName(AuthMechanism meca, AuthFactorySpec spec, CommandContext ctx) throws OperationFormatException, IOException {
        int i = 1;
        String computedName = spec.getName() + "-" + FACTORY_NAME;
        while (ElytronUtil.factoryExists(ctx, computedName, spec)) {
            computedName = spec.getName() + "-" + FACTORY_NAME + "_" + i;
            i += 1;
        }
        return computedName;
    }
}
