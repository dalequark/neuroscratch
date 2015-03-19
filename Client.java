import java.util.Collection;
import java.net.*;
import java.io.*;
import java.util.*;

public class Client{

  public static void main(String args[]){
    String hostName = args[0];
    int portNumber = Integer.parseInt(args[1]);
    char[] buff = new char[10];
    try{
        Socket echoSocket = new Socket(hostName, portNumber);
        PrintWriter out =
            new PrintWriter(echoSocket.getOutputStream(), true);
        BufferedReader in =
            new BufferedReader(
                new InputStreamReader(echoSocket.getInputStream()));
        BufferedReader stdIn =
            new BufferedReader(
                new InputStreamReader(System.in));
        String userInput;
        while(true){
          if((userInput = stdIn.readLine()) != null){
            out.println(userInput);
          }
          if(in.ready()){
            in.read(buff, 0, 10);
            System.out.println(buff);
          }
          else{
            System.out.println("Input not ready");
          }
        }
    }
    catch(Exception e){
      System.out.println("error");
      System.out.println(e);
    }

  }

}
