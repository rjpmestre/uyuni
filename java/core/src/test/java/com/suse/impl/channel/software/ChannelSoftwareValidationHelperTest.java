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

import static com.redhat.rhn.domain.role.RoleFactory.ORG_ADMIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.redhat.rhn.common.UyuniErrorReport;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactoryTest;
import com.redhat.rhn.domain.role.RoleFactory;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.InvalidChannelException;
import com.redhat.rhn.frontend.xmlrpc.NoSuchChannelException;
import com.redhat.rhn.frontend.xmlrpc.PermissionCheckFailureException;
import com.redhat.rhn.testing.BaseTestCaseWithUser;
import com.redhat.rhn.testing.TestUtils;
import com.redhat.rhn.testing.UserTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Date;

/**
 * Tests for ChannelSoftwareValidationHelper.
 */
public class ChannelSoftwareValidationHelperTest extends BaseTestCaseWithUser {

    private User admin;

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
        admin = UserTestUtils.createUser();
        admin.addPermanentRole(RoleFactory.CHANNEL_ADMIN);
        UserTestUtils.addUserRole(admin, RoleFactory.CHANNEL_ADMIN);
    }

    //validateAndLookupChannel
    @Test
    public void testValidateAndLookupChannelValidChannel() {
        Channel channel = ChannelFactoryTest.createTestChannel(admin);

        Channel result = ChannelSoftwareValidationHelper.validateAndLookupChannel(admin, channel.getLabel());

        assertNotNull(result);
        assertEquals(channel.getId(), result.getId());
    }

    @Test
    public void testValidateAndLookupChannelInvalidLabel() {
        assertThrows(NoSuchChannelException.class, () ->
            ChannelSoftwareValidationHelper.validateAndLookupChannel(admin, "non-existent-channel")
        );
    }

    // validateUserHasPermission
    @Test
    public void testValidateUserHasPermissionWithPermission() {
        Channel channel = ChannelFactoryTest.createTestChannel(admin);
        admin.addPermanentRole(ORG_ADMIN);

        // Should not throw
        ChannelSoftwareValidationHelper.validateUserHasPermission(admin, channel);
    }

    @Test
    public void testValidateUserHasPermissionNoPermission() {
        Channel channel = ChannelFactoryTest.createTestChannel(admin);

        assertThrows(PermissionCheckFailureException.class, () ->
            ChannelSoftwareValidationHelper.validateUserHasPermission(user, channel)
        );
    }

    // validateChannelIsCloned
    @Test
    public void testValidateChannelIsClonedClonedChannel() {
        Channel original = ChannelFactoryTest.createTestChannel(admin);
        Channel cloned = ChannelFactoryTest.createTestClonedChannel(original, admin);

        // Should not throw
        ChannelSoftwareValidationHelper.validateChannelIsCloned(cloned);
    }

    @Test
    public void testValidateChannelIsCloned() {
        Channel channel = ChannelFactoryTest.createTestChannel(admin);

        InvalidChannelException ex = assertThrows(InvalidChannelException.class, () ->
            ChannelSoftwareValidationHelper.validateChannelIsCloned(channel)
        );
        assertTrue(ex.getMessage().contains("is not cloned"));
    }

    // validateOriginalChannelAccessible
    @Test
    public void testValidateOriginalChannelAccessibleValidOriginal() {
        Channel original = ChannelFactoryTest.createTestChannel(admin);

        // Should not throw
        ChannelSoftwareValidationHelper.validateOriginalChannelAccessible(original, null);
    }

    @Test
    public void testValidateOriginalChannelAccessibleNullOriginal() {
        InvalidChannelException ex = assertThrows(InvalidChannelException.class, () ->
            ChannelSoftwareValidationHelper.validateOriginalChannelAccessible(null, "target-label")
        );
        assertEquals("Cannot access original channel for: target-label", ex.getMessage());
    }

    // validateRequestFields

    @Test
    public void testValidateRequestFieldsWithMinimalData() {
        UyuniErrorReport report = ChannelSoftwareValidationHelper.validateRequestFields(
                TestUtils.randomString(),
                null,
                null,
                null,
                false
        );

        assertFalse(report.hasErrors());
    }

    @Test
    public void testValidateRequestFieldsWillFullData() {
        Instant now = Instant.now();
        UyuniErrorReport report = ChannelSoftwareValidationHelper.validateRequestFields(
                TestUtils.randomString(),
                TestUtils.randomString(),
                Date.from(now.minusSeconds(1)),
                Date.from(now),
                true
        );

        assertFalse(report.hasErrors());
    }

    @Test
    public void testValidateRequestFieldsWhenValidationsFail() {
        Instant now = Instant.now();
        UyuniErrorReport report = ChannelSoftwareValidationHelper.validateRequestFields(
                null,
                null,
                Date.from(now),
                Date.from(now.minusSeconds(1)),
                true
        );

        assertTrue(report.hasErrors());
        String[] errorMessages = report.getErrorMessages();
        assertEquals(3,  errorMessages.length);
        assertTrue(Arrays.stream(errorMessages).anyMatch(s -> s.contains("Target channel label is required")));
        assertTrue(Arrays.stream(errorMessages).anyMatch(s -> s.contains("Source channel label is required")));
        assertTrue(Arrays.stream(errorMessages).anyMatch(s -> s.contains("End date cannot be before start date")));
    }

}
