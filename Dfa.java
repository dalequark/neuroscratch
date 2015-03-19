private class Dfa{

  private enum State {
    START, START_INSTRUCT, EPOCH_INSTRUCT, TRIAL_NORESP, TRIAL_RESP, TRAIN,
    FB_EPOCH_INSTRUCT, FB_TRIAL_NORESP, FB_TRIAL_RESP, DONE
  }
  private int epochNum;
  private int trialNum;
  private int thisRatio;
  private long stimOnset;
  private boolean shouldClick;
  private State state = START;

  /* Begin the experiment by showing the first instruction */
  public void start(){
    System.out.println("Starting experiment dfa");
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
        state = START_INSTRUCT;
        break;

      case START_INSTRUCT:
        epochNum = -1;
        sendToAll(getInstructionCommand(epochNum+1));
        thisRatio = 0.5;
        if(withEEG){
          System.out.println("Beginning to log EEG data!");
          logger.start();
        }
        state = EPOCH_INSTRUCT;
        break;

      case EPOCH_INSTRUCT:
        epochNum++;
        trialNum = 0;
        journal.addEpoch(epochType(epochNum));
        journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
          epochImageFiles[epochNum][trialNum%2+1]);
        sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
        timer.schedule(new doNextLater(), RESPONSE_TIME);
        state = TRIAL_NORESP;
        break;

      case TRIAL_NORESP:
        shouldClick = answers[epochNum][trialNum];
        if(!fromTimer){
          state = TRIAL_RESP;
          responseTime = System.currentTimeMillis();
          journal.endTrial(stimOnset, timeOfResponse, responseTime, shouldClick);
        }
        else{
          trialNum++;

          if(trialNum == TRIALS_PER_EPOCH){
            if(epochNum == TRAINING_EPOCHS - 1){
              System.out.println("Training model");
              sendToAll("I,One moment please!");
              state = TRAIN;
              // start training
            }
            sendToAll(getInstructionCommand(epochNum+1));
            state = EPOCH_INSTRUCT;
          }
          else{
            journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
              epochImageFiles[epochNum][trialNum%2+1]);
            sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
            timer.schedule(new doNextLater(), RESPONSE_TIME);
          }
        }
        break;

      case TRAIN_RESP:
        // Ignore second responses, in this case
        if(fromTimer){
          trialNum++;
          if(trialNum == TRIALS_PER_EPOCH){
            if(epochNum == TRAINING_EPOCHS - 1){
              System.out.println("Training model");
              sendToAll("I,One moment please!");
              state = TRAIN;
              // start training
            }
            sendToAll(getInstructionCommand(epochNum+1));
            state = EPOCH_INSTRUCT;
          }
          else{
            journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
              epochImageFiles[epochNum][trialNum%2+1]);
            sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
            timer.schedule(new doNextLater(), RESPONSE_TIME);
            state = TRIAL_NO_RESP;
          }
        }
        break;

      case TRAIN:
        // figure out how we get here
        sendToAll(getInstructionCommand(epochNum+1));
        state = FB_EPOCH_INSTRUCT;
        break;

      case FB_EPOCH_INSTRUCT:
        epochNum++;
        trialNum = 0;
        journal.addEpoch(epochType(epochNum));
        thisRatio = //getRatio
        journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
          epochImageFiles[epochNum][trialNum%2+1]);
        sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
        timer.schedule(new doNextLater(), RESPONSE_TIME);
        state = FB_TRIAL_NORESP;
        break;

      case FB_TRIAL_NORESP:
        shouldClick = answers[epochNum][trialNum];
        if(!fromTimer){
          state = FB_TRIAL_RESP;
          responseTime = System.currentTimeMillis();
          journal.endTrial(stimOnset, timeOfResponse, responseTime, shouldClick);
        }
        else{
          trialNum++;
          if(trialNum == TRIALS_PER_EPOCH){
            if(epochNum == NUM_EPOCHS - 1){
              sendToAll("I,Done--congratulations!");
              state = DONE;
              // start training
            }
            else{
              sendToAll(getInstructionCommand(epochNum+1));
              state = FB_EPOCH_INSTRUCT;
            }
          }
          else{
            thisRatio = //get new ratio
            journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
              epochImageFiles[epochNum][trialNum%2+1]);
            sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
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
              state = DONE;
            }
            else{
              sendToAll(getInstructionCommand(epochNum+1));
              state = FB_EPOCH_INSTRUCT;
            }
          }
          else{
            thisRatio = //get ratio
            journal.addTrial(thisRatio, epochImageFiles[epochNum][trialNum%2],
              epochImageFiles[epochNum][trialNum%2+1]);
            sendToAll(getTrialImagesCommand(epochNum, trialNum, thisRatio));
            timer.schedule(new doNextLater(), RESPONSE_TIME);
            state = TRIAL_NO_RESP;
          }
        }
        break;

      case DONE:
    }
  }

  private class doNextLater extends TimerTask{

    public void run(){
      doNext(true);
    }

  }
