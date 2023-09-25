<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ Copyright The WildFly Authors
  ~ SPDX-License-Identifier: Apache-2.0
  -->

<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

    <xsl:output indent="yes"/>

    <xsl:variable name="jboss" select="'urn:jboss:domain:'"/>
    <xsl:variable name="datasources" select="'urn:jboss:domain:datasources:'"/>

    <xsl:template match="//*[local-name()='management' and starts-with(namespace-uri(), $jboss)]">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>

            <xsl:element name="outbound-connections" namespace="{namespace-uri()}">
                <xsl:element name="ldap" namespace="{namespace-uri()}">
                    <xsl:attribute name="name">ldap</xsl:attribute>
                    <xsl:attribute name="url">ldap://localhost:10389</xsl:attribute>
                </xsl:element>
            </xsl:element>

        </xsl:copy>
    </xsl:template>

    <xsl:template match="//*[local-name()='management' and starts-with(namespace-uri(), $jboss)]
                          /*[local-name()='security-realms']
                          /*[local-name()='security-realm' and @name='ManagementRealm']">
        <xsl:copy>
            <xsl:apply-templates select="@*"/>

            <xsl:element name="authentication" namespace="{namespace-uri()}">
                <xsl:element name="local" namespace="{namespace-uri()}">
                    <xsl:attribute name="default-user">UserMappedToGroupSuperUser</xsl:attribute>
                </xsl:element>
                <xsl:element name="ldap" namespace="{namespace-uri()}">
                    <xsl:attribute name="connection">ldap</xsl:attribute>
                    <xsl:attribute name="base-dn">ou=Users,dc=wildfly,dc=org</xsl:attribute>
                    <xsl:attribute name="user-dn">dn</xsl:attribute>
                    <xsl:element name="username-filter" namespace="{namespace-uri()}">
                        <xsl:attribute name="attribute">uid</xsl:attribute>
                    </xsl:element>
                </xsl:element>
            </xsl:element>
            <xsl:element name="authorization" namespace="{namespace-uri()}">
                <xsl:attribute name="map-groups-to-roles">false</xsl:attribute>
                <xsl:element name="ldap" namespace="{namespace-uri()}">
                    <xsl:attribute name="connection">ldap</xsl:attribute>
                    <xsl:element name="username-to-dn" namespace="{namespace-uri()}">
                        <xsl:attribute name="force">false</xsl:attribute>
                        <xsl:element name="username-filter" namespace="{namespace-uri()}">
                            <xsl:attribute name="base-dn">ou=Users,dc=wildfly,dc=org</xsl:attribute>
                            <xsl:attribute name="user-dn-attribute">dn</xsl:attribute>
                            <xsl:attribute name="attribute">uid</xsl:attribute>
                        </xsl:element>
                    </xsl:element>
                    <xsl:element name="group-search" namespace="{namespace-uri()}">
                        <xsl:attribute name="group-name">SIMPLE</xsl:attribute>
                        <xsl:attribute name="group-dn-attribute">dn</xsl:attribute>
                        <xsl:attribute name="group-name-attribute">cn</xsl:attribute>
                        <xsl:element name="group-to-principal" namespace="{namespace-uri()}">
                            <xsl:attribute name="base-dn">ou=Groups,dc=wildfly,dc=org</xsl:attribute>
                            <xsl:attribute name="search-by">DISTINGUISHED_NAME</xsl:attribute>
                            <xsl:element name="membership-filter" namespace="{namespace-uri()}">
                                <xsl:attribute name="principal-attribute">member</xsl:attribute>
                            </xsl:element>
                        </xsl:element>
                    </xsl:element>
                </xsl:element>
            </xsl:element>

        </xsl:copy>
    </xsl:template>

    <!-- Copy everything else. -->
    <xsl:template match="node()|@*">
        <xsl:copy>
            <xsl:apply-templates select="node()|@*"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet>
