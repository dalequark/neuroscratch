import java.io.*;
import java.net.*;
import java.util.Collection;

import org.java_websocket.WebSocket;
import org.java_websocket.WebSocketImpl;
import org.java_websocket.framing.Framedata;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.util.*;


public class AttentionExperiment extends WebSocketServer{

  static String hostName = "127.0.0.1";
  static int portNumber = 6789;

  static String eegOutputFileName = "eegdata.csv";
  // see what megan does here...
  static boolean DEBUG = true;
  int TRAINING_EPOCHS;
  int FEEDBACK_EPOCHS;
  static int TRIALS_PER_EPOCH = 8;
  int NUM_EPOCHS;
  static int NUM_IMAGES_PER_CATEGORY = 70;
  static int WEB_SOCKETS_PORT = 8885;
  /* in millis, how long is each trial? i.e. how long do they get to respond? */
  static int RESPONSE_TIME = 100*10;
  static double TEASE_RATIO = .1;
  /* image directories. please name them 1,2,3,...n.jpg */
  static String MALE_FACES ="./imagesRenamed/male_neut/";
  static String FEMALE_FACES ="./imagesRenamed/female_neut/";
  static String OUTDOOR_PLACES ="./imagesRenamed/outdoor/";
  static String INDOOR_PLACES ="./imagesRenamed/indoor/";
  static boolean FACES = true;
  static boolean PLACES = false;
  volatile boolean done = false;
  volatile boolean doneTraining = false;

  boolean realFeedback;
  boolean withEEG;
  boolean tcpPublish;
  Timer timer;
  EEGJournal journal;
  EEGLog eeglog;
  EEGLoggingThread logger;

  PrintWriter controlOut;
  BufferedReader controlIn;
  int PUBLISH_PORT = 50000;

  int participantNum;
  boolean[] epochArray;
  String[][] epochImageFiles;
  boolean[][] answers;
  boolean[] epochType;
  private boolean hasStarted;


  int currentEpoch;
  int currentTrial;
  Long timeOfResponse;
  Long responseTime;
  Long stimOnset;
  double thisRatio;

  Dfa dfa;

  public AttentionExperiment (int participantNum, String outputDir, boolean realFeedback, boolean withEEG, boolean tcpPublish, int trainEpochs, int feedbackEpochs) throws Exception{
    super( new InetSocketAddress( WEB_SOCKETS_PORT ) );

    System.out.println("Starting new GUI web socket on port " + WEB_SOCKETS_PORT);
    this.participantNum = participantNum;
    this.realFeedback = realFeedback;
    this.withEEG = withEEG;
    this.tcpPublish = tcpPublish;
    hasStarted = false;
    done = false;
    /* If we're using the emotiv, star the emotiv logging */
    if(withEEG){
      eeglog = new EEGLogReal();
      eeglog.tryConnect();
      System.out.println("Successfully connected to emotiv");
      eeglog.addUser();
      System.out.println("Successfully added user. Starting logging thread");
      logger = new EEGLoggingThread(eeglog, outputDir + "/eeg", participantNum, tcpPublish);
    }
    else{
      eeglog = new EEGLogFake();
      eeglog.tryConnect();
      System.out.println("Successfully connected to fake emotiv");
      eeglog.addUser();
      System.out.println("Successfully added user. Starting logging thread");
      logger = new EEGLoggingThread(eeglog, outputDir + "/eeg", participantNum, tcpPublish);
    }

    TRAINING_EPOCHS = trainEpochs;
    FEEDBACK_EPOCHS = feedbackEpochs;
    NUM_EPOCHS = TRAINING_EPOCHS + FEEDBACK_EPOCHS;

    /* Create python control port*/
    if(realFeedback){
      Socket controlSocket = new Socket("localhost", PUBLISH_PORT);
      controlOut = new PrintWriter(new DataOutputStream(controlSocket.getOutputStream()));
      controlIn = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
      controlOut.println("cont");
      controlOut.flush();
      System.out.println("Successfuly connected to python backend");

    }

    // Start timer to schedule recurring trials
    timer = new Timer();
    // Generate a unique epoch array (random) for this participant
    // If epochArray[0] is true, click yes for male faces. If epochArray[1]
    // is true, click yes for outdoor places.
    epochArray = getEpochArray(participantNum);
    // generate the list of image files corresponding to this epoch array
    epochImageFiles = getEpochImages(TEASE_RATIO);

    // Create journal, struct for storing participant responses, times, etc
    journal = new EEGJournal(outputDir + "/log", participantNum, getExperimentHeader(), RESPONSE_TIME);

    currentEpoch = 0;
    currentTrial = 0;

  }


@Override
public void onMessage( WebSocket conn, String message ) {
    // when we get a message
    if(DEBUG) System.out.println("Message from client " + message);
    // Got a response (it's elapsed time)
    if(message.charAt(0) == 'R'){
      timeOfResponse = System.currentTimeMillis();
      dfa.doNext(false);
    }
    else if(message.charAt(0) == 'C'){
      // doNext from user input
      dfa.doNext(false);
    }
    else if(message.charAt(0) == 'Q' && message.charAt(1) == 'q'){
      done = true;
      System.out.println("Quitting ... goodbye!");
      if(journal != null) journal.close();
      System.out.println("Closed journal");
      if(logger != null)  logger.close();
      System.out.println("Closed logger");
      if(controlOut != null)  controlOut.close();
      System.exit(0);
    }
}

private String getExperimentHeader(){
  String headerString = "/*********************************************/\n";
  headerString += "Participant Number: " + participantNum + "\n";
  headerString += "------Epochs-----\n\n";
  for(int i = 0; i < epochImageFiles.length; i++){
    headerString += "Epoch " + i + ": \n";
    for(String imageFile : epochImageFiles[i]){
      headerString += imageFile + "\n";
    }
    headerString+= "\n\n";
  }

  return headerString;
}

/*---- Websocket Server Stuff ----*/


@Override
public void onError(WebSocket conn, Exception e){
  System.out.println("Error with websocket connection: " +e);
  e.printStackTrace();
  return;
}

@Override
public void onClose( WebSocket conn, int code, String reason, boolean remote ){
  System.out.println(conn + " has disconnected");
  done = true;
  journal.close();
  if(logger != null)  logger.close();
  if(controlOut != null)  controlOut.close();
  return;
}

@Override
	public void onOpen( WebSocket conn, ClientHandshake handshake ) {
    System.out.println("Connected to " + conn);
    startExperiment();
  }

@Override
	public void onFragment( WebSocket conn, Framedata fragment ) {
		System.out.println( "received fragment: " + fragment );
}

/*--------*/





public void sendToAll( String text ) {
  if(DEBUG) System.out.println("Sending message " + text);
  Collection<WebSocket> con = connections();
  synchronized ( con ) {
    for( WebSocket c : con ) {
      c.send( text );
    }
  }
}



  /* Set up any scheduled processes */
  public void startExperiment(){
    // shows the first instruction.
    dfa = new Dfa();
    dfa.start();
  };

  /* Returns a vector, [clickFemale, clickIndoor],
  where clickFemale is true if array[0] = true */
  private boolean[] getEpochArray(int participantNum){

    // two tasks array determines which stimulu (male, outdoors, ex)
    // this participant should click for

    boolean[] twoTasks = new boolean[2];
    switch(participantNum % 4){
      case(1):
        twoTasks[0] = false;
        twoTasks[1] = false;
      case(2):
        twoTasks[0] = false;
        twoTasks[1] = true;
      case(3):
        twoTasks[0] = true;
        twoTasks[1] = false;
      case(4):
        twoTasks[0] = true;
        twoTasks[1] = true;
    }

    // trial order is chosen such that the average time of each task is the
    // same: AABBAABBA
    epochType = new boolean[NUM_EPOCHS];
    boolean startCategory = Math.random() > 0.5 ? FACES : PLACES;
    int i = 0;
    while(i < NUM_EPOCHS){
      epochType[i++] = startCategory;
      if(NUM_EPOCHS > i)  epochType[i++] = !startCategory;
      if(NUM_EPOCHS > i)  epochType[i++] = !startCategory;
      if(NUM_EPOCHS > i)  epochType[i++] = startCategory;
    }
    System.out.println("Start category is " + (startCategory == FACES? "faces" : "places" ));
    return twoTasks;
  }


  /* Returns an array filenames specifying images to be shown in each trial.
     Tease ratio is the ratio of images that come from the lure category */

  private String[][] getEpochImages(double tease_ratio){
    int num_lures = (int) Math.ceil(tease_ratio * TRIALS_PER_EPOCH);
    boolean trial_type;
    String[][] epochImages = new String[NUM_EPOCHS][];
    answers = new boolean[NUM_EPOCHS][TRIALS_PER_EPOCH];
    int rand_idx;

    // Create lures
    for(int epoch = 0; epoch < NUM_EPOCHS; epoch++){

      String[] thisEpochFaces = new String[TRIALS_PER_EPOCH];
      String[] thisEpochPlaces = new String[TRIALS_PER_EPOCH];

      for(int i = 0; i < num_lures; i++){

        // faces
        rand_idx = (int) Math.floor(Math.random() * NUM_IMAGES_PER_CATEGORY);
        if(epochArray[0]){
          // Get a random index for this image
          thisEpochFaces[i] = MALE_FACES + rand_idx + ".jpg";
        }
        else{
          thisEpochFaces[i] = FEMALE_FACES + rand_idx + ".jpg";
        }

        // places
        rand_idx = (int) Math.floor(Math.random() * NUM_IMAGES_PER_CATEGORY);
        if(epochArray[1]){
          // Get a random index for this image
          thisEpochPlaces[i] = OUTDOOR_PLACES + rand_idx + ".jpg";
        }
        else{
          thisEpochPlaces[i] = INDOOR_PLACES + rand_idx + ".jpg";
        }
      }
      // Create non-lures
      for(int i = num_lures; i < TRIALS_PER_EPOCH; i++){

        rand_idx = (int) Math.floor(Math.random() * NUM_IMAGES_PER_CATEGORY);
        if(epochArray[0]){
          // Get a random index for this image
          thisEpochFaces[i] = FEMALE_FACES + rand_idx + ".jpg";
        }
        else{
          thisEpochFaces[i] = MALE_FACES + rand_idx + ".jpg";
        }

        rand_idx = (int) Math.floor(Math.random() * NUM_IMAGES_PER_CATEGORY);
        if(epochArray[1]){
          // Get a random index for this image
          thisEpochPlaces[i] = INDOOR_PLACES + rand_idx + ".jpg";
        }
        else{
          thisEpochPlaces[i] = OUTDOOR_PLACES + rand_idx + ".jpg";
        }
      }

      // Create a new array and merge the lures with the non-lures,
      // first shuffling them
      Collections.shuffle(Arrays.asList(thisEpochFaces));
      Collections.shuffle(Arrays.asList(thisEpochPlaces));

      epochImages[epoch] = new String[TRIALS_PER_EPOCH * 2];
      for(int j = 0; j < TRIALS_PER_EPOCH; j++){
        epochImages[epoch][j*2] = thisEpochFaces[j];
        epochImages[epoch][j*2+1] = thisEpochPlaces[j];

        if(epochType[epoch] == FACES){
          // trial has a face, the lure is a male face
          if(thisEpochFaces[j].startsWith(FEMALE_FACES) && epochArray[0]){
            // Answer is correct when the participant should click
            answers[epoch][j] = true;
          }
          else if(thisEpochFaces[j].startsWith(MALE_FACES) && !epochArray[0]){
            answers[epoch][j] = true;
          }
          else{
            answers[epoch][j] = false;
          }
        }
        else{
          // trial has an indoor place, the lure is an outdoor place
          if(thisEpochPlaces[j].startsWith(INDOOR_PLACES) && epochArray[1]){
            // Answer is correct when the participant should click
            answers[epoch][j] = true;
          }
          else if(thisEpochPlaces[j].startsWith(OUTDOOR_PLACES) && !epochArray[1]){
            answers[epoch][j] = true;
          }
          else{
            answers[epoch][j] = false;
          }
        }
      }

    }


    return epochImages;

  }

  public enum State {
    START, START_INSTRUCT, EPOCH_INSTRUCT, TRIAL_NORESP, TRIAL_RESP, TRAIN,
    FB_EPOCH_INSTRUCT, FB_TRIAL_NORESP, FB_TRIAL_RESP, DONE
  }

  private class Dfa{

    private int epochNum;
    private int trialNum;
    private double thisRatio;
    private long stimOnset;
    private boolean shouldClick;
    private State state;

    /* Begin the experiment by showing the first instruction */
    public void start(){
      System.out.println("Starting experiment dfa");
      state = State.START;
      sendToAll("I, Welcome! Press any key to continue");
    }

    public synchronized void doNext(boolean fromTimer){
      switch(state) {
        case START:

          // Send beginning instructions command
          sendToAll("I, Welcome! In each trial, you will be given a task: either to recognize the gender of faces or the location of places. " +
          "When you are asked to identify faces, click any key when you see a " + (epochArray[0] ? "female" : "male") + " face, but do not" +
          " click at all when a " + (!epochArray[0] ? "female" : "male") + " face is shown.  Similarly, click any key when a shown location is " +
          ((epochArray[1]) ? "indoors" : "outdoors") + ", but do not click when the location is " + ((!epochArray[1]) ? "indoors" : "outdoors") +
          ". When the word 'faces' is shown on the screen before a task, you should complete the face identification task as described above, and ignore the " +
          "shown places. Similary, when the word 'places' is shown, you should complete the places task and ignore any faces.  Good luck! Press any key to continue");
          state = State.START_INSTRUCT;
          break;

        case START_INSTRUCT:
          epochNum = -1;
          sendToAll(getInstructionCommand(epochNum+1));
          thisRatio = 0.5;
          System.out.println("Beginning to log EEG data!");
          logger.start();
          state = State.EPOCH_INSTRUCT;
          break;

        case EPOCH_INSTRUCT:
          epochNum++;
          trialNum = 0;
          journal.addEpoch(epochType(epochNum));
          journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
            epochImageFiles[epochNum][trialNum%2+1]);
          sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
          sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
          stimOnset = System.currentTimeMillis();
          timer.schedule(new doNextLater(), RESPONSE_TIME);
          state = State.TRIAL_NORESP;
          break;

        case TRIAL_NORESP:
          shouldClick = answers[epochNum][trialNum];
          if(!fromTimer){
            state = State.TRIAL_RESP;
            responseTime = System.currentTimeMillis();
            journal.endTrial(stimOnset, timeOfResponse, responseTime, shouldClick);
          }
          else{
            journal.endTrial(stimOnset, !shouldClick);
            trialNum++;

            if(trialNum == TRIALS_PER_EPOCH){
              if(epochNum == TRAINING_EPOCHS - 1){
                    System.out.println("Training model");
                    sendToAll("I,One moment please!");
                    state = State.TRAIN;
                    // start training
                    if(realFeedback){
                      try{
                        logger.flush();
                        controlOut.println("model," + journal.fileName + "," + logger.fileName);
                        controlOut.flush();
                        String data = controlIn.readLine();
                        if(data.equals("ok")){
                          System.out.println("Model built successfully");
                        }
                        else{
                          realFeedback = false;
                          System.out.println("Could not make model: " + data.length());

                          for(int i = 0; i < data.length(); i++){
                            char c = data.charAt(i);
                            System.out.print(String.format("\\u%04x", (int)c) );
                          }
                        }
                      }
                      catch(Exception e){
                        realFeedback = false;
                        System.out.println("Python network error: " + e);
                      }
                    }

                    doNext(true);
                    return;
                }
                sendToAll(getInstructionCommand(epochNum+1));
                state = State.EPOCH_INSTRUCT;
            }
            else{
              journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
                epochImageFiles[epochNum][trialNum%2+1]);
              sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
              sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
              stimOnset = System.currentTimeMillis();
              timer.schedule(new doNextLater(), RESPONSE_TIME);
            }
          }
          break;

        case TRIAL_RESP:
          // Ignore second responses, in this case
          if(fromTimer){
            trialNum++;
            if(trialNum == TRIALS_PER_EPOCH){
              if(epochNum == TRAINING_EPOCHS - 1){
                System.out.println("Training model");
                sendToAll("I,One moment please!");
                state = State.TRAIN;
                // start training
                if(realFeedback){
                  try{
                    controlOut.println("model," + journal.fileName + "," + logger.fileName);
                    controlOut.flush();
                    String data = controlIn.readLine();
                    if(data.equals("ok")){
                      System.out.println("Model built successfully");
                    }
                    else{
                      System.out.println("Got data " +data );
                      realFeedback = false;
                    }
                  }
                  catch(Exception e){
                    realFeedback = false;
                    System.out.println("Python network error: " + e);
                  }
                }


                doNext(true);
                return;

              }
              sendToAll(getInstructionCommand(epochNum+1));
              state = State.EPOCH_INSTRUCT;
            }
            else{
              journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
                epochImageFiles[epochNum][trialNum%2+1]);
              sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
              sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
              stimOnset = System.currentTimeMillis();
              timer.schedule(new doNextLater(), RESPONSE_TIME);
              state = State.TRIAL_NORESP;
            }
          }
          break;

        case TRAIN:
          if(fromTimer){
            if(FEEDBACK_EPOCHS > 0){
              sendToAll(getInstructionCommand(epochNum+1));
              state = State.FB_EPOCH_INSTRUCT;
            }
            else{
              sendToAll("I,Done--congratulations!");
              state = State.DONE;
              journal.close();
              if(logger != null)  logger.close();
              if(controlOut != null)  controlOut.close();
              System.exit(0);
            }

          }
          break;


        case FB_EPOCH_INSTRUCT:
          epochNum++;
          trialNum = 0;
          journal.addEpoch(epochType(epochNum));
          thisRatio = getNewRatio(trialNum);
          journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
            epochImageFiles[epochNum][trialNum%2+1]);
          sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
          sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
          stimOnset = System.currentTimeMillis();
          timer.schedule(new doNextLater(), RESPONSE_TIME);
          state = State.FB_TRIAL_NORESP;
          break;

        case FB_TRIAL_NORESP:
          shouldClick = answers[epochNum][trialNum];
          if(!fromTimer){
            state = State.FB_TRIAL_RESP;
            responseTime = System.currentTimeMillis();
            journal.endTrial(stimOnset, timeOfResponse, responseTime, shouldClick);
          }
          else{
            journal.endTrial(stimOnset, !shouldClick);
            trialNum++;
            if(trialNum == TRIALS_PER_EPOCH){
              if(epochNum == NUM_EPOCHS - 1){
                sendToAll("I,Done--congratulations!");
                state = State.DONE;
                journal.close();
                if(logger != null)  logger.close();
                if(controlOut != null)  controlOut.close();
                System.exit(0);
              }
              else{
                sendToAll(getInstructionCommand(epochNum+1));
                state = State.FB_EPOCH_INSTRUCT;
              }
            }
            else{
              thisRatio = getNewRatio(trialNum);
              journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
                epochImageFiles[epochNum][trialNum%2+1]);
              sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
              sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
              stimOnset = System.currentTimeMillis();
              timer.schedule(new doNextLater(), RESPONSE_TIME);
            }
          }
          break;

        case FB_TRIAL_RESP:
          // Ignore second responses, in this case
          if(fromTimer){
            trialNum++;
            if(trialNum == TRIALS_PER_EPOCH){
              if(epochNum == NUM_EPOCHS - 1){
                sendToAll("I,Done--congratulations!");
                state = State.DONE;
                journal.close();
                if(logger != null)  logger.close();
                if(controlOut != null)  controlOut.close();
                System.exit(0);
              }
              else{
                sendToAll(getInstructionCommand(epochNum+1));
                state = State.FB_EPOCH_INSTRUCT;
              }
            }
            else{
              thisRatio = getNewRatio(trialNum);
              journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
                epochImageFiles[epochNum][trialNum%2+1]);
              sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
              sendToAll("T," + (epochType[epochNum] == FACES ? "Faces" : "Places"));
              stimOnset = System.currentTimeMillis();
              timer.schedule(new doNextLater(), RESPONSE_TIME);
              state = State.FB_TRIAL_NORESP;
            }
          }
          break;

        case DONE:
          break;

        default:
          System.out.println("Could not start server, sorry!");
          return;
      }
    }

    private class doNextLater extends TimerTask{

      public void run(){
        doNext(true);
      }

    }

    public String epochType(int epochNum){
      return epochType[epochNum] == PLACES ? "places" : "faces";
    }

    public String getInstructionCommand(int epochNum){
      String instruct;

      if(epochNum % 10 == 0){
        instruct = "I, You are on block " + epochNum + " of " + (TRAINING_EPOCHS + FEEDBACK_EPOCHS) + ". Take a longer break, if you want! Next block is ";
        if(epochType[epochNum] == FACES){
          instruct += "FACES";
        }
        else{
          instruct += "PLACES";
        }

        return instruct;
      }
      if(epochType[epochNum] == FACES){
        instruct = "I, FACES";
      }
      else{
        instruct = "I, PLACES";
      }
      return instruct;
    }

    public String getTrialImagesCommand(int epochNum, int trialNum, double ratio){
      return "S," + epochImageFiles[epochNum][trialNum*2] + "," + epochImageFiles[epochNum][trialNum*2+1] +
      "," + ratio;
    }

    double getNewRatio(int trialNum){
      if(realFeedback){
        controlOut.println("pred," + System.currentTimeMillis() + "," + trialNum + "," + (epochType[epochNum] == FACES ? "faces" : "places"));
        controlOut.flush();
        try{
          String data = controlIn.readLine();
          double thisRatio = Double.parseDouble(data);
          System.out.println("Got ratio " + thisRatio);
          return thisRatio;
        }
        catch(Exception e){
          System.out.println(e);
          return 0.5;
        }
      }

      return 0.5;
    }

  }



  public static void main(String[] args){
    if(args.length < 4){
      System.out.println("Usage: <ParticipantNum> <outputDir> <realFeedback (1,0)> <withEEG> <tcpPublish (1,0)> <trainEpochs> <feedbackEpochs>");
      return;
    }

    int participantNum = Integer.parseInt(args[0]);
    String outputDir = args[1];
    boolean feedback = (Integer.parseInt(args[2]) == 1) ? true : false;
    boolean withEEG = (Integer.parseInt(args[3]) == 1) ? true : false;
    boolean tcpPublish = (Integer.parseInt(args[3]) == 1) ? true : false;
    int trainEpochs = (Integer.parseInt(args[5]));
    int feedbackEpochs = (Integer.parseInt(args[6]));


    try{
      AttentionExperiment thisExperiment =
        new AttentionExperiment(participantNum, outputDir, feedback, withEEG, tcpPublish, trainEpochs, feedbackEpochs);
      thisExperiment.start();
    }
    catch(Exception e){
      System.out.println("Couldn't start experiment " + e);
      e.printStackTrace();
      return;
    }
  }

}
