/*
 * Copyright (c) 2026 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 */
package com.suse.impl.channel.software;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactoryTest;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.errata.ErrataFactory;
import com.redhat.rhn.domain.errata.ErrataTestBuilder;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.rhnpackage.PackageTest;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.NoSuchChannelException;
import com.redhat.rhn.frontend.xmlrpc.PermissionCheckFailureException;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.ErrataTestUtils;
import com.redhat.rhn.testing.UserTestUtils;

import com.suse.spec.channel.software.dto.ErrataCriteria;
import com.suse.spec.channel.software.dto.SyncOperation;
import com.suse.spec.channel.software.dto.SyncRequest;
import com.suse.spec.channel.software.dto.SyncResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * Tests for SyncFromSourceServiceImpl
 */
public class SyncFromSourceServiceImplTest extends BaseTestCaseWithUser {

    private SyncFromSourceServiceImpl service;
    private Channel sourceChannel;
    private Channel targetChannel;
    private Org userOrg;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        service = new SyncFromSourceServiceImpl();
        sourceChannel = ChannelFactoryTest.createTestChannel(user);
        targetChannel = ChannelFactoryTest.createTestChannel(user);
        userOrg = user.getOrg();
    }

    /**
     * Tests that sync with invalid source channel label throws NoSuchChannelException.
     */
    @Test
    public void testFailSyncWhenInvalidSourceChannel() {
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_ONLY);

        NoSuchChannelException exception = assertThrows(NoSuchChannelException.class, () ->
                service.sync(user, "invalid-channel", targetChannel.getLabel(), request)
        );
        assertEquals("No such channel: invalid-channel", exception.getMessage());
    }

    /**
     * Tests that sync with invalid target channel label throws NoSuchChannelException.
     */
    @Test
    public void testFailSyncWhenInvalidTargetChannel() {
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_ONLY);

        NoSuchChannelException exception = assertThrows(NoSuchChannelException.class, () ->
                service.sync(user, sourceChannel.getLabel(), "invalid-channel", request)
        );
        assertEquals("No such channel: invalid-channel", exception.getMessage());
    }

    /**
     * Tests that sync without permission on target channel throws PermissionCheckFailureException.
     */
    @Test
    public void testFailSyncWhenNoPermissionOnTargetChannel() {
        // Create a user in same org but without channel admin permissions
        User otherUser = new UserTestUtils.UserBuilder().orgId(userOrg.getId()).build();
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_ONLY);

        PermissionCheckFailureException exception = assertThrows(PermissionCheckFailureException.class, () ->
                service.sync(otherUser, sourceChannel.getLabel(), targetChannel.getLabel(), request)
        );
        assertTrue(exception.getMessage().contains("User does not have permission to modify channel"));
    }

    // ERRATA_ONLY

    /**
     * Tests syncing erratas only from source to target channel.
     * Verifies it only handles erratas.
     */
    @Test
    public void testSyncWhenErrataOnly() throws Exception {
        // Create errata with a package in source channel
        Errata errata = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();
        Package pkg = PackageTest.createTestPackage(userOrg);
        errata.addPackage(pkg);
        ErrataFactory.addToChannel(errata, sourceChannel, user, Set.of(pkg));

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request only handles expected errata
        assertNotNull(response);
        assertEquals(1, response.erratas().size());
        assertTrue(response.erratas().contains(errata));
        assertTrue(response.packages().isEmpty());

        // Assert duplicated one does not consider that errata anymore
        assertTrue(duplicatedResponse.erratas().isEmpty());
        assertTrue(duplicatedResponse.packages().isEmpty());
    }

    /**
     * Tests syncing erratas only from source to target channel asynchronously.
     * Repeats same setup as {@link SyncFromSourceServiceImplTest#testSyncWhenErrataOnly}.
     */
    @Test
    public void testSyncWhenErrataOnlyAsynchronously() throws Exception {
        // Create errata with a package in source channel
        Errata errata = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();
        Package pkg = PackageTest.createTestPackage(userOrg);
        errata.addPackage(pkg);
        ErrataFactory.addToChannel(errata, sourceChannel, user, Set.of(pkg));

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequestAsync(SyncOperation.ERRATA_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request only handles expected errata
        assertNotNull(response);
        assertEquals(1, response.erratas().size());
        assertTrue(response.erratas().contains(errata));
        assertTrue(response.packages().isEmpty());

        // Assert duplicates of response will be the same in async
        assertEquals(response, duplicatedResponse);
    }

    // PACKAGES_ONLY

    /**
     * Tests syncing packages only from source to target channel.
     * Verifies it only handles packages.
     */
    @Test
    public void testSyncWhenPackagesOnly() throws Exception {
        TestSetupPackagesOnly testSetupPackagesOnly = getTestSetupPackagesOnly();

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.PACKAGES_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request only handles expected package
        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertEquals(1, response.packages().size());
        assertTrue(response.packages().contains(testSetupPackagesOnly.pkg2()));

        // Assert duplicated one does not consider that package anymore
        assertTrue(duplicatedResponse.erratas().isEmpty());
        assertTrue(duplicatedResponse.packages().isEmpty());
    }

    /**
     * Tests syncing packages only from source to target channel asynchronously.
     * Repeats same setup as {@link SyncFromSourceServiceImplTest#testSyncWhenPackagesOnly}.
     */
    @Test
    public void testSyncWhenPackagesOnlyAsynchronously() throws Exception {
        TestSetupPackagesOnly testSetupPackagesOnly = getTestSetupPackagesOnly();

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequestAsync(SyncOperation.PACKAGES_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request only handles expected package
        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertEquals(1, response.packages().size());
        assertTrue(response.packages().contains(testSetupPackagesOnly.pkg2()));

        // Assert duplicates of response will be the same in async
        assertEquals(response, duplicatedResponse);
    }

    /**
     * Tests syncing packages only from source to target channel.
     * Has same setup and assertations as {@link SyncFromSourceServiceImplTest#testSyncWhenPackagesOnly},
     * but applying filters with a wide range, verifying its equivalent to using no filters.
     */
    @Test
    public void testSyncWhenPackagesOnlyWithFilters() throws Exception {
        TestSetupPackagesOnly testSetupPackagesOnly = getTestSetupPackagesOnly();

        // Sync
        SyncRequest request = new SyncRequest(
                new ErrataCriteria(
                        List.of(
                                testSetupPackagesOnly.errata1().getAdvisoryName(),
                                testSetupPackagesOnly.errata2().getAdvisoryName()
                        ),
                        Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
                        Date.from(Instant.now().plus(1, ChronoUnit.DAYS))
                ),
                SyncOperation.PACKAGES_ONLY, false, false, false);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertEquals(1, response.packages().size());
        assertTrue(response.packages().contains(testSetupPackagesOnly.pkg2()));
    }

    /**
     * Tests syncing packages only from source to target channel.
     * Same test as {@link SyncFromSourceServiceImplTest#testSyncWhenPackagesOnlyWithFilters}
     * but in a scenario where filters do not match any errata.
     */
    @Test
    public void testSyncWhenPackagesOnlyWithFiltersReturnsNoErratas() throws Exception {
        getTestSetupPackagesOnly();

        // Sync
        SyncRequest request = new SyncRequest(
                new ErrataCriteria(
                        null, Date.from(Instant.now().plus(1, ChronoUnit.DAYS)), null
                ),
                SyncOperation.PACKAGES_ONLY, false, false, false);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertTrue(response.packages().isEmpty());
    }

    /**
     * Tests sync when source and target have identical packages.
     * Verifies that package difference calculation returns empty set.
     */
    @Test
    public void testSyncPackageOnlyWhenIdenticalPackagesReturnsEmpty() {
        // Add same packages to both channels
        Package pkg = PackageTest.createTestPackage(userOrg);
        sourceChannel.getPackages().add(pkg);
        targetChannel.getPackages().add(pkg);

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.PACKAGES_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertTrue(response.packages().isEmpty());
    }

    // ERRATA_AND_PACKAGES

    /**
     * Tests syncing both erratas and packages from source to target channel.
     * Verifies that:
     * - both erratas and packages handled
     * - repeating the sync will not clone again neither erratas nor packages
     */
    @Test
    public void testSyncWhenErrataAndPackages() throws Exception {
        // Create errata with packages in source channel
        Errata errata = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();
        Package pkg1 = PackageTest.createTestPackage(userOrg);
        Package pkg2 = PackageTest.createTestPackage(userOrg);
        errata.addPackage(pkg1);
        errata.addPackage(pkg2);
        ErrataFactory.addToChannel(errata, sourceChannel, user, Set.of(pkg1, pkg2));

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_AND_PACKAGES);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicateResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request will handle expected errata and packages
        assertNotNull(response);
        assertEquals(1, response.erratas().size());
        assertTrue(response.erratas().contains(errata));
        assertEquals(2, response.packages().size());
        assertTrue(response.packages().contains(pkg1));
        assertTrue(response.packages().contains(pkg2));

        // Assert duplicated one does not consider any of already handles erratas and packages
        assertNotNull(duplicateResponse);
        assertTrue(duplicateResponse.erratas().isEmpty());
        assertTrue(duplicateResponse.packages().isEmpty());
    }

    /**
     * Tests syncing asynchronously.
     * Repeats same setup as {@link SyncFromSourceServiceImplTest#testSyncWhenErrataAndPackages}.
     */
    @Test
    public void testSyncWhenErrataAndPackagesAsynchronously() throws Exception {
        // Create errata with packages in source channel
        Errata errata = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();
        Package pkg1 = PackageTest.createTestPackage(userOrg);
        Package pkg2 = PackageTest.createTestPackage(userOrg);
        errata.addPackage(pkg1);
        errata.addPackage(pkg2);
        ErrataFactory.addToChannel(errata, sourceChannel, user, Set.of(pkg1, pkg2));

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequestAsync(SyncOperation.ERRATA_AND_PACKAGES);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request will only return the queued erratas but not any package details
        assertNotNull(response);
        assertEquals(1, response.erratas().size());
        assertTrue(response.erratas().contains(errata));
        assertTrue(response.packages().isEmpty());

        // Assert duplicates of response will be the same in async
        assertEquals(response, duplicatedResponse);
    }

    /**
     * Tests sync when source channel has no erratas.
     * Verifies that response contains empty sets.
     */
    @Test
    public void testSyncErrataAndPackagesWhenSourceChannelHasNoErrata() {
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_AND_PACKAGES);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        assertNotNull(response);
        assertTrue(response.erratas().isEmpty());
        assertTrue(response.packages().isEmpty());
    }

    /**
     * Creates a setup with 2 erratas with a package each.
     * Target channel will have one of the errata (and package)
     * Source channel will have both
     */
    private TestSetupPackagesOnly getTestSetupPackagesOnly() throws Exception {
        // Create 2 erratas with 1 package each
        Errata errata1 = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();
        Errata errata2 = new ErrataTestBuilder().orgId(user.getOrg().getId()).buildAndSave();

        // Add 1 package to each errata
        Package pkg1 = PackageTest.createTestPackage(userOrg);
        Package pkg2 = PackageTest.createTestPackage(userOrg);
        errata1.addPackage(pkg1);
        errata2.addPackage(pkg2);

        // Target channel has one of the packages
        ErrataFactory.addToChannel(errata1, targetChannel, user, Set.of(pkg1));

        // Source channels has both
        ErrataFactory.addToChannel(errata1, sourceChannel, user, Set.of(pkg1));
        ErrataFactory.addToChannel(errata2, sourceChannel, user, Set.of(pkg2));

        return new TestSetupPackagesOnly(errata1, errata2, pkg2);
    }

    private record TestSetupPackagesOnly(Errata errata1, Errata errata2, Package pkg2) {
    }
}
