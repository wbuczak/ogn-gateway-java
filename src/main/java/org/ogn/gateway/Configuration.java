/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;
import org.springframework.stereotype.Component;

@Component
@ManagedResource(objectName = "org.ogn.gateway:name=Config", description = "OGN gateway's configuration holder")
public class Configuration {

    @Value("${ogn.gateway.plugins.folder:plugins}")
    private String pluginsFolder;

    @Value("${ogn.gateway.igc.folder:log/igc}")
    private String igcFolder;

    @Value("${ogn.gateway.plugins.scanning_interval:30000}")
    private int scanningIntervalMs;

    @Value("${ogn.gateway.max_packet_errors:5}")
    private int maxPacketErrors;

    @Value("${ogn.gateway.simulation:false}")
    private boolean simulationMode;

    @ManagedAttribute
    public String getPluginsFolderName() {
        return pluginsFolder;
    }

    @ManagedAttribute
    public int getScanningInterval() {
        return scanningIntervalMs;
    }

    @ManagedAttribute
    public int getMaxPacketErrors() {
        return maxPacketErrors;
    }

    @ManagedAttribute
    public boolean isSimulationModeOn() {
        return simulationMode;
    }

    @ManagedAttribute
    public String getIgcFolder() {
        return igcFolder;
    }

}