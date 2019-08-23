/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2015, Red Hat Middleware LLC, and individual contributors
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
 *
 */

package org.jboss.as.domain.management.security;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.jboss.msc.service.StartException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.naming.NamingException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;


/**
 * WFCORE-2502
 *
 * Mock tests for class LdapCacheService, testing mainly attribute "cacheFailures" and impact from fix of WFCORE-2502.
 *
 * @author Jiri Ondrusek (jondruse@redhat.com)
 */
public class LdapCacheServiceMockTest {

    //mock ldap results
    private static final String USER_01 = "user01";
    private static final String USER_02 = "user02";
    private static final String USER_03 = "user03";
    private static final String USER_NOT_EXISTING = "user_not_existing";
    private static final String USER_01_SLEEP_01_SEC = "user01_sleep_1_sec";

    private static final LdapEntry USER_01_ENTRY = new LdapEntry(USER_01, "dn=user01");
    private static final LdapEntry USER_02_ENTRY = new LdapEntry(USER_02, "dn=user02");
    private static final LdapEntry USER_03_ENTRY = new LdapEntry(USER_03, "dn=user03");

    private LdapSearcher<LdapEntry, String> userSearcher;


    private enum CacheMode {
        BY_SEARCH, BY_ACCESS
    }

    @Before
    public void before() throws IOException, NamingException {
        userSearcher = mock(LdapSearcher.class);

        when(userSearcher.search(null, USER_01)).thenReturn(USER_01_ENTRY);
        when(userSearcher.search(null, USER_02)).thenReturn(USER_02_ENTRY);
        when(userSearcher.search(null, USER_03)).thenReturn(USER_03_ENTRY);

        when(userSearcher.search(null, USER_NOT_EXISTING)).thenThrow(new NamingException());

        when(userSearcher.search(null, USER_01_SLEEP_01_SEC)).thenAnswer(
                invocationOnMock -> {Thread.sleep(1_000);
            return  USER_01_ENTRY;});
    }

    /**
     * Case cacheFailures is false: failed search is not cached therefore following search is taken from cache.
     */
    @Test
    public void testWithoutFailures() throws StartException, IOException, NamingException {
        testCacheFailures(CacheMode.BY_ACCESS, false, 2);
        //number of accesses is added to previos number (so if number of calls has to b 2, we must add all previous calls and assert 4)
        testCacheFailures(CacheMode.BY_SEARCH, false, 4);
    }

    /**
     * Case cacheFailures is true: failed search is not cached therefore following search is taken from cache.
     */
    @Test
    public void testWithFailures() throws StartException, IOException, NamingException {
        testCacheFailures(CacheMode.BY_ACCESS, true, 3);
        //number of accesses is added to previos number (so if number of calls has to b 2, we must add all previous calls and assert 4)
        testCacheFailures(CacheMode.BY_SEARCH, true, 6);
    }

    /**
     * Test that fix doesn't break previous behaviour by moving insertion into cache after result is known.
     */
    @Test
    public void testParallelAccess() throws StartException, IOException, NamingException, InterruptedException, ExecutionException {
        testParallelAccess(CacheMode.BY_ACCESS, 1);
        testParallelAccess(CacheMode.BY_SEARCH, 2);
    }

    /**
     * Test difference of cache mode BY_SEARCH and BY_ACCESS.
     * Sequence of search for: 1 - U1,
     *                         2 - U2,
     *                         3 - U1,
     *                         4 - U3,
     *                         5 - U1
     * ByAccess and cache size 2: 1 - {U1, -}                        - search
     *                            2 - {U1, U2}                       - search
     *                            3 - {U2, U1} - U1 is last
     *                            4 - {U1, U3} - U2 is removed       - search
     *                            5 - {U3, U1} - U1 is last
     *
     * BySerch and cache size 2:  1 - {U1, -}                        - search
     *                            2 - {U1, U2}                       - search
     *                            3 - {U1, U2}
     *                            4 - {U2, U3} - U1 is removed       - search
     *                            5 - {U3, U1}                       - search
     */
    @Test
    public void testModes() throws StartException, IOException, NamingException, InterruptedException, ExecutionException {
        testModes(CacheMode.BY_ACCESS, 3);
        testModes(CacheMode.BY_SEARCH, 7);
    }

    private LdapSearcherCache<LdapEntry, String> startService(CacheMode mode, boolean cacheFailures, int size) throws StartException {
        return startService(mode,cacheFailures, size, 30);
    }

    private LdapSearcherCache<LdapEntry, String> startService(CacheMode mode, boolean cacheFailures, int size, int evictionTimeout) {
        LdapCacheService<LdapEntry, String> service;
        switch (mode) {
            case BY_ACCESS:
                service = LdapCacheService.createByAccessCacheService(NullConsumer.INSTANCE, userSearcher, evictionTimeout, cacheFailures, size);
                break;
            default:
                service = LdapCacheService.createBySearchCacheService(NullConsumer.INSTANCE, userSearcher, evictionTimeout, cacheFailures, size);
                break;
        }
        service.start(null);
        return service.cacheImplementation;
    }

    private static final class NullConsumer implements Consumer<LdapSearcherCache<LdapEntry, String>> {

        private static final NullConsumer INSTANCE = new NullConsumer();

        @Override
        public Consumer<LdapSearcherCache<LdapEntry, String>> andThen(final Consumer<? super LdapSearcherCache<LdapEntry, String>> after) {
            // ignored
            return null;
        }

        @Override
        public void accept(final LdapSearcherCache<LdapEntry, String> o) {
            // ignored
        }
    }

    private void testCacheFailures(CacheMode mode, boolean cacheFailure, int calls) throws StartException, IOException, NamingException {

        LdapSearcherCache<LdapEntry, String> cache = startService(mode, cacheFailure, 1);

        cache.search(null, USER_01);

        try {
            cache.search(null, USER_NOT_EXISTING);
            Assert.fail(String.format("User '%s' doesn't exist, search has to fail.", USER_NOT_EXISTING));
        } catch(NamingException e) {}

        cache.search(null, USER_01);

        verify(userSearcher, times(calls)).search(anyObject(), anyObject());
    }

    private void testParallelAccess(CacheMode mode, int calls) throws StartException, IOException, NamingException, InterruptedException, ExecutionException {
        int threads = 2;

        LdapSearcherCache<LdapEntry, String> cache = startService(mode, false, 1);

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        List<Callable<String>> callables = new ArrayList<>();
        for(int i = 0; i < threads; i++) {
            callables.add(()->{cache.search(null, USER_01_SLEEP_01_SEC); return "";});
        }


        executor.invokeAll(callables).stream()
                .map(future -> {
                    try {
                        return future.get();
                    }
                    catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                })
                .count();

        verify(userSearcher, times(calls)).search(anyObject(), anyObject());
    }

    private void testModes(CacheMode mode, int calls) throws StartException, IOException, NamingException, InterruptedException, ExecutionException {

        LdapSearcherCache<LdapEntry, String> cache = startService(mode, true, 2);

        cache.search(null, USER_01);
        cache.search(null, USER_02);
        cache.search(null, USER_01);
        cache.search(null, USER_03);
        cache.search(null, USER_01);

        verify(userSearcher, times(calls)).search(anyObject(), anyObject());
    }

    /**
     * Test of timeout evictions for cacheByAccessTime.
     *
     * Timeout in cache is set to 2 seconds. size is 2. Search for user01 followed by user02 should hit the ldap.
     * Repeated read of user01 should not hit ldap instead for user02 it shoud ht ldap.
     *
     * Last 4 calls test also size of cache.
     *
     */
    @Test
    public void testTimeoutsByAccessTime() throws StartException, IOException, NamingException, InterruptedException, ExecutionException {
        int timeout = 2;

        LdapSearcherCache<LdapEntry, String> cache = startService(CacheMode.BY_ACCESS, true, 2, timeout);

        //call to ldap should be expected (1)
        cache.search(null, USER_01);
        verify(userSearcher, times(1)).search(any(LdapConnectionHandler.class), anyString());

        //call to ldap should be expected (2)
        cache.search(null, USER_02);
        verify(userSearcher, times(2)).search(any(LdapConnectionHandler.class), anyString());

        //call to ldap should not be expected (2)
        Thread.sleep(1000);
        cache.search(null, USER_01);
        verify(userSearcher, times(2)).search(any(LdapConnectionHandler.class), anyString());

        //call to ldap should not be expected (2)
        Thread.sleep(1000);
        cache.search(null, USER_01);

        //call to ldap should not be expected (2)
        verify(userSearcher, times(2)).search(any(LdapConnectionHandler.class), anyString());//should not be expected (2)
        Thread.sleep(1000);
        cache.search(null, USER_01);
        verify(userSearcher, times(2)).search(any(LdapConnectionHandler.class), anyString());

        //call to ldap should be expected (3)
        Thread.sleep(1000);
        cache.search(null, USER_02);
        verify(userSearcher, times(3)).search(any(LdapConnectionHandler.class), anyString());


        Thread.sleep(timeout * 1000);


        //call to ldap should be expected (4)
        cache.search(null, USER_01);
        verify(userSearcher, times(4)).search(any(LdapConnectionHandler.class), anyString());
        //call to ldap should be expected (5)
        cache.search(null, USER_02);
        verify(userSearcher, times(5)).search(any(LdapConnectionHandler.class), anyString());
        //call to ldap should be expected (6)
        cache.search(null, USER_03);
        verify(userSearcher, times(6)).search(any(LdapConnectionHandler.class), anyString());
        //call to ldap should be expected (7)
        cache.search(null, USER_01);
        verify(userSearcher, times(7)).search(any(LdapConnectionHandler.class), anyString());
    }


}
