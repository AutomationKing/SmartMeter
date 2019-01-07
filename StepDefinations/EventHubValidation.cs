using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
namespace StepDefinations
{
    
    public class EventHubValidation
    {
        public string EventhubConnectionString { get; set; }
        public string EventHubName { get; set; }
        public string StorageAccountName { get; set; }
        public string StorageAccountKey { get; set; }
        public string StorageConnectionString { get; set; }
        public DateTime Date { get; set; }
        public string Location { get; set; }
        public String Postcode { get; set; }
        public string Units { get; set; }
        public int TotalUnits { get; set; }

       
        public string Id { get; set; }

        public int _00_30 { get; set; }
        public string Mpan { get; set; }
        public string Msid { get; set; }
       








    }
}
