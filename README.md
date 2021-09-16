# AppDynamics Tibco BW (HAWK) Monitoring Extension


## Use Case

TIBCO BusinessWorks™ is a family of next-generation, industry-leading enterprise integration products designed to address the new integration challenges faced when transitioning to a digital business.
The Tibco BW Monitoring Extension executes BW methods using BW hawk microagents and presents them in the AppDynamics Metric Browser.

This extension works only with the standalone machine agent.

## Prerequisites

Before the extension is installed, the prerequisites mentioned [here](https://community.appdynamics.com/t5/Knowledge-Base/Extensions-Prerequisites-Guide/ta-p/35213) need to be met. Please do not proceed with the extension installation if the specified prerequisites are not met.

## Installation

1. Run 'mvn clean install' from tibcohawk-monitoring-extension
2. Copy and unzip TibcoHawkMonitor-\<version\>.zip from 'target' directory into \<machine_agent_dir\>/monitors/
3. Edit config.yml file in TibcoHawkMonitor and provide the required configuration (see Configuration section)
4. Configure BW hawk methods in metrics.xml file in TibcoHawkMonitor
5. Add the following Tibco jars to lib folder. In BW 5.9 all the jars are available in tibrv folder.
```
console.jar,console_agent_shared.jar,talon.jar,tibrvj.jar,util.jar,jms.jar,security.jar,tibcrypt.jar,tibjms.jar,tibrvjms.jar
```
6. Set system variables like RV_HOME, PATH and LD_LIBRARY_PATH. For more information take a look at https://docs.tibco.com/pub/api-exchange-gateway/2.2.0/doc/html/GUID-86868063-8A1E-4348-8A5A-8B73772036D8.html
7. Restart the Machine Agent.

Please place the extension in the **"monitors"** directory of your **Machine Agent** installation directory. Do not place the extension in the **"extensions"** directory of your **Machine Agent** installation directory.

## Configuration

### config.yml

**Note: Please avoid using tab (\t) when editing yaml files. You may want to validate the yaml file using a [yaml validator](https://jsonformatter.org/yaml-validator).**

| Param | Description | Example |
| ----- | ----- | ----- |
| displayName | Display name for the hawk domain | "Domin 1" |
| hawkDomain | BW Hawk domain from which we are trying to get the stats from | "testDomain" |
| rvService | RV service to use to connect to hawk | "7474" |
| rvNetwork | RV network to use to connect to hawk | ";" |
| rvDaemon | RV daemon to use to connect to hawk | "tcp:7474" |
| emsURL | EMS URL to use to connect to hawk | "tcp://localhost:7222" |
| emsUserName | EMS user to use to connect to hawk | "admin" |
| emsPassword | EMS userpassword to use to connect to hawk | "admin" |
| bwMicroagentNameMatcher | regex matcher to match and autodetect the BW hawk microagents  | ".\*bwengine.\*" |

### metrics.xml

This file contains the methods to execute using BW hawk micro agents and metrics to collect from the method results.

**Below is an example config for monitoring multiple BW  domains:**

~~~
#Metric prefix used when SIM is enabled for your machine agent
#metricPrefix: "Custom Metrics|Tibco BW|"

#This will publish metrics to specific tier
#Instructions on how to retrieve the Component ID can be found in the Metric Prefix section of https://community.appdynamics.com/t5/Knowledge-Base/How-do-I-troubleshoot-missing-custom-metrics-or-extensions/ta-p/28695
metricPrefix: "Server|Component:<COMPONENT_ID>|Custom Metrics|Tibco BW"

hawkConnection:
   - displayName: "RV Domain"
     hawkDomain: "testDomain"
     # Supported transport types are tibrv and tibems
     transportType: "tibrv"
     # RV transport properties
     rvService: "7474"
     rvNetwork: ";"
     rvDaemon: "tcp:7474"
     # EMS transport properties
     #emsURL: ""
     #emsUserName:
     #emsPassword:
     bwMicroagentNameMatcher: [".*bwengine.*testDomain.*"]
     #This pattern is matched with the BW microagent name and the name extracted from specified groups(i.e string matched in the parentheses) is used as the display name. If no group found or invalid group used we will use full microagent name.
     #Example, for "COM.TIBCO.ADAPTER.bwengine.testDomain.TestNew.Process Archive" using the below pattern, display name would be "testDomain-TestNew-Process Archive"
     bwMicroagentDisplayNamePattern: "COM.TIBCO.ADAPTER.bwengine\\.(.*)\\.(.*)\\.(.*)"
     bwMicroagentDisplayNameRegexGroups: [1, 2, 3]
     bwMicroagentDisplayNameRegexGroupSeparator: "-"
   - displayName: "EMS Domain"
     hawkDomain: "emsdomain"
     # Supported transport types are tibrv and tibems
     transportType: "tibems"
     # RV transport properties
     #rvService: "7474"
     #rvNetwork: ";"
     #rvDaemon: "tcp:7474"
     # EMS transport properties
     emsURL: "tcp://localhost:7222"
     emsUserName: "admin"
     emsPassword:
     bwMicroagentNameMatcher: [".*bwengine.*emsdomain.*"]
     #This pattern is matched with the BW microagent name and the name extracted from specified groups(i.e string matched in the parentheses) is used as the display name. If no group found or invalid group used we will use full microagent name.
     #Example, for "COM.TIBCO.ADAPTER.bwengine.testDomain.TestNew.Process Archive" using the below pattern, display name would be "testDomain-TestNew-Process Archive"
     bwMicroagentDisplayNamePattern: "COM.TIBCO.ADAPTER.bwengine\\.(.*)\\.(.*)\\.(.*)"
     bwMicroagentDisplayNameRegexGroups: [1, 2, 3]
     bwMicroagentDisplayNameRegexGroupSeparator: "-"

# number of concurrent tasks
numberOfThreads: 5

numberOfThreadsPerDomain: 5

#taskSchedule:
#    numberOfThreads: 1
#    taskDelaySeconds: 60
~~~

## Metrics
Metrics provided by this extension are depend on the methods and metrics configured in the metrics.xml. Below is list of methods and metrics they provide.

### GetMemoryUsage

| Metric | Description |
| ----- | ----- |
| TotalBytes | Total number of bytes allocated to the process engine. |
| FreeBytes | Total number of bytes that are not currently in use.  |
| UsedBytes |  Total number of bytes that are currently in use.  |
| PercentUsed | Percentage of total bytes that are in use.  |

### GetProcessCount

| Metric | Description |
| ----- | ----- |
| TotalRunningProcesses | Total number of currently executing process instances.  |


### GetProcessDefinitions

For each process definition following metrics are displayed

| Metric | Description |
| ----- | ----- |
| Created |  Number of process instances created for this process definition. |
| Suspended |  Number of times process instances have been suspended.  |
| Swapped | Number of times process instances have been swapped to disk.  |
| Queued | Number of times process instances have been queued for execution. |
| Aborted | Number of times process instances have been aborted.  |
| Completed | Number of process instances that have been successfully completed. |
| Checkpointed | Number of times process instances have executed a checkpoint. |
| TotalExecution | Total execution time (in milliseconds) for all successfully completed process instances. |
| AverageExecution | Average execution time (in milliseconds) for all successfully completed process instances. |
| TotalElapsed | Total elapsed time (in milliseconds) for all successfully completed process instances.  |
| AverageElapsed | Average elapsed clock time (in milliseconds) for all successfully completed process instances.  |
| MinElapsed | Elapsed clock time (in milliseconds) of the process instance that has completed in the shortest amount of elapsed time.  |
| MaxElapsed |  Elapsed clock time (in milliseconds) of the process instance that has completed in the longest amount of elapsed time. |
| MinExecution | Execution time (in milliseconds) of the process instance that has completed in the shortest amount of execution time. |
| MaxExecution | Execution time (in milliseconds) of the process instance that has completed in the longest amount of execution time.  |
| MostRecentExecutionTime | Execution time (in milliseconds) of the most recently completed process instance.  |
| MostRecentElapsedTime | Elapsed clock time (in milliseconds) of the most recently completed process instance.  |
| TimeSinceLastUpdate | Time (in milliseconds) since the statistics have been updated.  |
| CountSinceReset | Number of process instances that have completed since the last reset of the statistics. |


### GetActivities

For each activity in each process definition following metrics are displayed

| Metric | Description |
| ----- | ----- |
| ExecutionCount |  Number of times the activity has been executed.  |
| ElapsedTime | Total clock time (in milliseconds) used by all executions of this activity. This includes waiting time for Sleep, Call Process, and Wait For... activities.  |
| ExecutionTime | Total clock time (in milliseconds) used by all executions of this activity. This does not include waiting time for Sleep, Call Process, and Wait For... activities.  |
| ErrorCount |  Total number of executions of the activity that have returned an error.  |
| LastReturnCode | Status code returned by most recent execution of this activity. This can be either OK, DEAD, or ERROR.  |
| MinElapsedTime | Elapsed clock time (in milliseconds) of the activity execution that has completed in the shortest amount of elapsed time.  |
| MaxElapsedTime | Elapsed clock time (in milliseconds) of the activity execution that has completed in the longest amount of elapsed time.  |
| MinExecutionTime | Execution time (in milliseconds) of the activity execution that has completed in the shortest amount of execution time. |
| MaxExecutionTime | Execution time (in milliseconds) of the activity execution that has completed in the longest amount of execution time.  |
| TimeSinceLastUpdate | Time (in milliseconds) since the statistics have been updated.  |
| ExecutionCountSinceReset | Number of activity executions that have completed since the last reset of the statistics.  |


## Credentials Encryption
Please visit [this page](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-Password-Encryption-with-Extensions/ta-p/29397) to get detailed instructions on password encryption. The steps in this document will guide you through the whole process.

## Extensions Workbench
Workbench is an inbuilt feature provided with each extension in order to assist you to fine tune the extension setup before you actually deploy it on the controller. Please review the following document on [How to use the Extensions WorkBench](https://community.appdynamics.com/t5/Knowledge-Base/How-to-use-the-Extensions-WorkBench/ta-p/30130)

## Troubleshooting
1. Please follow the steps listed in this [troubleshooting-document](https://community.appdynamics.com/t5/Knowledge-Base/How-to-troubleshoot-missing-custom-metrics-or-extensions-metrics/ta-p/28695) in order to troubleshoot your issue. These are a set of common issues that customers might have faced during the installation of the extension.
2. Verify Machine Agent Data: Please start the Machine Agent without the extension and make sure that it reports data. Verify that the machine agent status is UP and it is reporting Hardware Metrics.
3. Tibco HAWK BW Microagents: Please verify that BW hawk micro agents are available using hawk display.
4. Check Logs: There could be some obvious errors in the machine agent logs. Please take a look.

## Contributing
Always feel free to fork and contribute any changes directly here on [GitHub](https://github.com/Appdynamics/tibcohawk-monitoring-extension/).

## Version
|          Name            |  Version   |
|--------------------------|------------|
|Extension Version         |2.0.0       |
|Last Update               |20/08/2021 |
|Change List|[ChangeLog](https://github.com/Appdynamics/tibcohawk-monitoring-extension/blob/master/CHANGELOG.md)|

**Note**: While extensions are maintained and supported by customers under the open-source licensing model, they interact with agents and Controllers that are subject to [AppDynamics’ maintenance and support policy](https://docs.appdynamics.com/latest/en/product-and-release-announcements/maintenance-support-for-software-versions). Some extensions have been tested with AppDynamics 4.5.13+ artifacts, but you are strongly recommended against using versions that are no longer supported.
