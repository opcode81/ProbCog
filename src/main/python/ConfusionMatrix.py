
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
		if self.instances > 0:
			print "correct: %.2f%% (%d/%d)" % ((float)(self.correct)/(float)(self.instances)*100.0, self.correct, self.instances)
		else:
			print "No instances counted"
	
	def getMetrics(self,cf):
		
		classes = []
		for classification in self.matrix:
			for truth in self.matrix.get(classification,{}):
				try:
					classes.index(truth)
				except ValueError:
					classes.append(truth)
		
		classes = sorted(classes)
	
		tp = self.matrix.get(cf,{}).get(cf,0)
		#print cf+": TP "+str(tp)
		
		fp = 0
		for c in classes:
			if c != cf:
				fp += self.matrix.get(cf,{}).get(c,0)
		
		#print cf+": FP "+str(fp) 
		
		fn = 0
		for c in classes:
			if c != cf:
				fn += self.matrix.get(c,{}).get(cf,0)
		#print cf+": FN "+str(fn)
		
		tn = 0
		for c in classes:
			if c != cf:
				for c2 in classes:
					if c2 != cf:
						tn += self.matrix.get(c,{}).get(c2,0)
		#print cf+": TN "+str(tn)
		
		#print cf+" - TP:"+str(tp)+", FP:"+str(fp)+", TN:"+str(tn)+", FN:"+str(fn)
		
		acc = 0.0
		if tp+tn+fp+fn > 0:
			acc = (float)(tp+tn)/(float)(tp+tn+fp+fn)
		
		pre = 0.0
		if tp+fp > 0:
			pre = (float)(tp)/(float)(tp+fp)
		
		rec = 0.0
		if tp+fn > 0:
			rec = (float)(tp)/(float)(tp+fn)
		
		f1 = 0.0
		if pre+rec > 0:
			f1  = (2.0*pre*rec)/(pre+rec)
			
		#print cf+" - Acc:"+str(acc)+", Pre:"+str(pre)+", Rec:"+str(rec)+", F1:"+str(f1)
		
		return (str(acc),str(pre),str(rec),str(f1))

	def printLatexTable(self,word,folds,mln):
		classes = []
		for classification in self.matrix:
			for truth in self.matrix.get(classification,{}):
				try:
					classes.index(truth)
				except ValueError:
					classes.append(truth)
		
		grid = "|l|"
		for cl in sorted(classes):
			grid += "l|"
		
		print "\\begin{table}[h!]"
		print "\\footnotesize"
		print "\\begin{tabular}{"+grid+"}"
		
		headerRow = "Truth/Class"
		for cl in sorted(classes):
			headerRow += " & " + cl 
		
		print "\\hline"
		print headerRow+"\\\\ \\hline"
		
		#for each class create row
		for cl in sorted(classes):
			values = []
			#for each row fill colum
			for cl2 in sorted(classes):
				if cl == cl2:
					values.append("\\textbf{"+str(self.matrix.get(cl2,{}).get(cl,0))+"}")
				else:
					values.append(str(self.matrix.get(cl2,{}).get(cl,0)))
			print cl + " & "+" & ".join(values) +"\\\\ \\hline"
		print "\\hline"
		
		preline = "Prescision "
		recline = "Recall "
		f1line = "F1 "
		for cl in sorted(classes):
			acc,pre,rec,f1 = self.getMetrics(cl)
			preline += " & "+pre
			recline += " & "+rec
			f1line  += " & "+f1
			
		print preline + "\\\\ \\hline"
		print recline + "\\\\ \\hline"
		print f1line + "\\\\ \\hline"
		print "\\end{tabular}"
		print "\\caption[Confusion Matrix: Word \\textbf{"+word+"} (folds: "+folds+", mln: "+mln+")]{Confusion Matrix of the Word \\textbf{"+word+"} (folds: "+folds+", mln: "+mln+")}"
		print "\\label{cf:"+word+"_"+folds+"_"+mln+"}"
		print "\\end{table}"

	def printPrecisions(self):
		
		classes = []
		for classification in self.matrix:
			for truth in self.matrix.get(classification,{}):
				try:
					classes.index(truth)
				except ValueError:
					classes.append(truth)
		
		classes = sorted(classes)
		
		for cf in classes:
			acc,pre,rec,f1 = self.getMetrics(cf)
			
			print cf+" - Acc:"+str(acc)+", Pre:"+str(pre)+", Rec:"+str(rec)+", F1:"+str(f1)
			print ""
			
		print ""

	def printTable(self):
		classes = []
		for classification in self.matrix:
			for truth in self.matrix.get(classification,{}):
				try:
					classes.index(truth)
				except ValueError:
					classes.append(truth)
		
		headerRow = "T/C\t\t"
		for cl in sorted(classes):
			headerRow +=  cl +"\t\t"
			
		print headerRow
		
		#for each class create row
		for cl in sorted(classes):
			values = []
			#for each row fill colum
			for cl2 in sorted(classes):
				values.append(str(self.matrix.get(cl2,{}).get(cl,0)))
			print cl + "\t\t"+"\t\t\t".join(values)
		print ""

	def getPercentageCorrect(self):
		if self.instances > 0:
			return (float)(self.correct)/(float)(self.instances)*100.0
		else:
			return 0.0
		
if __name__ == '__main__':
	cm = ConfusionMatrix()
	
	cm.addCase("A","A")
	cm.addCase("A","A")
	cm.addCase("A","A")
	cm.addCase("A","A")
	cm.addCase("A","A")
	cm.addCase("A","B")
	cm.addCase("A","B")
	cm.addCase("A","C")
	cm.addCase("B","A")
	cm.addCase("B","A")
	cm.addCase("B","A")
	cm.addCase("B","C")
	cm.addCase("B","C")
	cm.addCase("B","B")
	#cm.addCase("C","A")
	#cm.addCase("C","B")
	#cm.addCase("C","C")
	
	cm.printTable()
	cm.printPrecisions()