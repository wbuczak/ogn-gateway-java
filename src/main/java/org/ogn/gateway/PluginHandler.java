/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.ogn.client.AircraftBeaconListener;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.forwarder.OgnAircraftBeaconForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginHandler implements AircraftBeaconListener {

	static Logger LOG = LoggerFactory.getLogger(PluginHandler.class);

	static class AircraftBeaconWithDescriptor {
		AircraftBeacon beacon;
		AircraftDescriptor descriptor;
		String rawBeacon;

		AircraftBeaconWithDescriptor(AircraftBeacon beacon, AircraftDescriptor descriptor, String rawBeacon) {
			this.beacon = beacon;
			this.descriptor = descriptor;
			this.rawBeacon = rawBeacon;
		}
	}

	private OgnAircraftBeaconForwarder plugin;

	private BlockingQueue<AircraftBeaconWithDescriptor> beacons = new LinkedBlockingQueue<>();

	private static ExecutorService executor;
	private Future<?> queueConsumerFeature;

	public PluginHandler(OgnAircraftBeaconForwarder plugin) {
		this.plugin = plugin;
		// share one executor across all handlers
		if (executor == null) {
			executor = Executors.newCachedThreadPool();
		}
	}

	public OgnAircraftBeaconForwarder getPlugin() {
		return this.plugin;
	}

	@Override
	public void onUpdate(final AircraftBeacon beacon, final AircraftDescriptor descriptor, final String rawBeacon) {
		beacons.offer(new AircraftBeaconWithDescriptor(beacon, descriptor, rawBeacon));
	}

	public void start() {
		if (queueConsumerFeature == null) {
			queueConsumerFeature = executor.submit(new Runnable() {
				@Override
				public void run() {
					LOG.debug("starting plugin handler poller for plugin {}:{}", plugin.getName(), plugin.getVersion());
					while (!Thread.interrupted()) {
						AircraftBeaconWithDescriptor beacon;
						try {
							// get next beacon and proceed with sending it
							beacon = beacons.take();
							plugin.onBeacon(beacon.beacon, beacon.descriptor, beacon.rawBeacon);
						} catch (InterruptedException e) {
							LOG.warn("pugin-handler for pugin: {} - interrupted exception caught", plugin.getName());
							Thread.currentThread().interrupt();
							continue;
						}
					}// while

				}
			});
		}
	}

	public void stop() {
		if (plugin != null)
			plugin.stop();

		if (queueConsumerFeature != null)
			queueConsumerFeature.cancel(true);
	}
}