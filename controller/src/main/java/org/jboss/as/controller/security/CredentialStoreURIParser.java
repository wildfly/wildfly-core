/*
 * JBoss, Home of Professional Open Source
 * Copyright 2015 Red Hat, Inc., and individual contributors
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
package org.jboss.as.controller.security;

import static org.jboss.as.controller.logging.ControllerLogger.ROOT_LOGGER;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * This class provides parsing for URIs with scheme "cr-store".
 *
 * <p> Credential Store URI is used for configuring/referencing credential stores.
 * It can specify complete information about credential store including parameters as well as reference
 * of stored secured credentials (such  as passwords).
 *
 * <h3> Credential Store URI scheme </h3>
 *
 * <blockquote>
 * crStoreURI  =  <i>scheme</i> {@code :} {@code //}<i>store_name</i> [{@code /} <i>storage_file</i>] [?<i>query</i>] [{@code #} <i>attribute_name</i>]
 *
 * <i>scheme</i> =  <b>cr-store</b>
 *
 * <i>store_name</i> = {@code //} alpha *alphanum
 *
 * <i>storage_file</i> = file_name_uri
 *
 * <i>query</i> = store_parameter = value *[{@code ;} <i>store_parameter</i> = <i>value</i>]
 *
 * <i>store_parameter</i> = alpha *alphanum
 *
 * <i>value</i> = {@code '}alpha *alphanum{@code '} {@code |} alpha *alphanum
 *
 * <i>attribute_name</i> = alpha *alphanum
 * </blockquote>
 *
 * <p> Credential Store URI has to be absolute with <b>store_name></b> always defined.
 * <p> parameters to {@code {@link org.wildfly.security.credential.store.CredentialStoreSpi}} implementation are supplied
 * through <b>query</b> part of URI. In case they need to decode binary value Base64 encoding method should be used.
 *
 * @author <a href="mailto:pskopek@redhat.com">Peter Skopek</a>.
 */
public class CredentialStoreURIParser {

    /**
     * Credential Store URI scheme name ("cr-store").
     */
    public static final String CR_STORE_SCHEME = "cr-store";

    private String name;
    private String storageFile;
    private final HashMap<String, String> options = new HashMap<>();
    private String attribute;

    /**
     * Creates {@link CredentialStoreURIParser} based on given URI
     *
     * @param uri URI to parse
     * @throws URISyntaxException in case of problems parsing given URI
     */
    public CredentialStoreURIParser(final String uri) throws URISyntaxException {
        int schemeInd = 0;
        if (uri.startsWith(CR_STORE_SCHEME + ":")) {
            schemeInd = (CR_STORE_SCHEME + ":").length();
        }
        int fragmentInd = uri.indexOf('#');
        URI uriToParse;
        if (fragmentInd == 0) {
            throw ROOT_LOGGER.credentialStoreHasNoName(safeCRStoreURI(uri));
        } else if (fragmentInd > -1) {
            String fragment = uri.substring(fragmentInd + 1);
            if (fragment.indexOf('#') > -1) {
                throw new URISyntaxException(uri, ROOT_LOGGER.moreThanOneFragmentDefined(), fragmentInd + fragment.indexOf('#'));
            }
            uriToParse = new URI(CR_STORE_SCHEME, uri.substring(schemeInd, fragmentInd), fragment);
        } else {
            uriToParse = new URI(CR_STORE_SCHEME, uri.substring(schemeInd), null);
        }
        parse(uriToParse);
    }

    /**
     * Creates {@link CredentialStoreURIParser} based on given {@link URI}
     *
     * @param uri URI to parse
     */
    public CredentialStoreURIParser(final URI uri) {
        parse(uri);
    }

    private void parse(final URI uri) {
        if (! uri.isAbsolute()) {
            throw ROOT_LOGGER.credentialStoreURIisNotAbsolute(safeCRStoreURI(uri.toString()));
        }
        if (! CR_STORE_SCHEME.equals(uri.getScheme())) {
            throw ROOT_LOGGER.credentialStoreURIWrongScheme(safeCRStoreURI(uri.toString()));
        }

        String authority = uri.getAuthority();
        if (authority != null) {
            name = authority;
        } else {
            throw ROOT_LOGGER.credentialStoreHasNoName(safeCRStoreURI(uri.toString()));
        }

        String path = uri.getPath();
        if (path != null && path.length() > 1) {
            storageFile = path.substring(1);
        } else {
            storageFile = null;
        }

        parseQueryParameter(uri.getQuery(), uri.toString());

        String fragment = uri.getFragment();
        if (fragment != null && fragment.length() >= 0) {
            if (fragment.isEmpty()) {
                throw ROOT_LOGGER.credentialStoreURIAttributeEmpty(CredentialStoreURIParser.safeCRStoreURI(uri.toString()));
            }
            attribute = fragment;
        } else {
            attribute = null;
        }
    }

    /**
     * Parses and creates {@code options} map with all Credential Store URI query parameters separated.
     * key value pairs are separated by {@code ;} semicolon.
     * @param query part of the Credential Store URI
     * @param uri {@code String} for logging and error messages
     */
    private void parseQueryParameter(final String query, final String uri) {

        if (query == null) {
            return;
        }

        int i = 0;
        int state = 0; // possible states KEY = 0 | VALUE = 1
        StringBuilder token = new StringBuilder();
        String key = null;
        String value = null;
        while (i < query.length()) {
            char c = query.charAt(i);
            if (state == 0) {   // KEY state
                if (c == '=') {
                    state = 1;
                    key = token.toString();
                    value = null;
                    token.setLength(0);
                } else {
                    token.append(c);
                }
                i++;
            } else if (state == 1) {  // VALUE state
                if (c == '\'') {
                    if (query.charAt(i - 1) != '=') {
                        throw ROOT_LOGGER.credentialStoreURIParameterOpeningQuote(CredentialStoreURIParser.safeCRStoreURI(uri));
                    }
                    int inQuotes = i + 1;
                    c = query.charAt(inQuotes);
                    while (inQuotes < query.length() && c != '\'') {
                        token.append(c);
                        inQuotes++;
                        c = query.charAt(inQuotes);
                    }
                    if (c == '\'') {
                        i = inQuotes + 1;
                        if (i < query.length() && query.charAt(i) != ';') {
                            throw ROOT_LOGGER.credentialStoreURIParameterClosingQuote(CredentialStoreURIParser.safeCRStoreURI(uri));
                        }
                    } else {
                        throw ROOT_LOGGER.credentialStoreURIParameterUnexpectedEnd(CredentialStoreURIParser.safeCRStoreURI(uri));
                    }
                } else if (c == ';') {
                    value = token.toString();
                    if (key == null) {
                        throw ROOT_LOGGER.credentialStoreURIParameterNameExpected(CredentialStoreURIParser.safeCRStoreURI(uri));
                    }
                    // put to options and reset key, value and token
                    options.put(key, value);
                    i++;
                    key = null;
                    value = null;
                    token.setLength(0);
                    // set state to KEY
                    state = 0;
                } else {
                    token.append(c);
                    i++;
                }
            }
        }
        if (key != null && token.length() > 0) {
            options.put(key, token.toString());
        } else {
            throw ROOT_LOGGER.credentialStoreURIParameterUnexpectedEnd(CredentialStoreURIParser.safeCRStoreURI(uri));
        }

    }

    /**
     * Returns parsed credential store name.
     *
     * @return credential store name
     */
    public String getName() {
        return name;
    }

    /**
     * Returns scheme handled by this parser.
     *
     * @return Credential Store URI scheme (always {@code CR_STORE_SCHEME})
     */
    public String getScheme() {
        return CR_STORE_SCHEME;
    }


    /**
     * Transforms given parameter to safely displayed {@code String} by stripping potentially sensitive information from the URI.
     *
     * @param uri original URI string
     * @return {@code String} safe to display
     */
    public static String safeCRStoreURI(String uri) {
        // for now, just easy stripping
        int startOfQuery = uri.indexOf('?');
        if (startOfQuery > -1) {
            return uri.substring(0, startOfQuery) + "...";
        } else {
            return uri;
        }
    }

    /**
     * If storage file was not specified by the Credential Store URI returns {@code null}
     * @return storage file as parsed from Credential Store URI as {@code String}
     */
    public String getStorageFile() {
        return storageFile;
    }

    /**
     * Returns attribute specified by parsed Credential Store URI
     *
     * @return attribute from Credential Store URI, if attribute was not specified then {@code null}
     */
    public String getAttribute() {
        return attribute;
    }

    /**
     * Fetch parameter value from query string.
     *
     * @param param name of wanted parameter
     * @return parameter value as a {@code String} or {@code null} if parameter was not specified in query part of the URI
     */
    public String getParameter(final String param) {
        return options.get(param);
    }

    /**
     * Returns {@code Set<String>} of parameters specified by the parsed Credential Store URI.
     * @return set of parameter names
     */
    public Set<String> getParameters() {
        return options.keySet();
    }

    /**
     * Returns new {@code Map<String, Object>} for use in {@code {@link org.wildfly.security.credential.store.CredentialStoreSpi}
     * to initialize it.
     * @return Map of options parsed from the Credential Store URI
     */
    public Map<String, String> getOptionsMap() {
        return new HashMap<>(options);
    }

}
