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
package com.suse.persistence.dao;

import static com.redhat.rhn.common.ExceptionMessage.NOT_INSTANTIABLE;

import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.domain.channel.Channel;
import com.redhat.rhn.domain.errata.Errata;
import com.redhat.rhn.domain.org.Org;
import com.redhat.rhn.domain.rhnpackage.Package;
import com.redhat.rhn.domain.user.User;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Repository for package-related database queries.
 * Provides data access operations without business logic.
 */
public class PackageRepository {

    /**
     * Find all packages from multiple erratas that exist in a specific channel.
     * Returns distinct packages across all provided erratas.
     *
     * @param channel the channel to search in
     * @param erratas the erratas to get packages from
     * @return list of packages from the erratas that are in the channel
     */
    public static List<Package> findByErratasInChannel(Channel channel, Set<Errata> erratas) {
        if (erratas == null || erratas.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> errataIds = erratas.stream()
                .map(Errata::getId)
                .collect(Collectors.toList());

        String hql = """
            SELECT DISTINCT p
            FROM Package p
            INNER JOIN p.channels c
            INNER JOIN p.errata e
            WHERE c.id = :channelId
              AND e.id IN (:errataIds)
            """;

        return HibernateFactory.getSession()
                .createQuery(hql, Package.class)
                .setParameter("channelId", channel.getId())
                .setParameterList("errataIds", errataIds)
                .list();
    }

    /**
     * Find packages from errata in a channel.
     *
     * @param channel the channel to search in
     * @param errata the errata to get packages from
     * @param org the Org
     * @return list of packages from the errata that user has access to
     */
    public static List<Package> findByErrataInChannel(Channel channel, Errata errata, Org org) {
        String hql = """
            SELECT DISTINCT p
            FROM Package p
            INNER JOIN p.channels c
            INNER JOIN p.errata e
            WHERE c.id = :channelId
              AND e.id = :errataId
              AND (p.org IS NULL OR p.org.id = :orgId OR c.org.id = :orgId)
            """;

        return HibernateFactory.getSession()
                .createQuery(hql, Package.class)
                .setParameter("channelId", channel.getId())
                .setParameter("errataId", errata.getId())
                .setParameter("orgId", org.getId())
                .list();
    }

    /**
     * Find packages from an errata that also exist in a channel,
     * matching by name+arch.
     *
     * @param channel the target channel to check for name+arch matches
     * @param errata the errata to get packages from
     * @param user the user
     * @return list of packages from errata that have name+arch matches in channel
     */
    public static List<Package> findByErrataWithNameArchMatchInChannelForUser(
            Channel channel, Errata errata, User user
    ) {
        String hql = """
            SELECT DISTINCT p
            FROM Package p
            INNER JOIN p.errata e
            INNER JOIN Package p2
                ON p2.packageName.id = p.packageName.id
                AND p2.packageArch.id = p.packageArch.id
            INNER JOIN p2.channels c
            WHERE e.id = :errataId
              AND c.id = :channelId
              AND c.org.id = :orgId
              AND (e.org IS NULL OR e.org.id = :orgId)
              AND (p.org IS NULL OR p.org.id = :orgId)
            """;

        return HibernateFactory.getSession()
                .createQuery(hql, Package.class)
                .setParameter("errataId", errata.getId())
                .setParameter("channelId", channel.getId())
                .setParameter("orgId", user.getOrg().getId())
                .list();
    }

    private PackageRepository() {
        throw new UnsupportedOperationException(NOT_INSTANTIABLE);
    }
}
