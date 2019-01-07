using System;
using TechTalk.SpecFlow;
using Microsoft.ServiceBus.Messaging;

using TechTalk.SpecFlow.Assist;
using StepDefinations;
using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Threading.Tasks;
using System.IO;

using CsvHelper;
using OfficeOpenXml;
using System.Collections.Generic;
using System.Regex;
using Newtonsoft.Json.Linq;
using System.Linq;
using System.Collections;
using Microsoft.Azure.DataLake.Store;

namespace TestSmartMeter
{
    [Binding]
    public class ValidateEventHubSteps
    {
        private static string _adlsg1AccountName = "sitpim.azuredatalakestore.net";
        static AdlsClient clientone = AdlsClient.CreateClient(_adlsg1AccountName, "6f1fe4b3-ad1f-45bb-bc50-dbfa7ea82c0b");

        static HttpClient client = new HttpClient();
       
        


        [Given(@"Api details of the services should be exposed")]
        public void GivenApiDetailsOfTheServicesShouldBeExposed()
        {

            foreach (var entry in clientone.EnumerateDirectory("/Test"))
            {
                Console.WriteLine("entry is====="+entry);
            }



            using (var readStream = new StreamReader(clientone.GetReadStream("@one.csv")))
            {
                string line;
                while ((line = readStream.ReadLine()) != null)
                {
                    Console.WriteLine(line);
                }
            }


            Console.WriteLine("test33");
        }
        [Given(@"Event hub connection string should be available")]
        public void GivenEventHubConnectionStringShouldBeAvailable(Table table)

        {

           
        // Keep the console window open in debug mode.




        /* var eventgettersetter = table.CreateInstance<EventHubValidation>();
         eventgettersetter.EventhubConnectionString;
         eventgettersetter.StorageAccountName;
         eventgettersetter.StorageAccountKey;
         eventgettersetter.EventHubName;
         eventgettersetter.StorageConnectionString;*/
        Console.WriteLine("test35");

        }

        [Given(@"The data set is triggered from Source")]
        public void GivenTheDataSetIsTriggeredFromSource()
        {

            var retriveIds = new EventHubValidation();
            ServicePointManager.SecurityProtocol = SecurityProtocolType.Ssl3 | SecurityProtocolType.Tls12 | SecurityProtocolType.Tls11 | SecurityProtocolType.Tls;
            char[] charsToTrim = { ' ', ' ', '\'', '_' };
            string html = string.Empty;
            string url = @"https://data.bathhacked.org/resource/55f4-6jtr.csv";

            HttpWebRequest request = (HttpWebRequest)WebRequest.Create(url);


            request.AutomaticDecompression = DecompressionMethods.GZip;

            using (HttpWebResponse response = (HttpWebResponse)request.GetResponse())


            using (Stream stream = response.GetResponseStream())
            using (StreamReader reader = new StreamReader(stream))



            {
                List<string> strarray = new List<string>();
                while(!reader.EndOfStream)
                {
                strarray.Add(reader.ReadLine());

                }
              //  reader.ReadToEnd().Trim(charsToTrim);
             //   Console.WriteLine("Content of file is "+ test);





              //  string[] apiContentFile = System.IO.File.ReadAllLines(@"C:/Users/ravi.rai/Desktop/csvfiles/main.csv");

                string[] EventDataLakeFile = System.IO.File.ReadAllLines(@"C:/Users/ravi.rai/Desktop/csvfiles/one.csv");

               IEnumerable<string> differenceQuery =
                strarray.Except(EventDataLakeFile);



                // Execute the query.
                Console.WriteLine("The following lines are in source API but not in Event Data Lake data");
                foreach (string s in differenceQuery)
                    Console.WriteLine(s);


                //new change
                // html.Replace(@"\", " ");
                //html.Replace(@"-> ", " ");
                //above
                //  StringContent Content = new StringContent(html, System.Text.Encoding.UTF8, "application/json");


                //    JArray jarray = JArray.Parse(html);





                //    ArrayList list = new ArrayList();
                //    int countofJarray = jarray.Count;

                //    Dictionary<string, List<String>> map = new Dictionary<string, List<String>>();
                //    foreach (JObject o in jarray.Children<JObject>())
                //    {
                //        JToken id = o.GetValue("id");
                //        JToken date = o.GetValue("date");
                //        JToken location = o.GetValue("location");
                //        JToken mpan = o.GetValue("mpan");
                //        JToken msid = o.GetValue("msid");
                //        String IdVal = id.ToString();



                //        // create list one and store values
                //        List<string> valSetOne = new List<string>();
                //        valSetOne.Add(location.ToString());
                //        valSetOne.Add(date.ToString());
                //        valSetOne.Add(mpan.ToString());
                //        valSetOne.Add(msid.ToString());
                //        //  valSetOne.Add(IdVal.ToString());
                //        Console.WriteLine("Key is ===="+ IdVal);
                //        map.Add(IdVal, valSetOne);


                //    }

                //    Console.WriteLine("Map contains" + map);
                //    foreach (KeyValuePair<string, List<string>> kvp in map)
                //    {
                //        foreach (string value in kvp.Value)
                //        {
                //            Console.WriteLine("Key = {0}, Value = {1}", kvp.Key, value);
                //        }

                //    }



                //}
            }
        }

        //list<string> splitted = new list<string>();
        //string filelist =html;
        //string[] tempstr;

        //tempstr = filelist.split(',');

        //foreach (string item in tempstr)
        //{
        //   if (!string.isnullorwhitespace(item))
        //   {
        //        splitted.add(item);
        //    }
        //}

        ////tempstr = filelist.split(',');

        //console.writeline("convereted csv" + splitted);

    
        
        [Given(@"the data reaches to azure eventhub")]
        public void GivenTheDataReachesToAzureEventhub()
        {
            /*string EventHubConnectionString = "{Event Hubs namespace connection string}";
            string EventHubName = "{Event Hub name}";
            string StorageAccountName = "{storage account name}";
            string StorageAccountKey = "{storage account key}";
            string StorageConnectionString = string.Format("DefaultEndpointsProtocol=https;AccountName={0};AccountKey={1}", StorageAccountName, StorageAccountKey);

            string EventProcessorHostName = Guid.NewGuid().ToString();
            EventProcessorHost eventProcessorHost = new EventProcessorHost(EventProcessorHostName, EventHubName, EventHubConsumerGroup.DefaultGroupName, EventHubConnectionString, StorageConnectionString);
            Console.WriteLine("Registering EventProcessor...");
            var options = new EventProcessorOptions();
            options.ExceptionReceived += (sender, e) => { Console.WriteLine(e.Exception); };
            eventProcessorHost.RegisterEventProcessorAsync<EventProcessor>(options).Wait();

            Console.WriteLine("Receiving. Press enter key to stop worker.");
            Console.ReadLine();
            eventProcessorHost.UnregisterEventProcessorAsync().Wait();*/
        }

        [When(@"we validate the ""(.*)"" and ""(.*)""")]
        public void WhenWeValidateTheAnd(string p0, string p1)
        {
            Console.WriteLine("test1223");
        }
        
        [Then(@"the proper data should have reached to ""(.*)""")]
        public void ThenTheProperDataShouldHaveReachedTo(string p0)
        {
            Console.WriteLine("test98");
        }

        public string GetCSV(string url)
        {
            HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
            HttpWebResponse resp = (HttpWebResponse)req.GetResponse();

            StreamReader sr = new StreamReader(resp.GetResponseStream());
            string results = sr.ReadToEnd();
            sr.Close();

            return results;
        }
       
        public void readJson(string content)
        {

            

                string jsonString = content;
                JArray jArray = JArray.Parse(jsonString);
                string displayName = (string)jArray.SelectToken("id");
                string type = (string)jArray.SelectToken("location");
                string value = (string)jArray.SelectToken("postcode");
                Console.WriteLine("{0}, {1}, {2}", displayName, type, value);
                JArray Ids = (JArray)jArray.SelectToken("id");
                foreach (JToken id in Ids)
                {
                    type = (string)id.SelectToken("type");
                    value = (string)id.SelectToken("value");
                    Console.WriteLine("{0}, {1}", type, value);
                }

                Console.WriteLine("Done.");
                Console.ReadLine();
            
        }




    }
}
