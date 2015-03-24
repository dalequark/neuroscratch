import java.util.*;
import java.io.*;
import java.text.*;
import java.util.concurrent.locks.*;

public class EEGJournal{

  private static boolean DEBUG = true;
  private int participantNum;
  private int trialLength;
  public String fileName;
  private PrintWriter writer;
  private Queue<Epoch> epochQueue;
  private int numEpochs;
  private Epoch thisEpoch;


  public EEGJournal(String outputDir, int participantNum, String header, int trialLength) throws IOException{
    this.trialLength = trialLength;
    SimpleDateFormat ft = new SimpleDateFormat("yyyy.MM.dd.hh.mm");
    fileName = outputDir + "/journal_" + participantNum + "_" + ft.format(new Date());
    this.participantNum = participantNum;
    Date thisDate = new Date();
    numEpochs = 0;
    epochQueue = new LinkedList<Epoch>();
    new File(fileName);
    writer = new PrintWriter(fileName);
    System.out.println("New EEG Journal, writing to file " + fileName);
    // write header data to file
    writer.println(header);
    writer.println("-endheader-");
    writer.flush();
  }

  public EEGJournal(String outputDir, int participantNum, int trialLength) throws IOException{
    this(outputDir, participantNum,  "Participant " + participantNum + ", Date: " + new Date(), trialLength);
  }



  public String getFilename(){
    return fileName;
  }

  public synchronized void addEpoch(String epochType){
    thisEpoch = new Epoch(epochType, numEpochs);
    writer.println("Epoch " + thisEpoch.epochNum + " Type: " + thisEpoch.epochType);
    numEpochs++;
    epochQueue.add(thisEpoch);
    //writer.println(thisEpoch.getHeader());
  }

  // We only keep track of the ratio with which the pictures were displayed,
  // since which images were displayed is determined in advance.
  public synchronized void addTrial(double ratio, String im1, String im2){
    thisEpoch.addTrial(ratio, im1, im2);
  }

  /* End trial (with response times)*/
  public synchronized void endTrial(long timeImageOnset, long timeOfResponse, long responseTime, boolean correct){
    thisEpoch.endTrial(timeImageOnset, timeOfResponse, responseTime, correct);
  }

  // End trial (without response)
  public synchronized void endTrial(long timeImageOnset, boolean correct){
    thisEpoch.endTrial(timeImageOnset, -1, -1, correct);
  }

  public synchronized void close(){
    System.out.println("Closing journal");
    /*
    for(Epoch thisEpoch : epochQueue){
      writer.println("Epoch " + thisEpoch.epochNum + " Type: " + thisEpoch.epochType);
      for(Trial trial : thisEpoch.trialQueue){
        writer.println(trial);
      }
    }
    */
    writer.close();
  }

  public class Epoch{

    String epochType;
    Queue<Trial> trialQueue;
    int epochNum;
    Trial thisTrial;
    int trialNum;

    public Epoch(String epochType, int epochNum){
      this.epochType = epochType;
      this.epochNum = epochNum;
      this.trialNum = 0;
      trialQueue = new LinkedList<Trial>();
    }

    public String getHeader(){
      return "Epoch: " + epochNum + ", Type: " + epochType;
    }

    public void addTrial(double ratio, String im1, String im2){
      thisTrial = new Trial(ratio, trialNum++, im1, im2);
      trialQueue.add(thisTrial);
    }

    // Set image onset details, response times, etc.
    public void endTrial(long timeImageOnset, long timeOfResponse, long responseTime, boolean correct){
      thisTrial.stimulusOnset = timeImageOnset;
      thisTrial.timeOfResponse = timeOfResponse;
      thisTrial.responseTime = responseTime;
      thisTrial.correct = correct;
      thisTrial.stimulusOffset = timeImageOnset + trialLength;
      writer.println(thisTrial);
      writer.flush();
      if(DEBUG) System.out.println(thisEpoch.thisTrial);

    }

  }

  public class Trial{
    int trialNum;
    boolean correct;
    double ratio;
    long stimulusOnset;
    long timeOfResponse;
    long responseTime;
    long stimulusOffset;
    String im1;
    String im2;

    public Trial(double ratio, int trialNum, String im1, String im2){
      this.im1 = im1;
      this.im2 = im2;
      this.trialNum = trialNum;
      this.ratio = ratio;
    }


    public String toString(){
      return "Trial:" + trialNum + ",ImageRatio:" + ratio +
        ",StimOnset:" + stimulusOnset + ",StimOffset:" + stimulusOffset + ",TimeOfResponse:" + timeOfResponse +
        ",ResponseTime:" + responseTime + ",Correct:" + correct;
    }

  }

}
