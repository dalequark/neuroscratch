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


trialLen = 1000
trialsAhead = 1
binNum = 1


host = '127.0.0.1'
port = 50000
backlog = 5
client = None
size = 1024
eegDataQueue = []
maxQueueSize = 128 * 1000
killThread = False
recordData = False
model = None
lastTimestamp = None
lastPrediction = 0.5
localQueue = ''

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
    print 'Got control line'
    while 1:
        data = client.recv(size)
        if not data: break

        if 'pred' in data:
            datarray = data.split(',')
            timestamp = datarray[1]
            trialNum = datarray[2]
            print "trial num", trialNum
            if int(trialNum) >= trialsAhead:
                pred = str(get_prediction(timestamp))
            else:
                global lastPrediction
                lastPrediction = 0.5
                pred = str(0.5)

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


def get_prediction(timestamp):
    timestamp = long(timestamp)
    # timestamp marks start of new trial; want data earlier
    lastTimestamp = timestamp
    npdata = np.array(eegDataQueue)
    timestamps = np.array(npdata[:,-1], dtype=int64)
    # get data three trials ahead

    idxshigh = np.where( timestamps > (timestamp - (trialsAhead * trialLen - binNum * 100)))
    idxslow = np.where(timestamps < (timestamp - (trialsAhead * trialLen - binNum * 100 - 100)))
    idxs = np.intersect1d(idxshigh[0], idxslow[0])
    bin_data = npdata[idxs, :]

    global lastPrediction
    if bin_data.shape[0] == 0:
        return lastPrediction
    else:
        bin_data = np.array(bin_data, dtype=float)[:,3:17].mean(axis=0)
        lastPrediction = model.predict_proba(bin_data)[0][0]/6.0 + lastPrediction*5.0/6.0
    return lastPrediction

def make_model(logFile, eegFile):
    global model
    model = logistic_regression(logFile, eegFile)


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


    for idx, (epoch, label) in enumerate(zip(data.epochs, journal.epochType)):
        trials = np.array_split(epoch, 50)

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
