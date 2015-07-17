/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.ogn.commons.beacon.forwarder.OgnAircraftBeaconForwarder;
import org.ogn.commons.beacon.forwarder.OgnReceiverBeaconForwarder;
import org.ogn.commons.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Service;

/**
 * this service takes care of periodic scanning and registration of gateway
 * plug-ins
 * 
 * @author wbuczak
 */
@Service
@ManagedResource(objectName = "org.ogn.gateway:name=PluginsManager", description = "OGN gateway's plugins manager")
public class PluginsManager {

	static Logger LOG = LoggerFactory.getLogger(PluginsManager.class);

	Configuration conf;

	private ConcurrentMap<String, PluginHandler> aircraftPlugins = new ConcurrentHashMap<>();
	private ConcurrentMap<String, PluginHandler> receiverPlugins = new ConcurrentHashMap<>();

	private ScheduledExecutorService scheduledExecutor;

	private volatile Future<?> pluginsRegistrationFuture;

	@Autowired
	public void setConfig(final Configuration conf) {
		this.conf = conf;
	}

	public PluginsManager() {
		scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	}

	@PostConstruct
	public void init() {
		startPluginsRegistrationJob();
	}

	@PreDestroy
	public void preDestroy() {
		for (PluginHandler ph : getRegisteredAircraftPlugins()) {
			ph.stop();
		}

		for (PluginHandler ph : getRegisteredReceiverPlugins()) {
			ph.stop();
		}
	}

	public void stop() {
		if (pluginsRegistrationFuture != null) {
			pluginsRegistrationFuture.cancel(false);
		}
	}

	private void startPluginsRegistrationJob() {
		if (pluginsRegistrationFuture == null || pluginsRegistrationFuture.isCancelled()) {
			pluginsRegistrationFuture = scheduledExecutor.scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
					registerPlugins();
				}
			}, 0, conf.getScanningInterval(), TimeUnit.MILLISECONDS);
		}
	}

	public synchronized void registerPlugins() {
		File loc = new File(conf.getPluginsFolderName());

		File[] flist = loc.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.getPath().toLowerCase().endsWith(".jar");
			}
		});

		URL[] urls = new URL[flist.length];

		for (int i = 0; i < flist.length; i++)
			try {
				urls[i] = flist[i].toURI().toURL();
			} catch (MalformedURLException e) {
				LOG.error("failed to register plug-in", e);
			}

		URLClassLoader ucl = new URLClassLoader(urls);

		ServiceLoader<OgnAircraftBeaconForwarder> sl = ServiceLoader.load(OgnAircraftBeaconForwarder.class, ucl);

		Iterator<OgnAircraftBeaconForwarder> it = sl.iterator();
		while (it.hasNext()) {
			OgnAircraftBeaconForwarder bf = it.next();

			LOG.trace("loading plug-in: {}", bf.getClass().getName());

			String key = pluginKey(OgnAircraftBeaconForwarder.class, bf.getName(), bf.getVersion());

			String md5key = StringUtils.md5(key);

			// if not yet registered
			if (!aircraftPlugins.containsKey(md5key)) {
				LOG.info("registering OgnAircraftBeaconForwarder plug-in: {} [ name: {}, version: {}, descr.: {} ]", bf
						.getClass().getName(), bf.getName(), bf.getVersion(), bf.getDescription());

				PluginHandler ph = new AircraftPluginHandler(bf);
				ph.start();
				aircraftPlugins.putIfAbsent(md5key, ph);
			}// if
		}// while

		ServiceLoader<OgnReceiverBeaconForwarder> sl2 = ServiceLoader.load(OgnReceiverBeaconForwarder.class, ucl);
		Iterator<OgnReceiverBeaconForwarder> it2 = sl2.iterator();
		while (it2.hasNext()) {
			OgnReceiverBeaconForwarder bf = it2.next();

			LOG.trace("loading plug-in: {}", bf.getClass().getName());

			String key = pluginKey(OgnReceiverBeaconForwarder.class, bf.getName(), bf.getVersion());

			String md5key = StringUtils.md5(key);

			// if not yet registered
			if (!receiverPlugins.containsKey(md5key)) {
				LOG.info("registering OgnReceiverBeaconForwarder plug-in: {} {} {} {}", bf.getClass().getName(),
						bf.getName(), bf.getVersion(), bf.getDescription());

				PluginHandler ph = new ReceiverPluginHandler(bf);
				ph.start();
				receiverPlugins.putIfAbsent(md5key, ph);
			}// if
		}// while

	}

	@ManagedAttribute
	public int getRegisteredAircraftPluginsCount() {
		return aircraftPlugins.size();
	}

	@ManagedAttribute
	public int getRegisteredReceiverPluginsCount() {
		return receiverPlugins.size();
	}

	static String pluginKey(final Class<?> pluginType, final String pluginName, final String pluginVersion) {
		return String.format("%s:%s:%s", pluginType.getName(), pluginName, pluginVersion);
	}

	public Collection<PluginHandler> getRegisteredAircraftPlugins() {
		return Collections.unmodifiableCollection(aircraftPlugins.values());
	}

	public Collection<PluginHandler> getRegisteredReceiverPlugins() {
		return Collections.unmodifiableCollection(receiverPlugins.values());
	}

}