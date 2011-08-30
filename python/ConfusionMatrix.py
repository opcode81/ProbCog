
class ConfusionMatrix(object):

    def __init__(self):
        self.matrix = {}
        self.instances = 0
        self.correct = 0

    def addCase(self, classification, groundTruth):
        dict = self.matrix.get(classification, {});
        self.matrix[classification] = dict
        
        cnt = dict.get(groundTruth, 0);
        cnt += 1
        dict[groundTruth] = cnt
        self.instances += 1
        
        if classification == groundTruth:
            self.correct += 1;
    
    def printMatrix(self):
        for classification, e in sorted(self.matrix.iteritems()):
            print "%s: %s" % (str(classification), map(lambda item: str(item[0]) + "=" + str(item[1]), sorted(e.iteritems())))

        print "correct: %.2f%% (%d/%d)" % ((float)(self.correct)/(float)(self.instances)*100.0, self.correct, self.instances)

    def getPercentageCorrect(self):
        return (float)(self.correct)/(float)(self.instances)*100.0