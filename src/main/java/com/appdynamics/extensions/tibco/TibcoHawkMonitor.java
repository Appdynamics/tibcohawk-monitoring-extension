/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.tibco;


import com.appdynamics.extensions.ABaseMonitor;

import com.appdynamics.extensions.TasksExecutionServiceProvider;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.appdynamics.extensions.util.AssertUtils;

import org.slf4j.Logger;

import java.util.List;
import java.util.Map;


/**
 * @author Satish Muddam
 */
public class TibcoHawkMonitor extends ABaseMonitor {

    private static final String METRIC_PREFIX = "Custom Metrics|Tibco|";

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(TibcoHawkMonitor.class);

    private static final String CONFIG_ARG = "config-file";
    private static final String METRIC_ARG = "metric-file";

    private long previousTimestamp = 0;
    private long currentTimestamp = System.currentTimeMillis();

    private boolean initialized;
    //private MonitorConfiguration configuration;


    protected static String getImplementationVersion() {
        return TibcoHawkMonitor.class.getPackage().getImplementationTitle();
    }

    protected String getDefaultMetricPrefix() {
        return METRIC_PREFIX;
    }

    public String getMonitorName() {
        return "Tibco Hawk Monitor";
    }

    @Override
    protected void initializeMoreStuff(Map<String, String> args) {
        this.getContextConfiguration().setMetricXml(args.get("metric-file"), Method.Methods.class);
    }

    protected void doRun(TasksExecutionServiceProvider tasksExecutionServiceProvider) {
        AssertUtils.assertNotNull(getContextConfiguration().getMetricsXml(), "Metrics xml not available");
        List<Map<String, ?>> servers = (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("hawkConnection");

        Method[] methods = ((Method.Methods) getContextConfiguration().getMetricsXml()).getMethods();
        Integer numberOfThreadsPerDomain = (Integer) getContextConfiguration().getConfigYml().get("numberOfThreadsPerDomain");

        if (methods == null || methods.length <= 0) {
            logger.error("Methods are not configured in the metrics.xml. Exiting the process");
            return;
        }

        previousTimestamp = currentTimestamp;
        currentTimestamp = System.currentTimeMillis();
        if (previousTimestamp != 0) {
            for (Map<String, ?> server : servers) {
                try {
                    HawkMetricFetcher task = new HawkMetricFetcher(tasksExecutionServiceProvider, this.getContextConfiguration(), server, methods, numberOfThreadsPerDomain);

                    tasksExecutionServiceProvider.submit((String) server.get("displayName"), task);
                } catch (Exception e) {
                    logger.error("Error while creating task for {}", Util.convertToString(server.get("displayName"), ""),e);
                }
            }
        }
    }

    protected List<Map<String, ?>> getServers() {
        return (List<Map<String, ?>>) getContextConfiguration().getConfigYml().get("hawkConnection");
    }

}
