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

import com.redhat.rhn.common.security.PermissionException;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.channel.ChannelFactory;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.errata.ErrataFactory;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.NoSuchChannelException;
import com.redhat.rhn.manager.channel.ChannelManager;
import com.redhat.rhn.manager.errata.ErrataManager;
import com.redhat.rhn.manager.errata.cache.ErrataCacheManager;
import com.redhat.rhn.manager.user.UserManager;

import com.suse.spec.channel.software.SyncFromSourceService;
import com.suse.spec.channel.software.dto.ErrataCriteria;
import com.suse.spec.channel.software.dto.SyncOperation;
import com.suse.spec.channel.software.dto.SyncRequest;
import com.suse.spec.channel.software.dto.SyncResponse;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of SyncFromSourceService that unifies errata and package syncing operations.
 * Supports three modes:
 * - ERRATA_ONLY: Sync erratas without their packages
 * - PACKAGES_ONLY: Sync packages without erratas
 * - ERRATA_AND_PACKAGES: Sync both erratas and their associated packages
 */
public class SyncFromSourceServiceImpl implements SyncFromSourceService {

    private static final Logger LOG = LogManager.getLogger(SyncFromSourceServiceImpl.class);

    @Override
    public SyncResponse sync(
            User user,
            String sourceChannelLabel,
            String targetChannelLabel,
            SyncRequest request
    ) {
        Channel sourceChannel = lookupChannel(user, sourceChannelLabel);
        Channel targetChannel = lookupChannel(user, targetChannelLabel);

        validateChannelAccess(user, targetChannel);

        Set<Errata> syncedErratas = Collections.emptySet();
        Set<Package> syncedPackages = Collections.emptySet();

        if (request.operation().includesErratas()) {
            syncedErratas = syncErratas(user, sourceChannel, targetChannel, request.criteria());
        }

        if (request.operation().includesPackages()) {
            syncedPackages = syncPackages(user, sourceChannel, targetChannel, syncedErratas,
                request.operation(), request.alignModules());
        }

        updateErrataCacheIfNeeded(targetChannel, syncedPackages);

        return new SyncResponse(syncedErratas, syncedPackages);
    }

    /**
     * Sync erratas from source to target channel.
     */
    private Set<Errata> syncErratas(User user, Channel sourceChannel, Channel targetChannel,
                                     ErrataCriteria criteria) {
        LOG.debug("Syncing erratas from {} to {}", sourceChannel.getLabel(), targetChannel.getLabel());

        Set<Errata> erratasToSync = filterErratas(user, sourceChannel, criteria);

        if (erratasToSync.isEmpty()) {
            LOG.debug("No erratas to sync");
            return Collections.emptySet();
        }

        return ErrataManager.mergeErrataToChannel(user, erratasToSync, targetChannel, sourceChannel);
    }

    /**
     * Filter erratas based on criteria.
     */
    private Set<Errata> filterErratas(User user, Channel sourceChannel, ErrataCriteria criteria) {
        if (criteria == null || !criteria.hasFilters()) {
            // No filters - return all erratas from the source channel
            return new HashSet<>(sourceChannel.getErratas());
        }

        if (criteria.advisoryNames() != null && !criteria.advisoryNames().isEmpty()) {
            // Filter by specific advisory names
            return criteria.advisoryNames().stream()
                .map(name -> ErrataFactory.lookupByAdvisoryAndOrg(name, sourceChannel.getOrg()))
                .filter(errata -> errata != null && sourceChannel.getErratas().contains(errata))
                .collect(Collectors.toSet());
        }

        if (criteria.startDate() != null || criteria.endDate() != null) {
            // Filter by date range
            List<Errata> dateFiltered = ErrataFactory.lookupByChannelBetweenDates(
                user.getOrg(),
                sourceChannel,
                criteria.startDate() != null ? criteria.startDate().toString() : null,
                criteria.endDate() != null ? criteria.endDate().toString() : null
            );
            return new HashSet<>(dateFiltered);
        }

        return new HashSet<>(sourceChannel.getErratas());
    }

    /**
     * Sync packages from source to target channel.
     * For ERRATA_AND_PACKAGES mode, only syncs packages from the given erratas.
     * For PACKAGES_ONLY mode, syncs all packages from the source channel.
     */
    private Set<Package> syncPackages(User user, Channel sourceChannel, Channel targetChannel,
                                       Set<Errata> syncedErratas,
                                       SyncOperation operation,
                                       boolean alignModules) {
        LOG.debug("Syncing packages from {} to {}", sourceChannel.getLabel(), targetChannel.getLabel());

        Set<Package> packagesToSync = determinePackagesToSync(sourceChannel, targetChannel,
            syncedErratas, operation);

        if (packagesToSync.isEmpty()) {
            LOG.debug("No packages to sync");
            return Collections.emptySet();
        }

        // Add packages to target channel
        Set<Package> existingPackages = targetChannel.getPackages();
        Set<Package> newPackages = packagesToSync.stream()
            .filter(pkg -> !existingPackages.contains(pkg))
            .collect(Collectors.toSet());

        if (!newPackages.isEmpty()) {
            targetChannel.getPackages().addAll(newPackages);
            ChannelFactory.save(targetChannel);
            ChannelManager.refreshWithNewestPackages(targetChannel, "SyncFromSourceService");

            if (alignModules && sourceChannel.isModular()) {
                LOG.debug("Aligning modular metadata");
                targetChannel.cloneModulesFrom(sourceChannel);
            }
        }

        return newPackages;
    }

    /**
     * Determine which packages to sync based on operation type.
     */
    private Set<Package> determinePackagesToSync(Channel sourceChannel, Channel targetChannel,
                                                  Set<Errata> syncedErratas,
                                                  SyncOperation operation) {
        if (operation == SyncOperation.ERRATA_AND_PACKAGES) {
            // Only sync packages that belong to the synced erratas
            return syncedErratas.stream()
                .flatMap(errata -> errata.getPackages().stream())
                .collect(Collectors.toSet());
        }
        else {
            // PACKAGES_ONLY: sync all packages from source
            return new HashSet<>(sourceChannel.getPackages());
        }
    }

    /**
     * Update errata cache for the target channel if packages were synced.
     */
    private void updateErrataCacheIfNeeded(Channel targetChannel, Set<Package> syncedPackages) {
        if (!syncedPackages.isEmpty()) {
            List<Long> packageIds = syncedPackages.stream()
                .map(Package::getId)
                .collect(Collectors.toList());

            List<Long> channelIds = new ArrayList<>();
            channelIds.add(targetChannel.getId());

            ErrataCacheManager.insertCacheForChannelPackagesAsync(channelIds, packageIds);
        }
    }

    /**
     * Lookup a channel by label and user.
     */
    private Channel lookupChannel(User user, String channelLabel) {
        Channel channel = ChannelFactory.lookupByLabelAndUser(channelLabel, user);
        if (channel == null) {
            throw new NoSuchChannelException(channelLabel);
        }
        return channel;
    }

    /**
     * Validate that the user has admin access to the channel.
     */
    private void validateChannelAccess(User user, Channel channel) {
        if (!UserManager.verifyChannelAdmin(user, channel)) {
            throw new PermissionException("User does not have admin access to channel: " +
                channel.getLabel());
        }
    }
}
