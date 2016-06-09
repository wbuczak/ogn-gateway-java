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
import org.ogn.client.ReceiverBeaconListener;
import org.ogn.commons.beacon.AircraftBeacon;
import org.ogn.commons.beacon.AircraftBeaconWithDescriptor;
import org.ogn.commons.beacon.AircraftDescriptor;
import org.ogn.commons.beacon.ReceiverBeacon;
import org.ogn.commons.beacon.forwarder.OgnBeaconForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class PluginHandler implements AircraftBeaconListener, ReceiverBeaconListener {

	protected static Logger LOG = LoggerFactory.getLogger(PluginHandler.class);

	private BlockingQueue<Object> beacons = new LinkedBlockingQueue<>();

	private static ExecutorService executor;
	private Future<?> queueConsumerFeature;

	protected OgnBeaconForwarder plugin;

	public OgnBeaconForwarder getPlugin() {
		return plugin;
	}

	protected <Plugin extends OgnBeaconForwarder> PluginHandler(Plugin plugin) {
		this.plugin = plugin;

		// share one executor across all handlers
		if (executor == null) {
			executor = Executors.newCachedThreadPool();
		}
	}

	public void start() {
		if (queueConsumerFeature == null) {
			queueConsumerFeature = executor.submit(new Runnable() {
				@Override
				public void run() {

					try {
						plugin.init();
					} catch (Exception ex) {
						LOG.warn("exception caught when trying to initilize plugin: {}:{}:{}", plugin.getName(),
								plugin.getVersion(), plugin.getDescription(), ex);
					}

					while (!Thread.interrupted()) {
						Object obj = null;
						try {
							obj = beacons.take();
						} catch (InterruptedException e) {
							LOG.warn("pugin-handler for pugin: {} - interrupted exception caught", plugin.getName());
							Thread.currentThread().interrupt();
							continue;
						}

						try {
							processValue(obj);
						} catch (Exception e) {
							LOG.error("exception caught", e);
							continue;
						}

					} // while

				}
			});
		}
	}

	protected abstract void processValue(Object obj);

	public void stop() {
		if (plugin != null)
			plugin.stop();

		if (queueConsumerFeature != null)
			queueConsumerFeature.cancel(true);
	}

	@Override
	public void onUpdate(final AircraftBeacon beacon, final AircraftDescriptor descriptor) {
		beacons.offer(new AircraftBeaconWithDescriptor(beacon, descriptor));
	}

	@Override
	public void onUpdate(ReceiverBeacon beacon) {
		beacons.offer(beacon);
	}
}