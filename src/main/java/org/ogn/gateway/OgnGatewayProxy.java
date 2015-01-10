/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.ogn.client.AircraftBeaconListener;
import org.ogn.client.OgnClient;
import org.ogn.commons.beacon.AddressType;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.forwarder.OgnBeaconForwarder;
import org.ogn.commons.igc.IgcLogger;
import org.ogn.commons.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * The <code>OgnGatewayProxy</code> service subscribes to the OGN AircraftBeacons (through ogn-client-java) and
 * registers plug-ins manager as a listener of the AircraftBeacons. I also logs all received using IgcLogger
 * 
 * @author wbuczak
 */
@Service
@Lazy
public class OgnGatewayProxy implements AircraftBeaconListener {

    @Autowired
    private PluginsManager pluginsManager;

    @Autowired
    private OgnClient client;

    @Autowired
    IgcLogger igcLogger;

    @Autowired
    Configuration conf;

    static Logger LOG = LoggerFactory.getLogger("OgnGatewayPluginsLog");

    @PostConstruct
    public void init() {
        client.subscribeToAircraftBeacons(this);
    }

    @PreDestroy
    private void cleanUp() {
        client.unsubscribeFromAircraftBeacons(this);
        client.disconnect();
    }

    @Override
    public void onUpdate(AircraftBeacon beacon, AircraftDescriptor descriptor) {

        String id = !descriptor.isKnown() ? beacon.getId() : descriptor.getRegNumber();

        // log to IGC file (non blocking operation)
        igcLogger.log(id, beacon.getLat(), beacon.getLon(), beacon.getAlt(), beacon.getRawPacket());

        // notify forwarders if certain condition is met
        if (!beacon.isStealth() && !beacon.getAddressType().equals(AddressType.RANDOM)
                && beacon.getErrorCount() < conf.getMaxPacketErrors()) {

            for (PluginHandler ph : pluginsManager.getRegisteredPlugins()) {
                OgnBeaconForwarder p = ph.getPlugin();

                if (LOG.isInfoEnabled()) {
                    if (descriptor.isKnown())
                        LOG.info("{} {} {} {}", p.getName(), p.getVersion(), JsonUtils.toJson(beacon),
                                JsonUtils.toJson(descriptor));
                    else
                        LOG.info("{} {} {}", p.getName(), p.getVersion(), JsonUtils.toJson(beacon));
                }

                if (!conf.isSimulationModeOn())
                    ph.onUpdate(beacon, descriptor);
            }// for
        }// if

    }
}