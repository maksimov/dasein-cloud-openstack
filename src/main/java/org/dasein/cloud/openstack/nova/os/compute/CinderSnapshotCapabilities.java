/**
 * Copyright (C) 2009-2014 Dell, Inc.
 * See annotations for authorship information
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.openstack.nova.os.compute;

import org.dasein.cloud.*;
import org.dasein.cloud.compute.SnapshotCapabilities;
import org.dasein.cloud.openstack.nova.os.NovaOpenStack;
import org.dasein.cloud.util.NamingConstraints;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Locale;

/**
 * Describes the capabilities of Openstack with respect to Dasein snapshot operations.
 * User: daniellemayne
 * Date: 06/03/2014
 * Time: 09:49
 */
public class CinderSnapshotCapabilities extends AbstractCapabilities<NovaOpenStack> implements SnapshotCapabilities{
    public CinderSnapshotCapabilities(@Nonnull NovaOpenStack provider) {
        super(provider);
    }

    @Nonnull
    @Override
    public String getProviderTermForSnapshot(@Nonnull Locale locale) {
        return "snapshot";
    }

    @Nullable
    @Override
    public VisibleScope getSnapshotVisibleScope() {
        return VisibleScope.ACCOUNT_REGION;
    }

    @Nonnull
    @Override
    public Requirement identifyAttachmentRequirement() throws InternalException, CloudException {
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean supportsSnapshotCopying() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsSnapshotCreation() throws CloudException, InternalException {
        return true;
    }

    @Override
    public boolean supportsSnapshotSharing() throws InternalException, CloudException {
        return false;
    }

    @Override
    public boolean supportsSnapshotSharingWithPublic() throws InternalException, CloudException {
        return false;
    }

    @Override
    public NamingConstraints getSnapshotNamingConstraints(){
        return NamingConstraints.getAlphaNumeric(0, 255);
    }
}
