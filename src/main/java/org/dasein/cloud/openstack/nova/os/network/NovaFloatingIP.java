/**
 * Copyright (C) 2009-2012 enStratus Networks Inc
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

package org.dasein.cloud.openstack.nova.os.network;

import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.IpAddressSupport;
import org.dasein.cloud.network.IpForwardingRule;
import org.dasein.cloud.network.Protocol;
import org.dasein.cloud.openstack.nova.os.NovaException;
import org.dasein.cloud.openstack.nova.os.NovaMethod;
import org.dasein.cloud.openstack.nova.os.NovaOpenStack;
import org.dasein.util.CalendarWrapper;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

/**
 * IP addresses services for Dasein Cloud to access OpenStack Nova floating IPs.
 * @author George Reese (george.reese@imaginary.com)
 * @since 2011.10
 * @version 2011.10
 * @version 2012.04.1 Added some intelligence around features Rackspace does not support
 */
public class NovaFloatingIP implements IpAddressSupport {
    private NovaOpenStack provider;
    
    NovaFloatingIP(NovaOpenStack cloud) {
        provider = cloud;
    }
    
    @Override
    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        Logger logger = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER:" + NovaFloatingIP.class.getName() + ".assign(" + addressId + "," + serverId + ")");
        }
        try {
            HashMap<String,Object> json = new HashMap<String,Object>();
            HashMap<String,Object> action = new HashMap<String,Object>();
            IpAddress addr = getIpAddress(addressId);
            
            if( addr == null ) {
                throw new CloudException("No such IP address: " + addressId);
            }
            //action.put("server", serverId);
            action.put("address",addr.getAddress());
            json.put("addFloatingIp", action);

            NovaMethod method = new NovaMethod(provider);

            method.postServers("/servers", serverId, new JSONObject(json), true);
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + NovaFloatingIP.class.getName() + ".assign()");
            }
        }
    }

    @Override
    public void assignToNetworkInterface(@Nonnull String addressId, @Nonnull String nicId) throws InternalException, CloudException {
        throw new OperationNotSupportedException(provider.getCloudName() + " does not support network interfaces.");
    }

    @Override
    public @Nonnull String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding not supported");
    }

    @Override
    public @Nullable IpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        Logger std = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( std.isTraceEnabled() ) {
            std.trace("ENTER: " + NovaFloatingIP.class.getName() + ".getIpAddress(" + addressId + ")");
        }
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                std.error("No context exists for this request");
                throw new InternalException("No context exists for this request");
            }
            NovaMethod method = new NovaMethod(provider);
            JSONObject ob = method.getServers("/os-floating-ips", addressId, false);

            if( ob == null ) {
                return null;
            }
            try {
                if( ob.has("floating_ip") ) {
                    JSONObject json = ob.getJSONObject("floating_ip");
                    IpAddress addr = toIP(ctx, json);

                    if( addr != null ) {
                        return addr;
                    }
                }
            }
            catch( JSONException e ) {
                std.error("getIpAddress(): Unable to identify expected values in JSON: " + e.getMessage());
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for IP address");
            }
            return null;
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("EXIT: " + NovaFloatingIP.class.getName() + ".getIpAddress()");
            }
        }
    }

    @Override
    public @Nonnull String getProviderTermForIpAddress(@Nonnull Locale locale) {
        return "floating IP";
    }

    @Override
    public boolean isAssigned(@Nonnull AddressType type) {
        return type.equals(AddressType.PUBLIC);
    }

    @Override
    public boolean isAssigned(@Nonnull IPVersion version) throws CloudException, InternalException {
        return getVersions().contains(version);
    }

    @Override
    public boolean isForwarding() {
        return false;
    }

    @Override
    public boolean isForwarding(IPVersion version) throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isRequestable(@Nonnull AddressType type) {
        return type.equals(AddressType.PUBLIC);
    }

    @Override
    public boolean isRequestable(@Nonnull IPVersion version) throws CloudException, InternalException {
        return getVersions().contains(version);
    }

    private boolean verifySupport() throws InternalException, CloudException {
        NovaMethod method = new NovaMethod(provider);

        try {
            method.getServers("/os-floating-ips", null, false);
            return true;
        }
        catch( CloudException e ) {
            if( e.getHttpCode() == 404 ) {
                return false;
            }
            throw e;
        }
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        if( provider.getMajorVersion() > 1 && provider.getComputeServices().getVirtualMachineSupport().isSubscribed() ) {
            return verifySupport();
        }
        if( provider.getMajorVersion() == 1 && provider.getMinorVersion() >= 1  &&  provider.getComputeServices().getVirtualMachineSupport().isSubscribed() ) {
            return verifySupport();
        }
        return false;
    }

    @Override
    public @Nonnull Iterable<IpAddress> listPrivateIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<IpAddress> listPublicIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        return listIpPool(IPVersion.IPV4, unassignedOnly);
    }

    @Override
    public @Nonnull Iterable<IpAddress> listIpPool(@Nonnull IPVersion version, boolean unassignedOnly) throws InternalException, CloudException {
        Logger std = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( std.isTraceEnabled() ) {
            std.trace("ENTER: " + NovaFloatingIP.class.getName() + ".listPublicIpPool()");
        }
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                std.error("No context exists for this request");
                throw new InternalException("No context exists for this request");
            }
            NovaMethod method = new NovaMethod(provider);
            JSONObject ob = method.getServers("/os-floating-ips", null, false);
            ArrayList<IpAddress> addresses = new ArrayList<IpAddress>();

            try {
                if( ob != null && ob.has("floating_ips") ) {
                    JSONArray list = ob.getJSONArray("floating_ips");

                    for( int i=0; i<list.length(); i++ ) {
                        JSONObject json = list.getJSONObject(i);

                        try {
                            IpAddress addr = toIP(ctx, json);

                            if( addr != null ) {
                                if( !unassignedOnly || addr.getServerId() == null ) {
                                    addresses.add(addr);
                                }
                            }
                        }
                        catch( JSONException e ) {
                            std.error("Invalid JSON from cloud: " + e.getMessage());
                            throw new CloudException("Invalid JSON from cloud: " + e.getMessage());
                        }
                    }
                }
            }
            catch( JSONException e ) {
                std.error("list(): Unable to identify expected values in JSON: " + e.getMessage());
                e.printStackTrace();
                throw new CloudException(CloudErrorType.COMMUNICATION, 200, "invalidJson", "Missing JSON element for floating IP in " + ob.toString());
            }
            return addresses;
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("exit - " + NovaFloatingIP.class.getName() + ".listPublicIpPool()");
            }
        }
    }

    @Override
    public @Nonnull Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding not supported");
    }

    static private volatile List<IPVersion> versions;

    private Collection<IPVersion> getVersions() {
        if( versions == null ) {
            ArrayList<IPVersion> tmp = new ArrayList<IPVersion>();

            tmp.add(IPVersion.IPV4);
            //tmp.add(IPVersion.IPV6);     TODO: when there's API support for IPv6
            versions = Collections.unmodifiableList(tmp);
        }
        return versions;
    }

    @Override
    public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return getVersions();
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    @Override
    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        Logger std = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( std.isTraceEnabled() ) {
            std.trace("ENTER: " + NovaFloatingIP.class.getName() + ".releaseFromPool(" + addressId + ")");
        }
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                std.error("No context exists for this request");
                throw new InternalException("No context exists for this request");
            }
            NovaMethod method = new NovaMethod(provider);
            long timeout = System.currentTimeMillis() + CalendarWrapper.HOUR;

            do {
                try {
                    method.deleteServers("/os-floating-ips", addressId);
                    return;
                }
                catch( NovaException e ) {
                    if( e.getHttpCode() != HttpServletResponse.SC_CONFLICT ) {
                        throw e;
                    }
                }
                try { Thread.sleep(CalendarWrapper.MINUTE); }
                catch( InterruptedException e ) { /* ignore */ }
            } while( System.currentTimeMillis() < timeout );
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("EXIT: " + NovaFloatingIP.class.getName() + ".releaseFromPool()");
            }
        }
    }

    @Override
    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        Logger logger = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER:" + NovaFloatingIP.class.getName() + ".releaseFromServer(" + addressId + ")");
        }
        try {
            HashMap<String,Object> json = new HashMap<String,Object>();
            HashMap<String,Object> action = new HashMap<String,Object>();
            IpAddress addr = getIpAddress(addressId);

            if( addr == null ) {
                throw new CloudException("No such IP address: " + addressId);
            }
            String serverId = addr.getServerId();
            
            if( serverId == null ) {
                throw new CloudException("IP address " + addressId + " is not attached to a server");
            }
            //action.put("server", serverId);
            action.put("address",addr.getAddress());
            json.put("removeFloatingIp", action);

            NovaMethod method = new NovaMethod(provider);

            method.postServers("/servers", serverId, new JSONObject(json), true);
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + NovaFloatingIP.class.getName() + ".releaseFromServer()");
            }
        }
    }

    @Override
    public @Nonnull String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
        if( typeOfAddress.equals(AddressType.PRIVATE) ) {
            throw new OperationNotSupportedException("Requesting private IP addresses is not supported by OpenStack");
        }
        return request(IPVersion.IPV4);
    }

    @Override
    public @Nonnull String request(@Nonnull IPVersion version) throws InternalException, CloudException {
        Logger logger = NovaOpenStack.getLogger(NovaFloatingIP.class, "std");

        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER: " + NovaFloatingIP.class.getName() + ".request(" + version + ")");
        }
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                logger.error("No context exists for this request");
                throw new InternalException("No context exists for this request");
            }
            HashMap<String,Object> wrapper = new HashMap<String,Object>();
            NovaMethod method = new NovaMethod(provider);

            JSONObject result = method.postServers("/os-floating-ips", null, new JSONObject(wrapper), false);

            if( result != null && result.has("floating_ip") ) {
                try {
                    JSONObject ob = result.getJSONObject("floating_ip");
                    IpAddress addr = toIP(ctx, ob);

                    if( addr != null ) {
                        return addr.getProviderIpAddressId();
                    }
                }
                catch( JSONException e ) {
                    logger.error("create(): Unable to understand create response: " + e.getMessage());
                    if( logger.isTraceEnabled() ) {
                        e.printStackTrace();
                    }
                    throw new CloudException(e);
                }
            }
            logger.error("create(): No IP address was created by the create attempt, and no error was returned");
            throw new CloudException("No IP address was created");

        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + NovaFloatingIP.class.getName() + ".request()");
            }
        }
    }

    @Override
    public @Nonnull String requestForVLAN(IPVersion version) throws InternalException, CloudException {
        throw new OperationNotSupportedException(provider.getCloudName() + " does not support static IP addresses for VLANs");
    }

    @Override
    public void stopForward(@Nonnull String ruleId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Forwarding not supported");
    }

    @Override
    public boolean supportsVLANAddresses(@Nonnull IPVersion ofVersion) throws InternalException, CloudException {
        return false;
    }

    private IpAddress toIP(ProviderContext ctx, JSONObject json) throws JSONException {
        if(json == null ) {
            return null;
        }
        String regionId = ctx.getRegionId();

        IpAddress address = new IpAddress();

        if( regionId != null ) {
            address.setRegionId(regionId);
        }
        address.setServerId(null);
        address.setProviderLoadBalancerId(null);
        address.setAddressType(AddressType.PUBLIC);
        
        String id = (json.has("id") ? json.getString("id") : null);
        String ip = (json.has("ip") ? json.getString("ip") : null);
        String server = (json.has("instance_id") ? json.getString("instance_id") : null);
        if( id != null ) {
            address.setIpAddressId(id);
        }
        if( id != null  ) {
            address.setServerId(server);
        }
        if( ip != null ) {
            address.setAddress(ip);
        }
        if( id == null || ip == null ) {
            return null;
        }
        address.setVersion(IPVersion.IPV4);
        return address;
    }
}