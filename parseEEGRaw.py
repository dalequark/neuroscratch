# coding: utf-8
from pylab import *
import numpy as np
import scipy.interpolate as interpolate
import matplotlib.image as mpimg
from IPython import embed



markers = [(86,56),(40,65), (70,75), (53, 100), (25,125),
           (29,165), (80, 215), (135,215), (193,165),
           (200,125), (170,100), (150,75),(185,65), (135, 56)]
scalpFile = '/Users/Dale/Dropbox/code/neuromancer/scalp.png'
channels = ['AF3', 'F7', 'F3', 'FC5', 'T7', 'P7', 'O1', 'O2', 'P8', 'T8', 'FC6',
       'F4', 'F8', 'AF4']

def parseToArray(fileName):
  f = open(fileName)
  eegData = []
  for line in f:
    if line != '\n':
        eegData.append( [float(x) for x in line.split(',')]  )
  f.close()
  return eegData

def parseCedToArray(fileName):
     f = open(fileName)
     f.readline()
     return [chan.split()[1] for chan in f]

def plotScalpContour(component, axis):
    imp = mpimg.imread(scalpFile)
    axis.imshow(imp)
    X = [x[0] for x in markers]
    Y = [y[1] for y in markers]
    Z = [float(x)/component.sum() for x in component]
    f = interpolate.interp2d(X,Y,Z)
    X = np.linspace(0,200)
    Y = X
    Z = f(X,Y)
    #axis.pcolor(X,Y,Z)
    axis.contour(X, Y, Z)

class Journal:
    def __init__(self, journal_file):

        import itertools

        epochOnset = []
        epochOffset = []
        epochCorrect = []
        epochType = []
        epochLure = []

        # Load journal data
        f = open(journal_file)
        ar = [line for line in f]
        epoch_spacing = 4
        trial_types = []
        startidx = 5
        while startidx < len(ar):
            thisepoch = list(itertools.takewhile(lambda x: './' in x, [x for x in ar[startidx:]]))
            trial_types.append(thisepoch)
            startidx += len(thisepoch) + epoch_spacing - 1
            if "endheader" in ar[startidx]:
                break


        FEMALE = True
        MALE = False
        INDOORS = True
        OUTDOORS = False


        ar = ar[ar.index("-endheader-\n")+1:]
        stimOnsetIdx = len("StimOnset:")
        stimOffsetIdx = len("StimOffset:")
        correctIdx = len("Correct:")
        epochNum = -1
        f.close()

        for line in ar:
            if "Type" in line:
                epochType.append(line.split()[-1])
                epochNum += 1
                epochOnset.append([])
                epochOffset.append([])
                epochCorrect.append([])
                epochLure.append([])
            else:
                line = line.split(',')
                epochOnset[epochNum].append(long(line[2][stimOnsetIdx:]))
                epochOffset[epochNum].append(long(line[3][stimOffsetIdx:]))
                epochCorrect[epochNum].append((True if "true" in line[-1] else False))
                thisepoch = trial_types[epochNum]





        self.epochOnset = epochOnset
        self.epochOffset = epochOffset
        self.epochCorrect = epochCorrect
        self.epochType = epochType
        self.numEpochs = len(epochType)


        for epochNum, epochType in enumerate(epochType):
            thisepoch = trial_types[epochNum]

            if "faces" in epochType:
                # get epochs of male and female faces in face trial
                x = filter(lambda x: x != None, [True if ("/f" in y) else False if ("/m" in y) else None for y in thisepoch])

                if(x.count(True) > x.count(False)):
                    x = [(not y) for y in x]
                 # true if this trial is a lure
                epochLure[epochNum].append(x)

            elif "places" in epochType:
                x = filter(lambda x: x != None, [True if ("/in" in y) else False if ("/out" in y) else None for y in thisepoch])

                if(x.count(True) > x.count(False)):
                    x = [(not y) for y in x]
                 # true if this trial is a lure
                epochLure[epochNum].append(x)

        self.epochLure = epochLure


class EEGData:
    def __init__(self, journal, eegdata):
        eegdata = sorted(eegdata, key=lambda point: long((point[-1])))
        self.epochs = []
        self.timestamps = []
        offset = 0
        for epochNum in range(0, journal.numEpochs):
            try:
                thisEpoch = []
                thisTimestamps = []
                epochStart = journal.epochOnset[epochNum][0]
                epochEnd = journal.epochOffset[epochNum][-1]

                if offset >= len(eegdata):
                    return
                while long(eegdata[offset][-1]) < epochStart:
                    if offset+1 >= len(eegdata):
                        return
                    offset += 1

                thisTime = long(eegdata[offset][-1])
                while (thisTime >= epochStart) and (thisTime <= epochEnd):
                    if offset+1 >= len(eegdata):
                        return
                    thisEpoch.append(eegdata[offset][3:17])
                    thisTimestamps.append(eegdata[offset][-1])
                    offset += 1
                    thisTime = long(eegdata[offset][-1])

                self.epochs.append(thisEpoch)
                self.timestamps.append(thisTimestamps)
            except:
                return
