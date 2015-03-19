import java.lang.Runtime;
import java.io.*;

public class PythonCommander{

  public static String PYTHON_FILENAME = "classifier.py";
  BufferedReader input;
  BufferedWriter output;
  BufferedReader error;
  double[] model;

  public PythonCommander() throws Exception{
  }

  public String buildModel(String fileName, String logName) throws Exception{
    final Process p = Runtime.getRuntime().exec("python " + PYTHON_FILENAME);
    output = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
    input = new BufferedReader(new InputStreamReader(p.getInputStream()));
    error = new BufferedReader(new InputStreamReader(p.getErrorStream()));
    String command = "buildModel('" + fileName + "','" + logName + "')";
    output.write(command);
    output.close();
    System.out.println("sent command");
    String err;
    if(error.ready())
    {
      while((err = error.readLine()) != null) System.out.println(err);
      return "failed";
    }
    String res;
    String response = "";
    while((res = input.readLine()) != null){
      response += res;
    }

    return response;

  }

  public boolean classify(String data){
    return false;
  }

  public static void main(String[] args){
    PythonCommander comm;
    try{
      comm = new PythonCommander();
      comm.buildModel("test.txt", "test2.txt");
    }
    catch(Exception e)
    {
      System.out.println("Couldn't start python commander");
      e.printStackTrace();
      return;
    }
    String data = "dogs";
    System.out.println(comm.classify(data));

    }




}
