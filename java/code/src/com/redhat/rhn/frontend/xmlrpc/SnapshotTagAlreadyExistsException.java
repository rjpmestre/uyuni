/*
 * Copyright (c) 2011 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.frontend.xmlrpc;

import com.redhat.rhn.FaultException;
import com.redhat.rhn.common.localization.LocalizationService;


/**
 * SnapshotTagAlreadyExistsException
 */
public class SnapshotTagAlreadyExistsException extends FaultException {
    /**
     * Comment for <code>serialVersionUID</code>
     */
    private static final long serialVersionUID = -4258121382559963776L;

    /**
     * Contructor
     * @param tagName name of the snaphot tag
     */
    public SnapshotTagAlreadyExistsException(String tagName) {
        super(1211, "SnapshotTagAlreadyExists", LocalizationService.getInstance().
                getMessage("api.provisioning.snapshot.tagalreadyinuse"),
                new Object[] {tagName});
    }
}
