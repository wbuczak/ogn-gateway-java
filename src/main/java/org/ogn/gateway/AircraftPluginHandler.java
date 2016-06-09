package org.ogn.gateway;

import org.ogn.commons.beacon.AircraftBeaconWithDescriptor;
import org.ogn.commons.beacon.forwarder.OgnAircraftBeaconForwarder;

public class AircraftPluginHandler extends PluginHandler {

	protected AircraftPluginHandler(OgnAircraftBeaconForwarder plugin) {
		super(plugin);
	}

	@Override
	protected void processValue(Object value) {

		try {
			AircraftBeaconWithDescriptor aBeacon = (AircraftBeaconWithDescriptor) value;
			((OgnAircraftBeaconForwarder) plugin).onBeacon(aBeacon.getBeacon(), aBeacon.getDescriptor());
		} catch (Exception ex) {
			LOG.error("exception caught", ex);
		}

	}
}
