Feature: BlobToSqlValidation
	Validate the data coming from Azure blob to Sql Server

	Background: 
	Given Sql server should be accessible
	| DBConnectionString | Username | Password |
	|            test    |test      |test      |
	And Azure blob storage should be available 
	| ConnectionString | Blob        |
	|         test         |   test   |

@mytag
Scenario: Validate the data coming from Azure blob to Sql Server
	Given Data is triggered from Blob Storage 
	And has reached to Database
	When we validate the 'database' and 'blob' 
	Then the proper data should have reached to 'database'
