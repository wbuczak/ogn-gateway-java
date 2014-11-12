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

import org.ogn.commons.beacon.forwarder.OgnBeaconForwarder;
import org.ogn.commons.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * this service takes care of periodic scanning and registration of gateway plugins
 * 
 * @author wbuczak
 */
@Service
public class PluginsManager {

    static Logger LOG = LoggerFactory.getLogger(PluginsManager.class);

    Configuration conf;

    private ConcurrentMap<String, PluginHandler> plugins = new ConcurrentHashMap<>();

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
                LOG.error("failed to register plugin", e);
            }

        URLClassLoader ucl = new URLClassLoader(urls);

        ServiceLoader<OgnBeaconForwarder> sl = ServiceLoader.load(OgnBeaconForwarder.class, ucl);

        Iterator<OgnBeaconForwarder> apit = sl.iterator();

        while (apit.hasNext()) {
            OgnBeaconForwarder bf = apit.next();
            String key = pluginKey(bf.getName(), bf.getVersion());

            // TODO: Should we register the newest (higher) version of a plugin if two same
            // plugins are available (with same name)
            String md5key = StringUtils.md5(key);

            // if not yet registered
            if (!plugins.containsKey(md5key)) {
                PluginHandler ph = new PluginHandler(bf);
                ph.start();
                plugins.putIfAbsent(StringUtils.md5(key), ph);
            }// if

        }// while
    }

    public int getRegisteredPluginsCount() {
        return plugins.size();
    }

    static String pluginKey(final String pluginName, final String pluginVersion) {
        return String.format("%s:%s", pluginName, pluginVersion);
    }

    public Collection<PluginHandler> getRegisteredPlugins() {
        return Collections.unmodifiableCollection(plugins.values());
    }
}