/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import javax.annotation.Resource;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration("classpath:application-context.xml")
@ActiveProfiles("TEST")
public class ConfigurationTest {

	// try to set simulation mode as sys. env. variable
	static {
		System.setProperty("ogn.gateway.simulation", "true");
	}

	@Resource
	Configuration config;

	@Test
	public void testBasicInterface() {
		assertNotNull(config);
		assertEquals("plugins", config.getPluginsFolderName());
		assertEquals(30000, config.getScanningInterval());
		assertEquals("log/testigc", config.getIgcFolder());
		assertEquals(6, config.getMaxPacketErrors());
		assertEquals(true, config.isSimulationModeOn());
		assertFalse(config.isClusteringEnabled());

	}
}