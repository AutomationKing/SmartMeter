Feature: ValidateEventHub
	In order to validate the streaming data coming from live stream to eventhub 
Background: 
Given Api details of the services should be exposed
And Event hub connection string should be available

| EventhubConnectionString | EventHubName | StorageAccountName | StorageAccountKey | StorageConnectionString |
| test                     | test         | test               |test               |      test               |

@mytag
Scenario: Validate the live data set with event hub
	#Given The data set is triggered from Source

	#And the data reaches to azure eventhub 
	#When we validate the "source" and "eventhub" 
	#Then the proper data should have reached to "eventhub"
