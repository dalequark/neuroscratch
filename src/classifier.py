import sys
dataset = []
def buildModel(inputfile, logfile):
    datafile = open(inputfile)
    logfile = open(logfile)
    for line in f:
        dataset.append(line.strip())

def classify(data):
    if data in dataset:
        print "True"
    else:
        print "False"
        dataset.append(data)


command = input()
