/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
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
 * this service takes care of periodic scanning and registration of gateway plug-ins
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

	private Set<URL> pluginJars = new HashSet<>();

	public PluginsManager() {
		scheduledExecutor = Executors.newSingleThreadScheduledExecutor();
	}

	@Autowired
	public PluginsManager(final Configuration conf) {
		this();
		this.conf = conf;
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
		if (pluginsRegistrationFuture != null)
			pluginsRegistrationFuture.cancel(true);

		scheduledExecutor.shutdownNow();
	}

	private void startPluginsRegistrationJob() {
		if (pluginsRegistrationFuture == null || pluginsRegistrationFuture.isCancelled()) {
			pluginsRegistrationFuture = scheduledExecutor.scheduleAtFixedRate(new Runnable() {

				@Override
				public void run() {
					try {
						registerPlugins();
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
			}, 0, conf.getScanningInterval(), TimeUnit.MILLISECONDS);
		}
	}

	synchronized void registerPlugins() {
		File loc = new File(conf.getPluginsFolderName());

		File[] flist = loc.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				return file.getPath().toLowerCase().endsWith(".jar");
			}
		});

		List<URL> urls = new ArrayList<>();
		for (int i = 0; i < flist.length; i++)
			try {
				URL jar = flist[i].toURI().toURL();
				if (!pluginJars.contains(jar)) {
					urls.add(jar);
					pluginJars.add(jar);

					// add the jar to the system classpath! (if not yet added)
					addURL(jar);
				}
			} catch (Exception e) {
				LOG.error("failed to register plug-in", e);
			}

		// no point in continuing unless there are new plugins
		if (urls.isEmpty())
			return;

		URLClassLoader ucl = new URLClassLoader(urls.toArray(new URL[0]));
		ServiceLoader<OgnAircraftBeaconForwarder> aLoader = ServiceLoader.load(OgnAircraftBeaconForwarder.class, ucl);
		ServiceLoader<OgnReceiverBeaconForwarder> rLoader = ServiceLoader.load(OgnReceiverBeaconForwarder.class, ucl);
		Iterator<OgnAircraftBeaconForwarder> aIt = aLoader.iterator();
		Iterator<OgnReceiverBeaconForwarder> rIt = rLoader.iterator();

		while (aIt.hasNext()) {
			OgnAircraftBeaconForwarder bf = aIt.next();

			LOG.info("loading plug-in: {}", bf.getClass().getName());
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

		while (rIt.hasNext()) {
			OgnReceiverBeaconForwarder bf = rIt.next();

			LOG.trace("loading plug-in: {}", bf.getClass().getName());
			String key = pluginKey(OgnReceiverBeaconForwarder.class, bf.getName(), bf.getVersion());
			String md5key = StringUtils.md5(key);

			// if not yet registered
			if (!receiverPlugins.containsKey(md5key)) {
				LOG.info("registering OgnReceiverBeaconForwarder plug-in: {} [ name: {}, version: {}, descr.: {} ]", bf
						.getClass().getName(), bf.getName(), bf.getVersion(), bf.getDescription());

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

	public static void addURL(URL u) throws IOException {
		final Class<?>[] parameters = new Class[] { URL.class };
		URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
		Class<URLClassLoader> sysclass = URLClassLoader.class;

		try {
			Method method = sysclass.getDeclaredMethod("addURL", parameters);
			method.setAccessible(true);
			method.invoke(sysloader, new Object[] { u });
		} catch (Throwable t) {
			t.printStackTrace();
			throw new IOException("Error, could not add URL to system classloader");
		}

	}

}