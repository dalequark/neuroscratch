#!/usr/bin/env python

import socket
import thread
import random
import time
from IPython import embed
import threading
from sklearn import linear_model
from parseEEGRaw import *
from scipy import stats


trainEpochs = 2
trialLen = 1000
trialsAhead = 3
binNum = 1


host = '127.0.0.1'
port = 50000
backlog = 5
client = None
size = 1024
eegDataQueue = []
timestampRequests = []
predictions = []
maxQueueSize = 128 * 1000
killThread = False
recordData = False
model = None
lastTimestamp = None
lastPrediction = 0.5
localQueue = ''

FACES = 0
PLACES = 1

def data_handler(client, addr):
    print 'Got data line'
    while 1:
        data = client.recv(500)
        if recordData:

            global localQueue
            global eegDataQueue
            idx = -1
            newlines = [i for i,x in enumerate(data) if x == '\n']
            if len(newlines) > 0:

                localQueue = ''.join([localQueue, data[:newlines[0]]])
                dataPoint = localQueue.split(',')
                if len(dataPoint) == 23:
                    eegDataQueue.append(dataPoint)
                for i in range(0, len(newlines)-1):
                    dataPoint = data[newlines[i]:newlines[i+1]].split(',')
                    if len(dataPoint) == 23:
                        eegDataQueue.append(dataPoint)
                    else:
                        print "Len of data ponit was ", len(dataPoint)

                localQueue = data[newlines[-1]+1:]
                #if len(eegDataQueue) > maxQueueSize:
                #    eegDataQueue = eegDataQueue[len(eegDataQueue) - maxQueueSize:]
            else:
                localQueue.join(data)

def control_handler(client, addr):
    global timestampRequests
    print 'Got control line'
    while 1:
        data = client.recv(size)
        if not data: break

        if 'pred' in data:
            datarray = data.split(',')
            timestamp = datarray[1]
            trialNum = int(datarray[2])
            if trialNum == 0:
                global trialType
                trialType = datarray[3]
                if "faces" in trialType:
                    trialType = FACES
                else:
                    trialType = PLACES
                print "Trial type is ", trialType
                timestampRequests = [timestamp]
                eegDataQueue = []
                predictions = []
            else:
                timestampRequests.append(timestamp)

            pred = str(get_prediction(trialNum))

            print "Sending prediction ", pred
            client.send(pred + '\n')

        elif 'model' in data:
            data = data.split(',')
            logFile =  data[1]
            eegFile = data[2].strip()
            print "Making model from %s and %s" % (logFile, eegFile)
            make_model(logFile, eegFile)
            client.send('ok\n')
            global recordData
            recordData = True

        elif 'close' in data:
            print "Closing data client"
            print client.close()
            return

        else:
            print "got unrecognized command ", data


def get_prediction(trialNum):
    global lastPrediction
    global predictions
    global trialType
    if trialNum < trialsAhead:
            lastPrediction = 0.5
            predictions.append(0.5)
            return 0.5

    # Look at index three trials earlier than trialNum
    trialAheadStart = long(timestampRequests[trialNum - trialsAhead])

    npdata = np.array(eegDataQueue)
    timestamps = np.array(npdata[:,-1], dtype=int64)
    # get data three trials ahead
    idxshigh = np.where( timestamps >= (trialAheadStart + binNum*100) )
    idxslow = np.where( timestamps <= (trialAheadStart + binNum*100 + 100) )
    idxs = np.intersect1d(idxshigh[0], idxslow[0])
    bin_data = npdata[idxs, :]


    if bin_data.shape[0] == 0:
        predictions.append(lastPrediction)
        return lastPrediction
    else:
        bin_data = stats.zscore(np.array(bin_data, dtype=float)[:,3:17].mean(axis=0))
        # this is always face probability
        probs =  model.predict_proba(bin_data)[0]
        face_prob = probs[0]
        place_prob = probs[1]
        if trialType == PLACES:
            diff_prob = sigmoid(place_prob - face_prob,0.9,3,0.2, 0.15)
        else:
            diff_prob = sigmoid(face_prob - place_prob,0.9,3,0.2, 0.15)
            # because diff_prob is the opacity of the places, and in this case
            # the given opacity value is for faces:
            diff_prob = 1-diff_prob

        predictions.append(diff_prob)
        if(len(predictions) == 7):
            predictions.pop(0)
        lastPrediction = float(sum(predictions))/len(predictions)
        print "First timestamp: %d, Last timestamp: %d, Num points: %d, pred for point: %f" % (timestamps[idxs[0]], timestamps[idxs[-1]], len(bin_data), diff_prob)
    return lastPrediction

def make_model(logFile, eegFile):
    global model
    model = logistic_regression(logFile, eegFile)


def sigmoid(x, steepness, gain, x_shift, y_shift):
        return float(steepness)/(1 + math.exp(-gain*(x - x_shift))) + y_shift


def logistic_regression(logFile, eegFile):
    journal = Journal(logFile)
    eegdata = parseToArray(eegFile)
    data = EEGData(journal, eegdata)
    millis_len = 100
    points_per_bucket = int(ceil(128 * 0.001 * millis_len))
    buckets_per_trial = int(ceil(128 / points_per_bucket))
    before_avg_interval_millis = 80
    points_before_trial = 128 * 0.001 * before_avg_interval_millis
    g_accuracies = []
    back_stretch = 5
    train_feat = []
    train_lab = []


    for idx, (epoch, label) in enumerate(zip(data.epochs[:trainEpochs], journal.epochType[:trainEpochs])):
        trials = np.array_split(epoch, 5)

        for trialNum, trial in enumerate(trials):
            trial_dat = np.array(trial)[points_per_bucket:2*points_per_bucket, :]
            if trialNum > 0:
                trial_before = np.array(trials[trialNum - 1][-1 * points_before_trial:, :])
                trial_before = np.mean(trial_before, axis=0)
                trial_dat -= trial_before
            trial_dat = trial_dat.mean(axis=0)
            trial_dat = stats.zscore(trial_dat)
            train_feat.append(trial_dat)
            train_lab.append(label)

    # learn model
    cnf = linear_model.LogisticRegression()
    cnf.fit(train_feat, train_lab)
    print cnf.coef_
    return cnf


if __name__ == '__main__':
    addr = (host, port)
    s = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    s.bind(addr)
    s.listen(backlog)
    print "Running on "
    print addr
    dataBuffer = ""
    while 1:
        print "waiting for connection"
        client, addr = s.accept()
        print '...connected from: ', addr
        data = client.recv(4) #'data\n', 'cont\n'
        if 'data' in data:
            thread.start_new_thread(data_handler, (client, addr))
        elif 'cont' in data:
            thread.start_new_thread(control_handler, (client, addr))
        else:
            print "got unknown client type "
            print str(data)
