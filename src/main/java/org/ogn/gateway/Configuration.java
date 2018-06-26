/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jmx.export.annotation.ManagedAttribute;
import org.springframework.jmx.export.annotation.ManagedResource;

@ManagedResource(objectName = "org.ogn.gateway:name=Config", description = "OGN gateway's configuration holder")
public class Configuration {

	@Value("${ogn.gateway.plugins.folder:#{systemEnvironment['OGN_GATEWAY_PLUGINS_FOLDER'] ?: 'plugins'}}")
	private String	pluginsFolder;

	@Value("${ogn.gateway.igc.folder:#{systemEnvironment['OGN_GATEWAY_IGC_FOLDER'] ?: 'log/igc'}}")
	private String	igcFolder;

	@Value("${ogn.gateway.igc.enabled:#{systemEnvironment['OGN_GATEWAY_IGC_ENABLED'] ?: true}}")
	private boolean	igcEnabled;

	@Value("${ogn.gateway.plugins.scanning_interval:30000}")
	private int		scanningIntervalMs;

	@Value("${ogn.gateway.max_packet_errors:5}")
	private int		maxPacketErrors;

	@Value("${ogn.gateway.simulation:#{systemEnvironment['OGN_GATEWAY_SIMULATION'] ?: false}}")
	private boolean	simulationMode;

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
	public boolean isIgcEnabled() {
		return igcEnabled;
	}

	@ManagedAttribute
	public String getIgcFolder() {
		return igcFolder;
	}

}