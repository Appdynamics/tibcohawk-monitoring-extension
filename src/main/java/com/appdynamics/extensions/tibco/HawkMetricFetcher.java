/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */

package com.appdynamics.extensions.tibco;

import COM.TIBCO.hawk.console.hawkeye.AgentManager;
import COM.TIBCO.hawk.console.hawkeye.TIBHawkConsole;
import COM.TIBCO.hawk.console.hawkeye.TIBHawkConsoleFactory;
import COM.TIBCO.hawk.talon.DataElement;
import COM.TIBCO.hawk.talon.MethodInvocation;
import COM.TIBCO.hawk.talon.MicroAgentData;
import COM.TIBCO.hawk.talon.MicroAgentException;
import COM.TIBCO.hawk.talon.MicroAgentID;
import COM.TIBCO.hawk.utilities.misc.HawkConstants;
import com.appdynamics.extensions.AMonitorTaskRunnable;
import com.appdynamics.extensions.MetricWriteHelper;
import com.appdynamics.extensions.conf.MonitorContextConfiguration;
import com.appdynamics.extensions.logging.ExtensionsLoggerFactory;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Collections2;
import com.google.common.collect.Lists;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * @author Satish Muddam
 */
public class HawkMetricFetcher implements AMonitorTaskRunnable {

    private static final Logger logger = ExtensionsLoggerFactory.getLogger(HawkMetricFetcher.class);
    private MetricWriteHelper metricWriter;
    private static final String SELF_MICROAGENT_NAME = "COM.TIBCO.hawk.microagent.Self";

    private MonitorContextConfiguration configuration;
    private Map hawkConnection;
    private TibcoResultParser tibcoResultParser;
    private String hawkDomainDisplayName;
    private Method[] methods;
    private Integer numberOfThreadsPerDomain;
    private Pattern microagentDisplayNamePattern;
    private List<Integer> bwMicroagentDisplayNameRegexGroups;
    private String bwMicroagentDisplayNameRegexGroupSeparator;

    public HawkMetricFetcher(MetricWriteHelper metricWriter, MonitorContextConfiguration configuration, Map hawkConnection, Method[] methods, Integer numberOfThreadsPerDomain) {
        this.metricWriter=metricWriter;
        this.configuration = configuration;
        this.hawkConnection = hawkConnection;
        hawkDomainDisplayName = (String) hawkConnection.get("displayName");
        tibcoResultParser = new TibcoResultParser();
        this.methods = methods;
        this.numberOfThreadsPerDomain = numberOfThreadsPerDomain;
        this.microagentDisplayNamePattern = Pattern.compile((String) hawkConnection.get("bwMicroagentDisplayNamePattern"));
        this.bwMicroagentDisplayNameRegexGroups = (List<Integer>) hawkConnection.get("bwMicroagentDisplayNameRegexGroups");
        this.bwMicroagentDisplayNameRegexGroupSeparator = (String) hawkConnection.get("bwMicroagentDisplayNameRegexGroupSeparator");
    }

    @Override
    public void run() {
        AgentManager agentManager = null;
        try {
            agentManager = connect();
            List<MicroAgentID> bwMicroagents = getBWMicroagents(agentManager);

            if (bwMicroagents == null || bwMicroagents.isEmpty()) {
                logger.error("No BW microagents found for the specified patterns. Exiting the process.");
                return;
            }
            List<Method> methodsToExecute = Lists.newArrayList(this.methods);
            Collection<Method> enabledMethodsToExecute = filterEnabled(methodsToExecute);
            CountDownLatch countDownLatch = new CountDownLatch(enabledMethodsToExecute.size() * bwMicroagents.size());
            for (Method method : enabledMethodsToExecute) {
                executeMethod(agentManager, bwMicroagents, method, countDownLatch);
            }
            countDownLatch.await();
            //Shutting down the agentManager after executing all the tasks.
            logger.info("Shutting down the Tibco Hawk connection");
            agentManager.shutdown();
        } catch (Exception e) {
            logger.error("Error while collecting metrics from domain [" + hawkDomainDisplayName + "]", e);
        } finally {
            //Trying to shutting down the agentManager again, useful if some error occurred in try and could not shutdown there.
            if (agentManager != null) {
                logger.info("Verifying Tibco Hawk connection status and closing if required.");
                agentManager.shutdown();
            }
        }
    }

    Predicate<Method> predicate = new Predicate<Method>() {
        public boolean apply(Method input) {
            Boolean enabled = input.isEnabled();
            if (!enabled) {
                logger.info("Method " + input.getMethodName() + " is not enabled, hence skipping");
                return false;
            } else {
                return true;
            }
        }
    };

    private Collection<Method> filterEnabled(List<Method> methodsToExecute) {
        return Collections2.filter(methodsToExecute, predicate);
    }

    private List<MicroAgentID> getBWMicroagents(AgentManager agentManager) {
        List<String> bwMicroagentNameMatcher = (List) hawkConnection.get("bwMicroagentNameMatcher");

        MicroAgentID[] selfMicroAgentIDs = null;
        try {
            selfMicroAgentIDs = agentManager.getMicroAgentIDs(SELF_MICROAGENT_NAME, 1);
        } catch (MicroAgentException e) {
            logger.error("Error while trying to get microagents from [ " + SELF_MICROAGENT_NAME + " ]", e);
            throw new RuntimeException("Error while trying to get microagents from [ " + SELF_MICROAGENT_NAME + " ]", e);
        }

        if (selfMicroAgentIDs == null || selfMicroAgentIDs.length <= 0) {
            logger.error("Unable to find micro agent [ " + SELF_MICROAGENT_NAME + " ]");
            throw new IllegalArgumentException("Unable to find micro agent [ " + SELF_MICROAGENT_NAME + " ]");
        }

        Object methodResult = executeMethod(agentManager, selfMicroAgentIDs[0], "getMicroAgentInfo", null);
        List<String> bwMicroAgentIdNames = tibcoResultParser.parseMicroAgentInfoResult(methodResult, bwMicroagentNameMatcher);

        List<MicroAgentID> bwMicroAgentIds = new ArrayList<MicroAgentID>();

        for (String bwMicroAgentId : bwMicroAgentIdNames) {
            try {
                MicroAgentID[] microAgentIDs = agentManager.getMicroAgentIDs(bwMicroAgentId);
                bwMicroAgentIds.add(microAgentIDs[0]);
            } catch (MicroAgentException e) {
                logger.error("Unable to get the microagent with name [" + bwMicroAgentId + "]");
            }
        }
        return bwMicroAgentIds;
    }

    private AgentManager connect() {
        String hawkDomain = (String) hawkConnection.get("hawkDomain");
        String transportType = (String) hawkConnection.get("transportType");

        Map<String, Object> result = new HashMap<String, Object>();
        result.put(HawkConstants.HAWK_DOMAIN, hawkDomain);
        if (HawkConstants.HAWK_TRANSPORT_TIBEMS.equals(transportType)) {
            result.put(HawkConstants.HAWK_TRANSPORT, HawkConstants.HAWK_TRANSPORT_TIBEMS);

            String emsURL = (String) hawkConnection.get("emsURL");
            String emsUserName = (String) hawkConnection.get("emsUserName");
            String emsPassword = (String) hawkConnection.get("emsPassword");

            if (Strings.isNullOrEmpty(hawkDomain) || Strings.isNullOrEmpty(emsURL)) {
                logger.error("Please provide hawkDomain and emsURL in the config.");
                throw new IllegalArgumentException("Please provide hawkDomain and emsURL in the config.");
            }
            result.put(HawkConstants.HAWK_EMS_URL, emsURL);
            result.put(HawkConstants.HAWK_EMS_USERNAME, emsUserName);
            result.put(HawkConstants.HAWK_EMS_PWD, emsPassword);
        } else if (HawkConstants.HAWK_TRANSPORT_TIBRV.equals(transportType)) {
            result.put(HawkConstants.HAWK_TRANSPORT, HawkConstants.HAWK_TRANSPORT_TIBRV);

            String rvService = (String) hawkConnection.get("rvService");
            String rvNetwork = (String) hawkConnection.get("rvNetwork");
            String rvDaemon = (String) hawkConnection.get("rvDaemon");

            if (Strings.isNullOrEmpty(hawkDomain) || Strings.isNullOrEmpty(rvService)
                    || Strings.isNullOrEmpty(rvNetwork) || Strings.isNullOrEmpty(rvDaemon)) {
                logger.error("Please provide hawkDomain, rvService, rvNetwork, rvDaemon in the config.");
                throw new IllegalArgumentException("Please provide hawkDomain, rvService, rvNetwork, rvDaemon in the config.");
            }

            result.put(HawkConstants.RV_SERVICE, rvService);
            result.put(HawkConstants.RV_NETWORK, rvNetwork);
            result.put(HawkConstants.RV_DAEMON, rvDaemon);
        } else {
            logger.error("Invalid transport type [ " + transportType + " ] specified. Supported transport types are tibrv and tibems.");
            throw new IllegalArgumentException("Invalid transport type [ " + transportType + " ] specified. Supported transport types are tibrv and tibems.");
        }
        AgentManager agentManager = null;
        try {
            TIBHawkConsole hawkConsole = TIBHawkConsoleFactory.getInstance().createHawkConsole(result);
            agentManager = hawkConsole.getAgentManager();
            agentManager.initialize();
            logger.info("Connection to Tibco Hawk successful");
        } catch (Exception e) {
            logger.error("Exception while connecting to hawk", e);
            throw new RuntimeException("Exception while connecting to hawk", e);
        }
        return agentManager;
    }

    private void executeMethod(final AgentManager agentManager, List<MicroAgentID> bwMicroagents, final Method method, final CountDownLatch countDownLatch) {

        ExecutorService executorService = Executors.newFixedThreadPool(numberOfThreadsPerDomain);

        for (final MicroAgentID microAgentID : bwMicroagents) {
            executorService.execute(new Runnable() {
                public void run() {
                    try {
                        logger.debug("Executing in thread " + Thread.currentThread().getName());
                        logger.debug("Executing method [" + method.getMethodName() + "] on microagent [" + microAgentID.getName() + "]");
                        Object methodResult = executeMethod(agentManager, microAgentID, method.getMethodName(), null);
                        logger.trace("Method [" + method.getMethodName() + "] result on microagent [" + microAgentID.getName() + "] is [" + methodResult + "]");
                        String microagentDisplayName = microAgentID.getName();
                        StringBuilder displayNameBuilder = new StringBuilder();
                        try {
                            Matcher matcher = microagentDisplayNamePattern.matcher(microagentDisplayName);
                            if (matcher.find()) {
                                int groupCount = matcher.groupCount();
                                boolean failed = false;
                                for (Integer group : bwMicroagentDisplayNameRegexGroups) {
                                    if (group <= groupCount) {
                                        if (displayNameBuilder.length() > 0) {
                                            displayNameBuilder.append(bwMicroagentDisplayNameRegexGroupSeparator);
                                        }
                                        displayNameBuilder.append(matcher.group(group));
                                    } else {
                                        failed = true;
                                        logger.info("Invalid group provided in bwMicroagentDisplayNameRegexGroups, using the microagent full name");
                                        break;
                                    }
                                }
                                if (!failed) {
                                    microagentDisplayName = displayNameBuilder.toString();
                                }
                            } else {
                                logger.info("bwMicroagentDisplayNamePattern could not find a matched group, using the microagent full name");
                            }
                        } catch (Exception e) {
                            logger.warn("Error while trying to match bwMicroagentDisplayNamePattern with the microagent name, using the microagent full name");
                        }
                        printData(method.getMethodName(), null, methodResult, method, microagentDisplayName);
                    } finally {
                        countDownLatch.countDown();
                    }
                }
            });
        }
        executorService.shutdown();
    }

    private Object executeMethod(AgentManager agentManager, MicroAgentID microAgentID, String methodName, DataElement[] args) {

        MethodInvocation mi = new MethodInvocation(methodName, args);
        try {
            MicroAgentData m = agentManager.invoke(microAgentID, mi);
            return m.getData();
        } catch (Exception e) {
            logger.error("Error while executing method [ " + methodName + "] on microagent [" + microAgentID.getName() + "]", e);
        }
        return null;
    }

    private void printData(String methodName, String methodDisplayName, Object methodResult, Method method, String agentDisplayName) {
        StringBuilder methodNameWithDisplayName = new StringBuilder(methodName);
        if (methodDisplayName != null && methodDisplayName.length() > 0) {
            methodNameWithDisplayName.append("|").append(methodDisplayName);
        }

        TibcoResultParser tibcoResultParser = new TibcoResultParser();
        List<TibcoMetric> tibcoMetrics = tibcoResultParser.parseResult(methodResult, method);

        logger.debug("Collected " + tibcoMetrics.size() + " metrics for method [" + methodName + "] on microagent [" + agentDisplayName + "]");

        for (TibcoMetric tibcoMetric : tibcoMetrics) {
            metricWriter.printMetric(configuration.getMetricPrefix() + "|" + hawkDomainDisplayName + "|" + agentDisplayName + "|" + methodNameWithDisplayName + "|" + tibcoMetric.getFullPath(), tibcoMetric.getValue(), tibcoMetric.getMetricType());
        }
    }

    @Override
    public void onTaskComplete() {
        logger.info("Completed Task for hawkConnection {}",hawkDomainDisplayName);
    }
}
