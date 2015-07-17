package org.ogn.gateway;

import org.ogn.commons.beacon.ReceiverBeacon;
import org.ogn.commons.beacon.forwarder.OgnReceiverBeaconForwarder;

public class ReceiverPluginHandler extends PluginHandler {

	public ReceiverPluginHandler(OgnReceiverBeaconForwarder plugin) {
		super(plugin);
	}

	@Override
	protected void processValue(Object value) {
		try {
			ReceiverBeacon rBeacon = (ReceiverBeacon) value;
			((OgnReceiverBeaconForwarder) plugin).onBeacon(rBeacon);
		} catch (Exception ex) {
			LOG.error("exception caught", ex);
		}

	}

}