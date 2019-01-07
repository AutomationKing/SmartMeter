using System;
using System.IO;
using System.Threading;
using System.Linq;
using System.Text;
using System.Collections.Generic;
using System.Security.Cryptography.X509Certificates; // Required only if you are using an Azure AD application created with certificates

using Microsoft.Rest;
using Microsoft.Rest.Azure.Authentication;
using Microsoft.Azure.DataLake.Store;
using Microsoft.IdentityModel.Clients.ActiveDirectory;

namespace TestSmartMeter
{
    class DataLakeAccess
    {
        private static string _adlsg1AccountName = "sitpim.azuredatalakestore.net";
        static AdlsClient client = AdlsClient.CreateClient(_adlsg1AccountName, "ravi.rai@asos.com");
        static void main()
        {

            //Read file contents
            using (var readStream = new StreamReader(client.GetReadStream("@one.csv")))
            {
                string line;
                while ((line = readStream.ReadLine()) != null)
                {
                    Console.WriteLine(line);
                }
            }

        }



    }
    }
    

