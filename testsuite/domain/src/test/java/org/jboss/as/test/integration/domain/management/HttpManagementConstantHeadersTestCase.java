/*
 * Copyright 2019 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.as.test.integration.domain.management;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;

import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.log4j.Logger;
import org.jboss.as.test.http.Authentication;
import org.jboss.as.test.integration.domain.management.util.DomainTestSupport;
import org.jboss.as.test.integration.domain.suites.CLITestSuite;
import org.jboss.as.test.integration.management.base.AbstractCliTestBase;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test case to test custom / constant headers are applied to existing contexts.
 * See WFCORE-1110.
 *
 * @author <a href="mailto:tterem@redhat.com">Tomas Terem</a>
 */
public class HttpManagementConstantHeadersTestCase extends AbstractCliTestBase {

   private static final int MGMT_PORT = 9990;
   private static final String MGMT_CTX = "/management";
   private static final String ERROR_CTX = "/error";

   private URL managementUrl;
   private URL errorUrl;
   private HttpClient httpClient;

   @BeforeClass
   public static void before() throws Exception {
      CLITestSuite.createSupport(HttpManagementConstantHeadersTestCase.class.getSimpleName());
      AbstractCliTestBase.initCLI(DomainTestSupport.masterAddress);
   }

   @AfterClass
   public static void after() throws Exception {
      AbstractCliTestBase.closeCLI();
      CLITestSuite.stopSupport();
   }

   @Before
   public void createClient() throws Exception {
      this.managementUrl = new URL("http", DomainTestSupport.masterAddress, MGMT_PORT, MGMT_CTX);
      this.errorUrl = new URL("http", DomainTestSupport.masterAddress, MGMT_PORT, ERROR_CTX);

      CredentialsProvider credsProvider = new BasicCredentialsProvider();
      credsProvider.setCredentials(new AuthScope(managementUrl.getHost(), managementUrl.getPort()), new UsernamePasswordCredentials(Authentication.USERNAME, Authentication.PASSWORD));

      this.httpClient = HttpClients.custom()
            .setDefaultCredentialsProvider(credsProvider)
            .build();
   }

   @After
   public void closeClient() {
      if (httpClient instanceof Closeable) {
         try {
            ((Closeable) httpClient).close();
         } catch (IOException e) {
            Logger.getLogger(HttpManagementConstantHeadersTestCase.class).error("Failed closing client", e);
         }
      }
   }

   @After
   public void removeHeaders() {
      cli.sendLine("/host=master/core-service=management/management-interface=http-interface:undefine-attribute(name=constant-headers)");
      cli.sendLine("reload --host=master");
   }

   /**
    * Test that a call to the '/management' endpoint returns the expected headers.
    */
   @Test
   public void testManagement() throws Exception {
      configureHeaders();

      HttpGet get = new HttpGet(managementUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader("X-All");
      assertEquals("All", header.getValue());

      header = response.getFirstHeader("X-Management");
      assertEquals("Management", header.getValue());

      header = response.getFirstHeader("X-Error");
      assertNull("Header X-Error Unexpected", header);
   }

   /**
    * Test that a call to the '/error' endpoint returns the expected headers.
    */
   @Test
   public void testError() throws Exception {
      configureHeaders();

      HttpGet get = new HttpGet(errorUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader("X-All");
      assertEquals("All", header.getValue());

      header = response.getFirstHeader("X-Error");
      assertEquals("Error", header.getValue());

      header = response.getFirstHeader("X-Management");
      assertNull("Header X-Management Unexpected", header);
   }

   /**
    * Test that security headers can be configured and are returned from '/management' endpoint
    */
   @Test
   public void testSecurity() throws Exception {
      configureSecurityHeaders();

      HttpGet get = new HttpGet(managementUrl.toURI().toString());
      HttpResponse response = httpClient.execute(get);
      assertEquals(200, response.getStatusLine().getStatusCode());

      Header header = response.getFirstHeader("X-XSS-Protection");
      assertEquals("1; mode=block", header.getValue());

      header = response.getFirstHeader("X-Frame-Options");
      assertEquals("SAMEORIGIN", header.getValue());

      header = response.getFirstHeader("Content-Security-Policy");
      assertEquals("default-src https: data: 'unsafe-inline' 'unsafe-eval'", header.getValue());

      header = response.getFirstHeader("Strict-Transport-Security");
      assertEquals("max-age=31536000; includeSubDomains;", header.getValue());

      header = response.getFirstHeader("X-Content-Type-Options");
      assertEquals("nosniff", header.getValue());
   }

   /**
    * Configure one header to be applied to all endpoints (X-All), one to be applied only to /management endpoints
    * (X-Management) and one to be applied only to /error endpoint (X-Error)
    */
   private static void configureHeaders() {
      cli.sendLine("/host=master/core-service=management/management-interface=http-interface:write-attribute" +
            "(name=constant-headers, value=[" +
            "{path=/, headers=[{name=X-All, value=All}]}," +
            "{path=/management, headers=[{name=X-Management, value=Management}]}," +
            "{path=/error, headers=[{name=X-Error, value=Error}]}" +
            "])");
      cli.sendLine("reload --host=master");
   }

   /**
    * Configure security headers for /management endpoint.
    */
   private static void configureSecurityHeaders() {
      cli.sendLine("/host=master/core-service=management/management-interface=http-interface:write-attribute" +
            "(name=constant-headers, value=[" +
            "{path=/management, headers=[" +
            "{name=X-XSS-Protection, value=1; mode=block}," +
            "{name=X-Frame-Options, value=SAMEORIGIN}," +
            "{name=Content-Security-Policy, value=default-src https: data: 'unsafe-inline' 'unsafe-eval'}," +
            "{name=Strict-Transport-Security, value=max-age=31536000; includeSubDomains;}," +
            "{name=X-Content-Type-Options, value=nosniff}" +
            "]}])");
      cli.sendLine("reload --host=master");
   }
}
