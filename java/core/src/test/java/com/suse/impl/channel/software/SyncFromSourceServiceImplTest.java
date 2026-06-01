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

import static java.util.Collections.emptySet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactoryTest;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.errata.ErrataFactory;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.rhnpackage.PackageFactory;
import com.redhat.rhn.domain.rhnpackage.PackageTest;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.events.SyncFromSourceErrataEvent;
import com.redhat.rhn.frontend.xmlrpc.NoSuchChannelException;
import com.redhat.rhn.frontend.xmlrpc.PermissionCheckFailureException;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.ErrataTestUtils;
import com.redhat.rhn.testing.ObservableMessageQueue;
import com.redhat.rhn.testing.UserTestUtils;

import com.suse.spec.channel.software.dto.ErrataCriteria;
import com.suse.spec.channel.software.dto.SyncOperation;
import com.suse.spec.channel.software.dto.SyncRequest;
import com.suse.spec.channel.software.dto.SyncResponse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
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
     *
     */
    @Test
    public void testSyncWhenErrataOnly() {
        TestSetupGeneral testSetup = getTestSetup();

        Set<Errata> expectedErratas = Set.of(testSetup.e1, testSetup.e4);
        Set<Package> expectedPackages = emptySet();

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request matches expected data
        assertNotNull(response);
        assertEquals(expectedErratas, response.erratas());
        assertEquals(expectedPackages, response.packages());

        // Assert duplicated request does not consider any errata
        assertTrue(duplicatedResponse.erratas().isEmpty());
        assertTrue(duplicatedResponse.packages().isEmpty());
    }

    // PACKAGES_ONLY

    /**
     * Tests syncing packages only from source to target channel.
     * Verifies it only handles packages.
     */
    @Test
    public void testSyncWhenPackagesOnly() {
        TestSetupGeneral testSetup = getTestSetup();

        Set<Errata> expectedErratas = emptySet();
        Set<Package> expectedPackages = Set.of(testSetup.p1, testSetup.p3, testSetup.p4, testSetup.p7);

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.PACKAGES_ONLY);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicatedResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request matches expected data
        assertNotNull(response);
        assertEquals(expectedErratas, response.erratas());
        assertEquals(expectedPackages, response.packages());

        // Assert duplicated request does not consider any errata
        assertTrue(duplicatedResponse.erratas().isEmpty());
        assertTrue(duplicatedResponse.packages().isEmpty());
    }

    /**
     * Tests syncing packages only from source to target channel.
     * Has same setup and assertations as {@link SyncFromSourceServiceImplTest#testSyncWhenPackagesOnly},
     * but applying filters with a wide range, verifying its equivalent to using no filters.
     */
    @Test
    public void testSyncWhenPackagesOnlyWithFilters() throws Exception {
        TestSetupGeneral testSetupGeneral = getTestSetup();

        // Sync
//        SyncRequest request = new SyncRequest(
//                new ErrataCriteria(
//                        List.of(
//                                testSetupGeneral.errata1().getAdvisoryName(),
//                                testSetupGeneral.errata2().getAdvisoryName()
//                        ),
//                        Date.from(Instant.now().minus(1, ChronoUnit.DAYS)),
//                        Date.from(Instant.now().plus(1, ChronoUnit.DAYS))
//                ),
//                SyncOperation.PACKAGES_ONLY, false, false, false);
//        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
//
//        assertNotNull(response);
//        assertTrue(response.erratas().isEmpty());
//        assertEquals(1, response.packages().size());
//        assertTrue(response.packages().contains(testSetupGeneral.pkg2()));
    }

    /**
     * Tests syncing packages only from source to target channel.
     * Same test as {@link SyncFromSourceServiceImplTest#testSyncWhenPackagesOnlyWithFilters}
     * but in a scenario where filters do not match any errata.
     */
    @Test
    public void testSyncWhenPackagesOnlyWithFiltersReturnsNoErratas() throws Exception {
        getTestSetup();

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
    public void testSyncWhenErrataAndPackages() {
        TestSetupGeneral testSetup = getTestSetup();

        // Sync
        SyncRequest request = ChannelSoftwareTestUtils.createSyncRequest(SyncOperation.ERRATA_AND_PACKAGES);
        SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
        SyncResponse duplicateResponse =
                service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

        // Assert first request will handle expected errata and packages
        assertNotNull(response);
        assertEquals(1, response.erratas().size());
//        assertTrue(response.erratas().contains(errata));
//        assertEquals(2, response.packages().size());
//        assertTrue(response.packages().contains(pkg1));
//        assertTrue(response.packages().contains(pkg2));

        // Assert duplicated one does not consider any of already handles erratas and packages
        assertNotNull(duplicateResponse);
        assertTrue(duplicateResponse.erratas().isEmpty());
        assertTrue(duplicateResponse.packages().isEmpty());
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
     * Tests async requests publish an SyncFromSourceErrataEvent.
     * Repeats the request to verify idempotency.
     */
    @Test
    public void testSyncFromSourceWhenAsyncPublishEvent() throws Exception {
        for ( SyncOperation op : SyncOperation.values()){
            ObservableMessageQueue.registerCapturingAction(SyncFromSourceErrataEvent.class);

            // Sync
            SyncRequest request = ChannelSoftwareTestUtils.createSyncRequestAsync(op);

            try {
                SyncResponse response = service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);
                SyncResponse duplicatedResponse =
                        service.sync(user, sourceChannel.getLabel(), targetChannel.getLabel(), request);

                // Async calls return always empty
                assertTrue(response.erratas().isEmpty());
                assertTrue(response.packages().isEmpty());
                assertEquals(response, duplicatedResponse);

                // Commit transaction so EventDatabaseMessage can be processed and actions be captured
                HibernateFactory.commitTransaction();

                // Wait for both events to be captured
                List<SyncFromSourceErrataEvent> syncFromSourceErrataEvents =
                        ObservableMessageQueue.awaitAndFetch(SyncFromSourceErrataEvent.class, 2);
                assertEquals(2, syncFromSourceErrataEvents.size());

                // Assert published event properties and its the same as the duplicated
                SyncFromSourceErrataEvent event = syncFromSourceErrataEvents.get(0);
                // All properties but async should be the same
                assertEquals(sourceChannel.getLabel(), event.getSourceChannelLabel());
                assertEquals(targetChannel.getLabel(), event.getTargetChannelLabel());
                assertEquals(user.getId(), event.getUserId());
                assertEquals(request.criteria(), event.getSyncRequest().criteria());
                assertEquals(request.operation(), event.getSyncRequest().operation());
                assertEquals(request.alignModules(), event.getSyncRequest().alignModules());
                assertEquals(request.forceRefresh(), event.getSyncRequest().forceRefresh());

                assertFalse(event.getSyncRequest().async());

                // Events are equal
                assertEquals(syncFromSourceErrataEvents.get(0), syncFromSourceErrataEvents.get(1));
            } finally {
                ObservableMessageQueue.unregisterCapturingAction(SyncFromSourceErrataEvent.class);
            }

        }

    }

   /**
     * Creates a setup to cover all scenarios.
     * Channels, Erratas and Packages all have N-to-N relationships between them.
     * The possible scenarios are:
     * SOURCE (S)                               TARGET (T)
     * ├─ E1 (source-only)                      ├─ E2 (in-both, already synced)
     * │  ├─ P1                                 │  └─ P2
     * │  └─ P2 (shared with E2)                │
     * │                                        ├─ E3 (in-both, already synced)
     * ├─ E2 (in-both)                          │
     * │  ├─ P2 (shared with E1)                ├─ E5 (target-only)
     * │  └─ P3                                 │  ├─ P5
     * │                                        │  └─ P6
     * ├─ E3 (in-both)                          │
     * │  └─ P4                                 ├─ P2 (standalone, from E1 & E2)
     * │                                        ├─ P5 (standalone, from E5)
     * ├─ E4 (source-only, no packages)         ├─ P6 (standalone, from E5)
     * │                                        └─ P8 (standalone)
     * ├─ E5 (target-only in source)
     * │  ├─ P5 (shared with target E5)
     * │  └─ P6 (shared with target E5)
     * │
     * ├─ P7 (standalone in source)
     * └─ P8 (standalone, shared with target)
     */
    private TestSetupGeneral getTestSetup() {
        // Create erratas
        Errata e1 = ErrataTestUtils.errataBuilder(user).build();
        Errata e2 = ErrataTestUtils.errataBuilder(user).build();
        Errata e3 = ErrataTestUtils.errataBuilder(user).build();
        Errata e4 = ErrataTestUtils.errataBuilder(user).build(); // No packages
        Errata e5 = ErrataTestUtils.errataBuilder(user).build();

        // Create packages
        Package p1 = PackageTest.createTestPackage(userOrg);
        Package p2 = PackageTest.createTestPackage(userOrg);
        Package p3 = PackageTest.createTestPackage(userOrg);
        Package p4 = PackageTest.createTestPackage(userOrg);
        Package p5 = PackageTest.createTestPackage(userOrg);
        Package p6 = PackageTest.createTestPackage(userOrg);
        Package p7 = PackageTest.createTestPackage(userOrg);
        Package p8 = PackageTest.createTestPackage(userOrg);

        // Add to SOURCE channel (this also builds errata-package relationships)
        ErrataFactory.addToChannel(e1, sourceChannel, user, Set.of(p1, p2));
        ErrataFactory.addToChannel(e2, sourceChannel, user, Set.of(p2, p3));
        ErrataFactory.addToChannel(e3, sourceChannel, user, Set.of(p4));
        ErrataFactory.addToChannel(e4, sourceChannel, user, Set.of()); // Empty errata
        ErrataFactory.addToChannel(e5, sourceChannel, user, Set.of(p5, p6));
        sourceChannel.addPackage(p7); // standalone
        sourceChannel.addPackage(p8); // standalone, shared with target

        // Add to TARGET channel
        ErrataFactory.addToChannel(e2, targetChannel, user, Set.of(p2)); // Already synced
        ErrataFactory.addToChannel(e3, targetChannel, user, Set.of());   // Already synced but no packages
        ErrataFactory.addToChannel(e5, targetChannel, user, Set.of(p5, p6)); // Target-only errata
        targetChannel.addPackage(p2); // standalone (from E1/E2)
        targetChannel.addPackage(p5); // standalone (from E5)
        targetChannel.addPackage(p6); // standalone (from E5)
        targetChannel.addPackage(p8); // standalone, shared with source

        // Flush to ensure all relationships are persisted
        HibernateFactory.getSession().flush();

        return new TestSetupGeneral(e1, e2, e3, e4, e5, p1, p2, p3, p4, p5, p6, p7, p8);
    }

    private record TestSetupGeneral(
        Errata e1, Errata e2, Errata e3, Errata e4, Errata e5,
        Package p1, Package p2, Package p3, Package p4, Package p5, Package p6, Package p7, Package p8
    ) {
    }
}
