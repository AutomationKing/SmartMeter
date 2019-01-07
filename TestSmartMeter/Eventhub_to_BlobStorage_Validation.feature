Feature: Eventhub_to_BlobStorage_Validation
	To validate the data flow and storage from eventhub to Azure Blob storage 



	Background: 
	Given Event hub connection string should be available
	| EventhubConnectionString | EventHubName |
	| test                     | test         |

	And Azure blob storage should be available 
	| ConnectionString | Blob        |
	|         test         |   test   |

@mytag
Scenario: Validate the live data set with azure blob
	Given Dataset should have ben triggered from eventhub
	And the data should have reached to azure blob
	When we validate the 'eventhub' and 'blob' 
	Then the proper data should have reached to 'blob'
