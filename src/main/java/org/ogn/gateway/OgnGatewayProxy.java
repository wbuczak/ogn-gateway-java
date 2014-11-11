/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.ogn.client.OgnBeaconListener;
import org.ogn.client.OgnClient;
import org.ogn.commons.beacon.AddressType;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.igc.IgcLogger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The <code>OgnGatewayProxy</code> service subscribes to the OGN AircraftBeacons (through ogn-client) and registers
 * plugins manager as a listener of the AircraftBeacons
 * 
 * @author wbuczak
 */
@Service
public class OgnGatewayProxy implements OgnBeaconListener<AircraftBeacon> {

    @Autowired
    private PluginsManager pluginsManager;

    @Autowired
    private OgnClient client;

    @Autowired
    IgcLogger igcWriter;
    
    @Autowired
    Configuration conf;

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
    public void onUpdate(AircraftBeacon beacon) {
        // Log position in IGC file
        // IGCWriter.logPosition(getRegNum(immat),currentFix.latitude,currentFix.longitude,currentFix.altitude,line);
        // IGCWriter.logPosition(getRegNum(immat+"."+currentFix.Id+"."+currentFix.regnum+"."+currentFix.CN),currentFix.latitude,currentFix.longitude,currentFix.altitude,line);

        igcWriter.log(beacon);

        // notify forwarders only when certain condition is met
        if (beacon.getId() != null && !beacon.isStealth() && !beacon.getAddressType().equals(AddressType.RANDOM)
                && beacon.getErrorCount() < conf.getMaxPacketErrors()) {

            for (PluginHandler ph : pluginsManager.getRegisteredPlugins()) {
                ph.onUpdate(beacon);
            }// for

        }

    }// if
}
