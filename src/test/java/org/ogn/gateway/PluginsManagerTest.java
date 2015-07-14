/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.replay;
import static org.junit.Assert.assertEquals;

import org.easymock.EasyMock;
import org.junit.Test;

public class PluginsManagerTest {

	Configuration conf;

	@Test(timeout = 4000)
	public void test() throws Exception {
		PluginsManager pluginsManager = new PluginsManager();

		conf = EasyMock.createMock(Configuration.class);
		expect(conf.getPluginsFolderName()).andReturn("src/test/resources/plugins");
		expectLastCall().atLeastOnce();

		expect(conf.getScanningInterval()).andReturn(500);
		expectLastCall().atLeastOnce();

		replay(conf);

		pluginsManager.setConfig(conf);

		assertEquals(0, pluginsManager.getRegisteredPluginsCount());
		pluginsManager.registerPlugins();
		// call more times the same registration
		pluginsManager.registerPlugins();
		pluginsManager.registerPlugins();

		// there should only be one plugin registered
		assertEquals(1, pluginsManager.getRegisteredPluginsCount());

		pluginsManager.init();

		Thread.sleep(2500);

		// there should only be one plugin registered
		assertEquals(1, pluginsManager.getRegisteredPluginsCount());

		pluginsManager.stop();
	}
}