/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.easymock.EasyMock;
import org.junit.Before;
import org.junit.Test;

public class PluginsManagerTest2 {

	Configuration conf;

	@Before
	public void before() throws Exception {
		Path dir = Paths.get("src/test/resources/plugins2");
		Path file = Paths.get("src/test/resources/plugins2/ogn-gateway-duplicates-1.0.0.jar");

		if (Files.exists(dir)) {

			if (Files.exists(file))
				Files.delete(file);

			Files.delete(dir);
		}

		// create an empty folder
		Files.createDirectory(dir);
	}

	@Test(timeout = 5000)
	public void test2() throws Exception {

		PluginsManager pluginsManager = new PluginsManager();

		conf = EasyMock.createMock(Configuration.class);
		expect(conf.getPluginsFolderName()).andReturn("src/test/resources/plugins2");
		expectLastCall().atLeastOnce();

		expect(conf.getScanningInterval()).andReturn(300);
		expectLastCall().atLeastOnce();

		replay(conf);

		pluginsManager.setConfig(conf);

		pluginsManager.init();
		Thread.sleep(1000);

		// no plugins expected at first
		assertEquals(0, pluginsManager.getRegisteredAircraftPluginsCount());

		Path source = Paths.get("src/test/resources/plugins/ogn-gateway-duplicates-1.0.0.jar");
		Path destination = Paths.get("src/test/resources/plugins2/ogn-gateway-duplicates-1.0.0.jar");

		Files.copy(source, destination);

		while (pluginsManager.getRegisteredAircraftPluginsCount() < 1) {
			Thread.sleep(50);
		}

		// there should now only be one plugin registered
		assertEquals(1, pluginsManager.getRegisteredAircraftPluginsCount());

		pluginsManager.stop();

		Thread.sleep(2500);
	}
}
