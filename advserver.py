#!/usr/bin/env python

import socket
import thread
import random
import time
from IPython import embed
import threading

host = '127.0.0.1'
port = 50000
backlog = 5
client = None
size = 1024
eegDataQueue = []
killThread = False
recordData = False

def data_handler(client, addr):
    print 'Got data line'
    while 1:
        data = client.recv(size)
        if recordData:
            if not data: break
            print 'Got data: ', data
            if 'close' in data:
                print "Closing data client"
                print client.close()
                return
            eegDataQueue.append(data)

def control_handler(client, addr):
    print 'Got control line'
    client.send('ok\n')
    while 1:
        data = client.recv(size)
        if not data: break

        if 'pred' in data:
            timestamp = data.split(',')[1]
            pred = str(get_prediction(timestamp))
            print "Sending prediction ", pred
            client.send(pred + '\n')

        elif 'model' in data:
            print "Making model"
            make_model()
            client.send('ok\n')
            recordData = True

        if 'close' in data:
            print "Closing data client"
            print client.close()
            return
        # return a prediction
        elif 'p' in data:
            client.send(get_prediction())

def get_prediction(timestamp):
    print "timestamp ", timestamp
    return 0.9

def make_model():
    time.sleep(10)
    return

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
