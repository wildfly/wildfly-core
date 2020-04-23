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
package org.jboss.as.domain.management.security;

import static org.jboss.as.domain.management.logging.DomainManagementLogger.SECURITY_LOGGER;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.LdapReferralException;

/**
 * Factory to create searchers for user in LDAP.
 *
 * @author <a href="mailto:darran.lofthouse@jboss.com">Darran Lofthouse</a>
 */
class LdapUserSearcherFactory {

    protected static final int searchTimeLimit = 10000; // TODO - Maybe make configurable.

    static LdapSearcher<LdapEntry, String> createForUsernameIsDn() {
        return new LdapSearcher<LdapEntry, String>() {

            @Override
            public LdapEntry search(LdapConnectionHandler connectionHandler, String suppliedName) {
                return new LdapEntry(suppliedName, suppliedName);
            }
        };
    }

    static LdapSearcher<LdapEntry, String> createForUsernameFilter(final String baseDn, final boolean recursive, final String userDnAttribute, final String attribute, final String usernameLoad) {
        return new LdapUserSearcherImpl(baseDn, recursive, userDnAttribute, attribute, null, usernameLoad);
    }

    static LdapSearcher<LdapEntry, String> createForAdvancedFilter(final String baseDn, final boolean recursive, final String userDnAttribute, final String filter, final String usernameLoad) {
        return new LdapUserSearcherImpl(baseDn, recursive, userDnAttribute, null, filter, usernameLoad);
    }

    private static class LdapUserSearcherImpl implements LdapSearcher<LdapEntry, String> {

        final String baseDn;
        final boolean recursive;
        final String userDnAttribute;
        final String userNameAttribute;
        final String advancedFilter;
        final String usernameLoad;

        private LdapUserSearcherImpl(final String baseDn, final boolean recursive, final String userDnAttribute,
                final String userNameAttribute, final String advancedFilter, final String usernameLoad) {
            this.baseDn = baseDn;
            this.recursive = recursive;
            this.userDnAttribute = userDnAttribute;
            this.userNameAttribute = userNameAttribute;
            this.advancedFilter = advancedFilter;
            this.usernameLoad = usernameLoad;

            if (SECURITY_LOGGER.isTraceEnabled()) {
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl baseDn=%s", baseDn);
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl recursive=%b", recursive);
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl userDnAttribute=%s", userDnAttribute);
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl userNameAttribute=%s", userNameAttribute);
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl advancedFilter=%s", advancedFilter);
                SECURITY_LOGGER.tracef("LdapUserSearcherImpl usernameLoad=%s", usernameLoad);
            }
        }


        @Override
        public LdapEntry search(final LdapConnectionHandler connectionHandler, final String suppliedName) throws IOException, NamingException {
            NamingEnumeration<SearchResult> searchEnumeration = null;

            try {
                SearchControls searchControls = new SearchControls();
                if (recursive) {
                    SECURITY_LOGGER.trace("Performing recursive search");
                    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
                } else {
                    SECURITY_LOGGER.trace("Performing single level search");
                    searchControls.setSearchScope(SearchControls.ONELEVEL_SCOPE);
                }
                if (usernameLoad == null) {
                    searchControls.setReturningAttributes(new String[] { userDnAttribute });
                } else {
                    searchControls.setReturningAttributes(new String[] { userDnAttribute, usernameLoad });
                }
                searchControls.setTimeLimit(searchTimeLimit);

                Object[] filterArguments = new Object[] { suppliedName };
                String filter = userNameAttribute != null ? "(" + userNameAttribute + "={0})" : advancedFilter;
                SECURITY_LOGGER.tracef("Searching for user '%s' using filter '%s'.", suppliedName, filter);

                String distinguishedUserDN = null;
                String username = usernameLoad == null ? suppliedName : null;
                URI referralAddress = null;
                Attributes attributes = null;

                LdapConnectionHandler currentConnectionHandler = connectionHandler;
                final DirContext connection = currentConnectionHandler.getConnection();
                try {
                    searchEnumeration = connection.search(baseDn, filter, filterArguments, searchControls);
                    if (searchEnumeration.hasMore() == false) {
                        SECURITY_LOGGER.tracef("User '%s' not found in directory.", suppliedName);
                        throw SECURITY_LOGGER.userNotFoundInDirectory(suppliedName);
                    }
                } catch (LdapReferralException e) {
                    Object info = e.getReferralInfo();
                    try {
                        URI fullUri = new URI(info.toString());
                        referralAddress = new URI(fullUri.getScheme(), null, fullUri.getHost(), fullUri.getPort(), null, null,
                                null);
                        distinguishedUserDN = fullUri.getPath().substring(1);
                        SECURITY_LOGGER.tracef("Received referral with address '%s' for dn '%s'", referralAddress.toString(),
                                distinguishedUserDN);

                        currentConnectionHandler = currentConnectionHandler.findForReferral(referralAddress);
                        if (currentConnectionHandler == null) {
                            SECURITY_LOGGER.tracef("Unable to follow referral to '%s' for user '%s'", fullUri, suppliedName);
                            throw SECURITY_LOGGER.userNotFoundInDirectory(suppliedName);
                        }
                    } catch (URISyntaxException ue) {
                        SECURITY_LOGGER.tracef("Unable to construct URI from referral: %s", info);
                        throw SECURITY_LOGGER.nameNotFound(suppliedName);
                    }

                    DirContext context = currentConnectionHandler.getConnection();

                    attributes = context.getAttributes(distinguishedUserDN, searchControls.getReturningAttributes());
                }

                SearchResult result = null;
                if (attributes == null && searchEnumeration != null) {
                    /*
                     * If a referral has already been handled due to a LdapReferralException then the attributes would have been
                     * loaded after following the referral.
                     */

                    result = searchEnumeration.next();
                    if (result.isRelative() == false) {
                        /*
                         * In this scenario we have a result so any referral must have been followed automatically, we need to
                         * capture the address but we don't need to do anything with it at the moment.
                         */

                        String name = result.getName();

                        try {
                            URI fullUri = new URI(name);

                            referralAddress = new URI(fullUri.getScheme(), null, fullUri.getHost(), fullUri.getPort(), null,
                                    null, null);
                            distinguishedUserDN = fullUri.getPath().substring(1);
                            SECURITY_LOGGER.tracef("Received referral with address '%s' for dn '%s'",
                                    referralAddress.toString(), distinguishedUserDN);
                        } catch (URISyntaxException usi) {
                            SECURITY_LOGGER.tracef("Unable to construct URI from referral name: %s", name);
                            throw SECURITY_LOGGER.nameNotFound(suppliedName);
                        }
                    }
                    attributes = result.getAttributes();
                }
                if (attributes != null) {
                    if (distinguishedUserDN == null) {
                        Attribute dn = attributes.get(userDnAttribute);
                        if (dn != null) {
                            distinguishedUserDN = (String) dn.get();
                        }
                    }
                    if (usernameLoad != null) {
                        Attribute usernameAttr = attributes.get(usernameLoad);
                        if (usernameAttr != null) {
                            username = (String) usernameAttr.get();
                            SECURITY_LOGGER.tracef("Converted username '%s' to '%s'", suppliedName, username);
                        }
                    }
                }

                if (distinguishedUserDN == null && result != null) {
                    /*
                     * If this was a referral it would have been handled above.
                     */
                    distinguishedUserDN = result.getNameInNamespace();
                }

                if (username == null) {
                    throw SECURITY_LOGGER.usernameNotLoaded(suppliedName);
                }
                SECURITY_LOGGER.tracef("DN '%s' found for user '%s'", distinguishedUserDN, username);

                return new LdapEntry(username, distinguishedUserDN, referralAddress);
            } finally {
                if (searchEnumeration != null) {
                    try {
                        searchEnumeration.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }

    }



}
