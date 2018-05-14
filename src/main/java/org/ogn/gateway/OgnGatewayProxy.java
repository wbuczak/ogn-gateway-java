/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import java.util.Optional;

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
 * The <code>OgnGatewayProxy</code> service subscribes to the OGN beacons (through ogn-client-java) and registers
 * plug-ins manager as a listener of the AircraftBeacons and ReceiverBeacons. I also logs all received using IgcLogger
 * 
 * @author wbuczak
 */
@Service
public class OgnGatewayProxy implements AircraftBeaconListener, ReceiverBeaconListener {

	@Autowired
	private PluginsManager	pluginsManager;

	@Autowired
	private OgnClient		client;

	@Autowired
	IgcLogger				igcLogger;

	@Autowired
	Configuration			conf;

	static final Logger		LOG_FORWARDED	= LoggerFactory.getLogger("OgnGatewayForwardedBeaconsLog");
	static final Logger		LOG_DISCARDED	= LoggerFactory.getLogger("OgnGatewayDiscardedBeaconsLog");

	static final Logger		LOG_AIR_RAW		= LoggerFactory.getLogger("RawAircraftBeaconsLog");
	static final Logger		LOG_AIR_DECODED	= LoggerFactory.getLogger("DecodedAircraftBeaconsLog");

	static final Logger		LOG_REC_RAW		= LoggerFactory.getLogger("RawReceiverBeaconsLog");
	static final Logger		LOG_REC_DECODED	= LoggerFactory.getLogger("DecodedReceiverBeaconsLog");

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
		pluginsManager.stop();
	}

	@Override
	public void onUpdate(final AircraftBeacon beacon, final Optional<AircraftDescriptor> descriptor) {

		LOG_AIR_RAW.info("{}", beacon.getRawPacket());

		if (LOG_AIR_DECODED.isInfoEnabled()) {
			if (descriptor.isPresent())
				LOG_AIR_DECODED.info("{} {} {}", beacon.getId(), JsonUtils.toJson(beacon),
						JsonUtils.toJson(descriptor.get()));
			else
				LOG_AIR_DECODED.info("{} {}", beacon.getId(), JsonUtils.toJson(beacon));
		}

		// only log if IGC logging enabled and the user allows tracking + identification
		if (conf.isIgcEnabled() && descriptor.isPresent() && descriptor.get().isTracked()
				&& descriptor.get().isIdentified()) {
			// log to IGC file (non blocking operation)
			igcLogger.log(beacon, descriptor);
		}

		final AddressType type = beacon.getAddressType();
		boolean discard = false;

		// notify forwarders if certain condition is met
		if (!type.equals(AddressType.RANDOM) && !type.equals(AddressType.UNRECOGNIZED)
				&& beacon.getErrorCount() <= conf.getMaxPacketErrors()) {

			// check also the descriptor - forward if either unknown or if known
			// BUT! the tracking flag is ON
			// (i.e. the person entering the record explicitly allowed tracking)
			if (!descriptor.isPresent() || descriptor.get().isTracked()) {

				for (final PluginHandler ph : pluginsManager.getRegisteredAircraftPlugins()) {
					final OgnBeaconForwarder p = ph.getPlugin();

					LOG_FORWARDED.info("{} {} {} {} {} {} {} ", beacon.isStealth(), getTrackedFlag(descriptor), type,
							beacon.getErrorCount(), p.getName(), p.getVersion(), beacon.getRawPacket());

					if (!conf.isSimulationModeOn())

						// no need to wrap it in the try-catch - the call is
						// just offering a beacon
						// to the handler's queue
						ph.onUpdate(beacon, descriptor);
				} // for

			} else {
				discard = true;
			}

		} // if
		else {
			discard = true;
		}

		if (discard)
			LOG_DISCARDED.info("{} {} {} {} {}", beacon.isStealth(), getTrackedFlag(descriptor), type,
					beacon.getErrorCount(), beacon.getRawPacket());

	}

	private static String getTrackedFlag(Optional<AircraftDescriptor> descriptor) {
		return descriptor.isPresent() ? Boolean.toString(descriptor.get().isTracked()) : "?";
	}

	@Override
	public void onUpdate(final ReceiverBeacon beacon) {
		// just log it
		LOG_REC_RAW.info("{}", beacon.getRawPacket());

		if (LOG_REC_DECODED.isInfoEnabled()) {
			LOG_REC_DECODED.info("{} {}", beacon.getId(), JsonUtils.toJson(beacon));
		}

		for (final PluginHandler ph : pluginsManager.getRegisteredReceiverPlugins()) {
			// OgnBeaconForwarder p = ph.getPlugin();
			// System.out.println("XXXX");
			if (!conf.isSimulationModeOn())

				// no need to wrap it in the try-catch - the call is
				// just offering a beacon
				// to the handler's queue
				ph.onUpdate(beacon);
		} // for
	}
}