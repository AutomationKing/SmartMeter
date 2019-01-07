using System;
using TechTalk.SpecFlow;

namespace TestSmartMeter
{
    [Binding]
    public class AzureDataValidation
    {
        [Given(@"Azure blob storage should be available")]
        public void GivenAzureBlobStorageShouldBeAvailable(Table table)
        {
            Console.WriteLine("ravi");
        }
        
        [Given(@"Dataset should have ben triggered from eventhub")]
        public void GivenDatasetShouldHaveBenTriggeredFromEventhub()
        {
            Console.WriteLine("ravi1");
        }
        
        [Given(@"the data should have reached to azure blob")]
        public void GivenTheDataShouldHaveReachedToAzureBlob()
        {
            Console.WriteLine("ravi23");
        }
        [When(@"we validate the '(.*)' and '(.*)'")]
        public void WhenWeValidateTheAnd(string p0, string p1)
        {
            Console.WriteLine("ravi567");
        }
        [Then(@"the proper data should have reached to '(.*)'")]
        public void ThenTheProperDataShouldHaveReachedTo(string p0)
        {
            Console.WriteLine("ravi9876");
        }

    }
}
