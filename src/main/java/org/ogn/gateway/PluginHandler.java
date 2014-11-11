/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import org.ogn.client.OgnBeaconListener;
import org.ogn.commons.beacon.OgnBeacon;
import org.ogn.commons.beacon.forwarder.OgnBeaconForwarder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PluginHandler implements OgnBeaconListener<OgnBeacon> {

    static Logger LOG = LoggerFactory.getLogger(PluginHandler.class);

    private OgnBeaconForwarder plugin;    

    private BlockingQueue<OgnBeacon> beacons = new LinkedBlockingQueue<>();

    private static ExecutorService executor;
    private Future<?> queueConsumerFeature;

    public PluginHandler(OgnBeaconForwarder plugin) {
        this.plugin = plugin;
        // share one executor across all handlers
        if (executor == null) {
            executor = Executors.newCachedThreadPool();
        }
    }

    @Override
    public void onUpdate(OgnBeacon beacon) {
        beacons.offer(beacon);
    }

    public void start() {
        if (queueConsumerFeature == null) {
            queueConsumerFeature = executor.submit(new Runnable() {
                @Override
                public void run() {
                    LOG.debug("starting plugin handler poller for plugin {}:{}", plugin.getName(), plugin.getVersion());
                    while (!Thread.interrupted()) {
                        OgnBeacon beacon;
                        try {
                            // get next beacon and proceed with sending it
                            beacon = beacons.take();
                            plugin.onBeacon(beacon);
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
        if (queueConsumerFeature != null)
            queueConsumerFeature.cancel(true);
    }
}