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

import java.util.List;
import org.jboss.as.cli.Util;
import org.jboss.dmr.ModelNode;

/**
 * Server SSLContext model class.
 *
 * @author jdenise@redhat.com
 */
public class ServerSSLContext {

    private final String name;
    private final KeyManager keyManager;
    private final KeyManager trustManager;
    private final boolean exists;

    private List<String> protocols;
    private boolean authenticationOptional;
    private String cipherSuiteFilter = "DEFAULT";
    // WFCORE-4789: Set cipherSuiteNames to CipherSuiteSelector.OPENSSL_DEFAULT_CIPHER_SUITE_NAMES once we are ready to enable
    // TLS 1.3 by default
    private String cipherSuiteNames;
    private String finalPrincipalTransformer;
    private String postRealmPrincipalTransformer;
    private String preRealmPrincipalTransformer;
    private String providerName;
    private List<String> providers;
    private String realmMapper;
    private String securityDomain;
    private boolean want;
    private boolean need;
    private boolean useCipherSuiteOrder;

    public ServerSSLContext(String name, KeyManager keyManager,
            KeyManager trustManager, boolean exists) {
        this.name = name;
        this.keyManager = keyManager;
        this.trustManager = trustManager;
        this.exists = exists;
    }

    public boolean exists() {
        return exists;
    }

    public String getName() {
        return name;
    }

    public KeyManager getKeyManager() {
        return keyManager;
    }

    public KeyManager getTrustManager() {
        return trustManager;
    }

    /**
     * @return the protocols
     */
    public List<String> getProtocols() {
        return protocols;
    }

    /**
     * @param protocols the protocols to set
     */
    public void setProtocols(List<String> protocols) {
        this.protocols = protocols;
    }

    /**
     * @return the authenticationOptional
     */
    public boolean isAuthenticationOptional() {
        return authenticationOptional;
    }

    /**
     * @param authenticationOptional the authenticationOptional to set
     */
    public void setAuthenticationOptional(boolean authenticationOptional) {
        this.authenticationOptional = authenticationOptional;
    }

    /**
     * @return the cipherSuiteFilter
     */
    public String getCipherSuiteFilter() {
        return cipherSuiteFilter;
    }

    /**
     * @param cipherSuiteFilter the cipherSuiteFilter to set
     */
    public void setCipherSuiteFilter(String cipherSuiteFilter) {
        this.cipherSuiteFilter = cipherSuiteFilter;
    }

    /**
     * Get the cipher suite names.
     *
     * @return the cipher suite names
     */
    public String getCipherSuiteNames() {
        return cipherSuiteNames;
    }

    /**
     * Set the cipher suite names.
     *
     * @param cipherSuiteNames the cipher suite names
     */
    public void setCipherSuiteNames(String cipherSuiteNames) {
        this.cipherSuiteNames = cipherSuiteNames;
    }


    /**
     * @return the finalPrincipalTransformer
     */
    public String getFinalPrincipalTransformer() {
        return finalPrincipalTransformer;
    }

    /**
     * @param finalPrincipalTransformer the finalPrincipalTransformer to set
     */
    public void setFinalPrincipalTransformer(String finalPrincipalTransformer) {
        this.finalPrincipalTransformer = finalPrincipalTransformer;
    }

    /**
     * @return the postRealmPrincipalTransformer
     */
    public String getPostRealmPrincipalTransformer() {
        return postRealmPrincipalTransformer;
    }

    /**
     * @param postRealmPrincipalTransformer the postRealmPrincipalTransformer to
     * set
     */
    public void setPostRealmPrincipalTransformer(String postRealmPrincipalTransformer) {
        this.postRealmPrincipalTransformer = postRealmPrincipalTransformer;
    }

    /**
     * @return the preRealmPrincipalTransformer
     */
    public String getPreRealmPrincipalTransformer() {
        return preRealmPrincipalTransformer;
    }

    /**
     * @param preRealmPrincipalTransformer the preRealmPrincipalTransformer to
     * set
     */
    public void setPreRealmPrincipalTransformer(String preRealmPrincipalTransformer) {
        this.preRealmPrincipalTransformer = preRealmPrincipalTransformer;
    }

    /**
     * @return the providerName
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * @param providerName the providerName to set
     */
    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    /**
     * @return the providers
     */
    public List<String> getProviders() {
        return providers;
    }

    /**
     * @param providers the providers to set
     */
    public void setProviders(List<String> providers) {
        this.providers = providers;
    }

    /**
     * @return the realmMapper
     */
    public String getRealmMapper() {
        return realmMapper;
    }

    /**
     * @param realmMapper the realmMapper to set
     */
    public void setRealmMapper(String realmMapper) {
        this.realmMapper = realmMapper;
    }

    /**
     * @return the securityDomain
     */
    public String getSecurityDomain() {
        return securityDomain;
    }

    /**
     * @param securityDomain the securityDomain to set
     */
    public void setSecurityDomain(String securityDomain) {
        this.securityDomain = securityDomain;
    }

    /**
     * @return the want
     */
    public boolean isWant() {
        return want;
    }

    /**
     * @param want the want to set
     */
    public void setWant(boolean want) {
        this.want = want;
    }

    /**
     * @return the need
     */
    public boolean isNeed() {
        return need;
    }

    /**
     * @param need the need to set
     */
    public void setNeed(boolean need) {
        this.need = need;
    }

    /**
     * @return the useCipherSuiteOrder
     */
    public boolean isUseCipherSuiteOrder() {
        return useCipherSuiteOrder;
    }

    /**
     * @param useCipherSuiteOrder the useCipherSuiteOrder to set
     */
    public void setUseCipherSuiteOrder(boolean useCipherSuiteOrder) {
        this.useCipherSuiteOrder = useCipherSuiteOrder;
    }

    public ModelNode buildResource() {
        ModelNode sslCtx = new ModelNode();
        sslCtx.get(Util.KEY_MANAGER).set(keyManager.getName());
        sslCtx.get(Util.WANT_CLIENT_AUTH).set(want);
        sslCtx.get(Util.NEED_CLIENT_AUTH).set(need);
        if (trustManager != null) {
            sslCtx.get(Util.TRUST_MANAGER).set(trustManager.getName());
        } else {
            sslCtx.get(Util.TRUST_MANAGER);
        }
        if (protocols != null) {
            ModelNode protocolsNode = sslCtx.get(Util.PROTOCOLS);
            for (String p : protocols) {
                protocolsNode.add(p);
            }
        } else {
            sslCtx.get(Util.PROTOCOLS);
        }
        sslCtx.get(Util.AUTHENTICATION_OPTIONAL).set(authenticationOptional);
        if (cipherSuiteFilter != null) {
            sslCtx.get(Util.CIPHER_SUITE_FILTER).set(cipherSuiteFilter);
        } else {
            sslCtx.get(Util.CIPHER_SUITE_FILTER);
        }
        if (cipherSuiteNames != null) {
            sslCtx.get(Util.CIPHER_SUITE_NAMES).set(cipherSuiteNames);
        } else {
            sslCtx.get(Util.CIPHER_SUITE_NAMES);
        }
        if (finalPrincipalTransformer != null) {
            sslCtx.get(Util.FINAL_PRINCIPAL_TRANSFORMER).set(finalPrincipalTransformer);
        } else {
            sslCtx.get(Util.FINAL_PRINCIPAL_TRANSFORMER);
        }
        if (postRealmPrincipalTransformer != null) {
            sslCtx.get(Util.POST_REALM_PRINCIPAL_TRANSFORMER).set(postRealmPrincipalTransformer);
        } else {
            sslCtx.get(Util.POST_REALM_PRINCIPAL_TRANSFORMER);
        }
        if (preRealmPrincipalTransformer != null) {
            sslCtx.get(Util.PRE_REALM_PRINCIPAL_TRANSFORMER).set(preRealmPrincipalTransformer);
        } else {
            sslCtx.get(Util.PRE_REALM_PRINCIPAL_TRANSFORMER);
        }
        if (providerName != null) {
            sslCtx.get(Util.PROVIDER_NAME).set(providerName);
        } else {
            sslCtx.get(Util.PROVIDER_NAME);
        }
        if (providers != null) {
            ModelNode providersNode = sslCtx.get(Util.PROVIDERS);
            for (String p : providers) {
                providersNode.add(p);
            }
        } else {
            sslCtx.get(Util.PROVIDERS);
        }
        if (realmMapper != null) {
            sslCtx.get(Util.REALM_MAPPER).set(realmMapper);
        } else {
            sslCtx.get(Util.REALM_MAPPER);
        }
        if (securityDomain != null) {
            sslCtx.get(Util.SECURITY_DOMAIN).set(securityDomain);
        } else {
            sslCtx.get(Util.SECURITY_DOMAIN);
        }
        sslCtx.get(Util.USE_CIPHER_SUITES_ORDER).set(useCipherSuiteOrder);
        return sslCtx;
    }
}
