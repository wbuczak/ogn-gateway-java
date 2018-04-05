/**
 * Copyright (c) 2014 OGN, All Rights Reserved.
 */

package org.ogn.gateway;

import org.apache.log4j.xml.DOMConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class OgnGateway {

	private static final Logger LOG = LoggerFactory.getLogger(OgnGateway.class);

	private static void configureLogging() {
		final String log4jConf = System.getProperty("log4j.configuration");

		if (log4jConf != null)
			try {
				// Load the log4j configuration and create conf file watch-dog
				DOMConfigurator.configureAndWatch(System.getProperty("log4j.configuration"), 30 * 1000);
			} catch (final Exception ex) {
				System.err.println("Unable to load log4j configuration file : " + ex.getMessage());
				ex.printStackTrace();
			}
	}

	public static void main(String[] args) {
		configureLogging();
		LOG.info("starting OGN gateway process");
		@SuppressWarnings("resource")
		final ClassPathXmlApplicationContext ctx =
				new ClassPathXmlApplicationContext("classpath:application-context.xml");
		ctx.getEnvironment().setDefaultProfiles("PRO");
		// ctx.getEnvironment().setActiveProfiles("TEST");
		ctx.refresh();

		final Configuration conf = ctx.getBean(Configuration.class);
		LOG.info("simulation mode: {}", conf.isSimulationModeOn() ? "ON" : "OFF");
	}
}