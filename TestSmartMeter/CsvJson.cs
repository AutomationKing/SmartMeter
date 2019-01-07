using Newtonsoft.Json;
using System;
using System.Collections.Generic;
using System.Linq;
using System.Text;
using System.Threading.Tasks;
namespace StepDefinations
{

    public class testtt
    {
        public void GivenApiDetailsOfTheServicesShouldBeExposed()
        {
            var csv = new List<string[]>(); // or, List<YourClass>
            var lines = System.IO.File.ReadAllLines(@"C:\file.txt");
            foreach (string line in lines)
                csv.Add(line.Split(',')); // or, populate YourClass          
         //   string json = new
              //  System.Web.Script.Serialization.JavaScriptSerializer().Serialize(csv);
            




        }




    }
}
