/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.ogn.client.AircraftBeaconListener;
import org.ogn.client.OgnClient;
import org.ogn.client.ReceiverBeaconListener;
import org.ogn.commons.beacon.AddressType;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.ReceiverBeacon;
import org.ogn.commons.beacon.forwarder.OgnBeaconForwarder;
import org.ogn.commons.igc.IgcLogger;
import org.ogn.commons.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The <code>OgnGatewayProxy</code> service subscribes to the OGN AircraftBeacons (through ogn-client-java) and
 * registers plug-ins manager as a listener of the AircraftBeacons. I also logs all received using IgcLogger
 * 
 * @author wbuczak
 */
@Service
public class OgnGatewayProxy implements AircraftBeaconListener, ReceiverBeaconListener {

    @Autowired
    private PluginsManager pluginsManager;

    @Autowired
    private OgnClient client;

    @Autowired
    IgcLogger igcLogger;

    @Autowired
    Configuration conf;

    static Logger LOG = LoggerFactory.getLogger(OgnGatewayProxy.class);
    static Logger LOG_DISCARDED = LoggerFactory.getLogger("OgnGatewayProxyDiscardedLog");

    static Logger LOG_AIR_RAW = LoggerFactory.getLogger("RawAircraftBeaconsLog");
    static Logger LOG_AIR_DECODED = LoggerFactory.getLogger("DecodedAircraftBeaconsLog");

    static Logger LOG_REC_RAW = LoggerFactory.getLogger("RawReceiverBeaconsLog");
    static Logger LOG_REC_DECODED = LoggerFactory.getLogger("DecodedReceiverBeaconsLog");

    @PostConstruct
    public void init() {
        client.subscribeToAircraftBeacons(this);
        client.subscribeToReceiverBeacons(this);
    }

    @PreDestroy
    private void cleanUp() {
        client.unsubscribeFromAircraftBeacons(this);
        client.unsubscribeFromReceiverBeacons(this);
        client.disconnect();
    }

    @Override
    public void onUpdate(AircraftBeacon beacon, AircraftDescriptor descriptor) {

        LOG_AIR_RAW.info("{}", beacon.getRawPacket());

        if (LOG_AIR_DECODED.isInfoEnabled()) {
            if (descriptor.isKnown())
                LOG_AIR_DECODED
                        .info("{} {} {}", beacon.getId(), JsonUtils.toJson(beacon), JsonUtils.toJson(descriptor));
            else
                LOG_AIR_DECODED.info("{} {}", beacon.getId(), JsonUtils.toJson(beacon));
        }

        String id = !descriptor.isKnown() ? beacon.getId() : descriptor.getRegNumber();

        // log to IGC file (non blocking operation)
        igcLogger.log(id, beacon.getLat(), beacon.getLon(), beacon.getAlt(), beacon.getRawPacket());

        // notify forwarders if certain condition is met
        AddressType type = beacon.getAddressType();
        if (!beacon.isStealth() && !type.equals(AddressType.RANDOM) && !type.equals(AddressType.UNRECOGNIZED)
                && descriptor.isTracked() 
                && beacon.getErrorCount() <= conf.getMaxPacketErrors()) {

            for (PluginHandler ph : pluginsManager.getRegisteredPlugins()) {
                OgnBeaconForwarder p = ph.getPlugin();

                LOG.info("{} {} {}", p.getName(), p.getVersion(), beacon.getRawPacket());

                if (!conf.isSimulationModeOn())
                    ph.onUpdate(beacon, descriptor);
            }// for
        }// if
        else {
            LOG_DISCARDED.info("{} {} {} {}", beacon.isStealth(), type, beacon.getErrorCount(), beacon.getRawPacket());
        }

    }

    @Override
    public void onUpdate(ReceiverBeacon beacon) {
        // just log it
        LOG_REC_RAW.info("{}", beacon.getRawPacket());
        LOG_REC_DECODED.info("{} {}", beacon.getId(), JsonUtils.toJson(beacon));
    }
}