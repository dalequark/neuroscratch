#!/usr/bin/env python

import socket
import thread
import random
import time

host = '127.0.0.1'
port = 50000
backlog = 5
client = None
size = 468
eegDataQueue = []
killThread = False

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
        if not client:
            print "waiting for connection"
            client, addr = s.accept()
        else:
            data = client.recv(size)
            if data:
                print "Got data", data
