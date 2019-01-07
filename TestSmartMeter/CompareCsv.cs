using System;
using System.IO;
using System.Threading;
using System.Linq;
using System.Text;
using System.Collections.Generic;
using System.Security.Cryptography.X509Certificates; // Required only if you are using an Azure AD application created with certificates



namespace TestSmartMeter
{
    class CompareCsv
    {


        static void Main()
        {
            // Create the IEnumerable data sources.

      
            string[] names1 = System.IO.File.ReadAllLines(@"C:/Users/ravi.rai/Desktop/csvfiles/file.csv");
            string[] names2 = System.IO.File.ReadAllLines(@"C:/Users/ravi.rai/Desktop/csvfiles/nextfile.csv");

            // Create the query. Note that method syntax must be used here.
            IEnumerable<string> differenceQuery =
              names1.Except(names2);

            // Execute the query.
            Console.WriteLine("The following lines are in names1.txt but not names2.txt");
            foreach (string s in differenceQuery)
                Console.WriteLine(s);

            // Keep the console window open in debug mode.
            Console.WriteLine("Press any key to exit");
            Console.ReadKey();
        }




    }
}
