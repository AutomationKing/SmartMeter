using System;
using TechTalk.SpecFlow;

namespace TestSmartMeter
{
    [Binding]
    public class BlobToSqlValidationSteps
    {
        [Given(@"Sql server should be accessible")]
        public void GivenSqlServerShouldBeAccessible(Table table)
        {
            Console.WriteLine("test12");
        }
        
        [Given(@"Data is triggered from Blob Storage")]
        public void GivenDataIsTrgiggeredFromBlobStorage()
        {
            Console.WriteLine("test13");
        }
        
        [Given(@"has reached to Database")]
        public void GivenHasReachedToDatabase()
        {
            Console.WriteLine("test14");
        }
    }
}
