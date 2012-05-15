/*
 * Created on Nov 7, 2007
 */
package edu.tum.cs.bayesnets.learning;

import java.util.*;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import de.tum.in.fipm.base.util.weka.NamedClassifier;

import weka.classifiers.trees.J48;
import weka.classifiers.trees.REPTree;
import weka.core.Attribute;
import weka.core.Instance;
import weka.core.Instances;
import edu.ksu.cis.bnj.ver3.core.*;
import edu.ksu.cis.bnj.ver3.core.values.Field;
import edu.ksu.cis.bnj.ver3.core.values.ValueDouble;
import edu.tum.cs.bayesnets.core.BeliefNetworkEx;
import edu.tum.cs.bayesnets.core.DiscretizationFilter;
import edu.tum.cs.bayesnets.core.Discretized;
import edu.tum.cs.util.math.MathUtils;

/**
 * An instance of <code>BeliefNetworkFromClassifiers</code> is able to create the structure
 * of a BeliefNetwork from the dependencies and splits of decision trees ({@link weka.classifiers.trees.J48}).
 * @author kirchlec
 */
public class BeliefNetworkFromClassifiers {
	protected static Logger logger = Logger.getLogger(BeliefNetworkFromClassifiers.class); 
	static {
		logger.setLevel(Level.WARN);
	}
	NamedClassifier[] classifiers;
	HashMap<String, Attribute> attributes = new HashMap<String, Attribute>();
	HashMap<String, Set<String>> connections = new HashMap<String, Set<String>>();
	HashMap<String, HashMap<String, double[]>> splitPoints = new HashMap<String, HashMap<String,double[]>>();
	
	/**
	 * Creates an instance of <code>BeliefNetworkFromClassifiers</code> for the given classifiers.
	 * @param classifiers	the classifiers to use for structure of the belief network.
	 */
	public BeliefNetworkFromClassifiers(NamedClassifier[] classifiers) {
		this.classifiers = classifiers;
		initMaps();
	}
	
	protected void insertConnection(String from, String to) {
		Set<String> tos = connections.get(from);
		if (tos == null) {
			tos = new HashSet<String>();
			connections.put(from, tos);
		}
		tos.add(to);
	}
	
	/**
	 * Initialize the attributes, connections and splitPoints maps.
	 */
	@SuppressWarnings("unchecked")
	protected void initMaps() {
		for (int i=0; i<classifiers.length; i++) {
			Instances insts = classifiers[i].getTrainingData();
			Enumeration<Attribute> en = insts.enumerateAttributes();
			while (en.hasMoreElements()) {
				Attribute att = en.nextElement();
				attributes.put(att.name(), att);
			}
			attributes.put(insts.classAttribute().name(), insts.classAttribute());
			
			if (classifiers[i].getClassifier() instanceof J48) {
				J48 classifier = (J48)classifiers[i].getClassifier();
				String classAttribute = insts.classAttribute().name(); 
				Set<Integer> usedAttributes = classifier.getUsedAttributes();
				for (int attribute: usedAttributes) {
					String attributeName = insts.attribute(attribute).name();
					insertConnection(attributeName, classAttribute);

					HashMap<String, double[]> splits = splitPoints.get(attributeName);
					if (splits == null) {
						splits = new HashMap<String, double[]>();
						splitPoints.put(attributeName, splits);
					}
					splits.put(classAttribute, classifier.getSplitPoints(attribute));
				}
			} else if (classifiers[i].getClassifier() instanceof REPTree) {
				REPTree classifier = (REPTree)classifiers[i].getClassifier();
				String classAttribute = insts.classAttribute().name(); 
				Set<Integer> usedAttributes = classifier.getUsedAttributes();
				for (int attribute: usedAttributes) {
					String attributeName = insts.attribute(attribute).name();
					insertConnection(attributeName, classAttribute);

					HashMap<String, double[]> splits = splitPoints.get(attributeName);
					if (splits == null) {
						splits = new HashMap<String, double[]>();
						splitPoints.put(attributeName, splits);
					}
					splits.put(classAttribute, classifier.getSplitPoints(attribute));
				}
			} else
				throw new IllegalArgumentException("Cannot handle classifier "+classifiers[i].getClassifier().getClass().getName());
		}
		
		collectSplitPoints(splitPoints);
		sanityCheckSplitPoints();
	}
	
	/**
	 * Check that the split points span at least 1% of the total split point range.
	 */
	public void sanityCheckSplitPoints() {
		for (String attribute: splitPoints.keySet()) {
			Map<Double, Double> eliminations = new HashMap<Double, Double>();
			double[] splits = splitPoints.get(attribute).get(attribute);
			if (splits.length == 0)
				continue;
			double minDistance = (splits[splits.length-1]-splits[0])/100.0;
			double lastSplit = splits[0];
			for (int i=1; i<splits.length; i++) {
				if (splits[i] - lastSplit < minDistance) {
					eliminations.put(splits[i], lastSplit);
				} else
					lastSplit = splits[i];
			}
			
			if (eliminations.isEmpty()) {
				continue;
			}
			eliminateSplitBuckets(attribute, eliminations);
		}
	}
	
	/**
	 * Eliminate all the split buckets via assigning the elimination split points with their counterpart.
	 * @param attribute the attribute the split points come from.
	 * @param eliminations the assignments that should be made.
	 */
	private void eliminateSplitBuckets(String attribute, Map<Double, Double> eliminations) {
		Map<String, double[]> splits = splitPoints.get(attribute);
		for (String toAttribute: splits.keySet()) {
			double[] toSplits = splits.get(toAttribute);
			double[] newTmpSplits = new double[toSplits.length];
			int newSplitIdx = 0;
			boolean changed = false;
			for (int i=0; i<toSplits.length; i++) {
				if (eliminations.containsKey(toSplits[i])) {
					changed = true;
					if (newSplitIdx != 0 && newTmpSplits[newSplitIdx-1] == eliminations.get(toSplits[i]))
						continue;
					else
						newTmpSplits[newSplitIdx++] = eliminations.get(toSplits[i]);
				} else {
					newTmpSplits[newSplitIdx++] = toSplits[i];
				}
			}
			if (changed) {
				 double[] newSplits = new double[newSplitIdx];
				 System.arraycopy(newTmpSplits, 0, newSplits, 0, newSplitIdx);
				 splits.put(toAttribute, newSplits);
			}
		}
	}
	
	/**
	 * Get the distribution over the buckets defined by the split points.
	 * @param instances the instances used for filling the buckets.
	 * @return the distribution over the buckets. I.e. a map from the attribute name to the bucket histogram.
	 */
	protected HashMap<String, int[]>  getBucketDistribution(Instances instances) {
		// Initialize the buckets
		HashMap<String, int[]> buckets = new HashMap<String, int[]>();
		for (String attribute: splitPoints.keySet()) {
			buckets.put(attribute, new int[splitPoints.get(attribute).get(attribute).length + 1]);
		}
		
		// get the instances' attribute map
		HashMap<String, Attribute> myAttributes = new HashMap<String, Attribute>();
		Enumeration<Attribute> en = instances.enumerateAttributes();
		while (en.hasMoreElements()) {
			Attribute att = en.nextElement();
			myAttributes.put(att.name(), att);
		}
		myAttributes.put(instances.classAttribute().name(), instances.classAttribute());
		
		// fill the buckets
		Enumeration<Instance> insts = instances.enumerateInstances();
		while (insts.hasMoreElements()) {
			Instance inst = insts.nextElement();
			for (String attribute: attributes.keySet()) {
				try {
					double[] splits = splitPoints.get(attribute).get(attribute);
					double value = inst.value(myAttributes.get(attribute));
					for (int i=0; i<splits.length; i++) {
						if (value<=splits[i]) {
							buckets.get(attribute)[i]++;
							break;
						}
					}
					buckets.get(attribute)[splits.length]++;
				} catch (RuntimeException e) {
//					e.printStackTrace();
					continue;
				}
			}
		}
		return buckets;
	}

	protected boolean incrementCounter(int[] counter, int[] sizes, int startPoint) {
		for (int i=startPoint; i<counter.length; i++) {
			counter[i]++;
			if (counter[i]>=sizes[i]) {
				counter[i]=0;
			} else
				return true;
		}
		return false;
	}
	
	protected Instance extractInstanceFromDomains(BeliefNetworkEx bnx, Instances insts, HashMap<String, Integer> values) {
		Instance result = new Instance(insts.numAttributes());
		result.setDataset(insts);
		for (int i=0; i<insts.numAttributes(); i++) {
			Attribute attr = insts.attribute(i);
			String attrName = attr.name();
			if (!values.containsKey(attr.name())) {
				boolean nameSet = false;
				for (String name: values.keySet()) {
					if (name.startsWith(attrName+"$")) {
						attrName = name;
						nameSet = true;
						break;
					}
				}
				if (!nameSet) {
					logger.debug("Cannot find argument "+attr.name()+" in values "+values);
					result.setMissing(attr);
					continue;
				}
			}
			if (attr.isNominal()) {
				result.setValue(attr, bnx.getNode(attrName).getDomain().getName(values.get(attrName)));
			} else {
				result.setValue(attr, ((Discretized)bnx.getNode(attrName).getDomain()).getExampleValue(values.get(attrName)));
			}
		}
		return result;
	}
	
	protected double[] getDistributionFromClassifiers(BeliefNetworkEx bnx, String nodeName, HashMap<String, Integer> values) throws Exception {
		NamedClassifier nc = null;
		for (int i=0; i<classifiers.length; i++) {
			if (classifiers[i].getTrainingData().classAttribute().name().equals(nodeName)) {
				nc = classifiers[i];
				break;
			}
		}
		if (nc == null) {
			throw new IllegalArgumentException("Cannot find nodeName "+nodeName+" as class attribute of the classifiers!");
		}

		Instances insts = new Instances(nc.getTrainingData());
		Instance inst = extractInstanceFromDomains(bnx, insts, values);
		if (nc.getTrainingData().classAttribute().isNumeric())
			if (nc.getClassifier() instanceof REPTree) {
				return new double[] { nc.getClassifier().distributionForInstance(inst)[0], 
					((REPTree)nc.getClassifier()).varianceForInstance(inst) };
			} else {
				throw new RuntimeException("Classifier for continuous variable not REPTree -> cannot extract variance!");
			}
		return nc.getClassifier().distributionForInstance(inst);
	}

	protected static int initds(double os[], int nos, double eta) {
		double err, ans;
		int i;

		if (nos < 1) {
			throw new IllegalArgumentException("\n\nERROR: The number of coefficients for initds"
					+ " was less than 1.\n\n");
		}

		i = nos;
		err = 0.0;

		for (int j = 1; j <= nos; j++) {
			i = nos + 1 - j;
			err += Math.abs(os[i]);
			if (err > eta)
				break;
		}

		if (i == nos) {
			logger.warn("The Chebyshev series is too short for the specified accuracy.\n\n");
		}

		return i;
	}
	
	static double sqrt2 = Math.sqrt(2);
	protected double gaussIntegral(double mean, double variance, double intervalStart, double intervalEnd) {
		double sigma = Math.sqrt(variance);
		double erfStart = MathUtils.erf((intervalStart-mean)/sigma/sqrt2);
		double erfEnd = MathUtils.erf((intervalEnd-mean)/sigma/sqrt2);
		return 0.5*(erfEnd-erfStart);
	}
	
	protected double[] getGaussianDistribution(Discretized domain, double mean, double variance) {
		double[] bins = new double[domain.getOrder()];
		for (int i=0; i<domain.getOrder(); i++) {
			double[] intervals = domain.getIntervals(i);
			double binValue = 0;
			for (int j=0; j<intervals.length/2; j++) {
				bins[i] += gaussIntegral(mean, variance, intervals[2*j], intervals[2*j+1]);
			}
		}
		return bins;
	}
	
	/**
	 * Check all CPTs of the net for undefined columns and set the distribution from the classifiers.
	 * @param bnx the belief network to change.
	 * @throws Exception 
	 */
	public void completeCPTs(BeliefNetworkEx bnx) throws Exception {
		BeliefNode[] nodes = bnx.bn.getNodes();
		for (int i=0; i<nodes.length; i++) {
			CPF cpf = nodes[i].getCPF();
			BeliefNode[] domProd = cpf.getDomainProduct();
			if (domProd.length <= 1)	// We do not change prior distributions
				continue;
			assert domProd[0]==nodes[i];
			int[] counter = new int[domProd.length];
			int[] sizes = new int[domProd.length];
			Arrays.fill(counter, 0);
			for (int j=0; j<domProd.length; j++) {
				sizes[j] = domProd[j].getDomain().getOrder();
			}
			
			int address1 = cpf.addr2realaddr(counter);
			counter[0]=1;
			int address2 = cpf.addr2realaddr(counter);
			counter[0]=0;
			int diff = address2-address1;
			int numClasses = domProd[0].getDomain().getOrder();
			while (true) {
				int address = cpf.addr2realaddr(counter);
				boolean isZero = true;
				for (int domainIdx=0; domainIdx<numClasses; domainIdx++) {
					if (!Field.isZero(cpf.get(address+diff*domainIdx))) {
						isZero = false;
						break;
					}
				}
				if (isZero) {
					HashMap<String, Integer> values = new HashMap<String, Integer>();
					for (int j=1; j<domProd.length; j++) {
						values.put(domProd[j].getName(), counter[j]);
					}
					double[] distribution = getDistributionFromClassifiers(bnx, nodes[i].getName(), values);
					if (numClasses != distribution.length) {
						if (nodes[i].getDomain() instanceof Discretized && distribution.length == 2) {
							distribution = getGaussianDistribution((Discretized)nodes[i].getDomain(), distribution[0], distribution[1]);
						} else
							throw new IllegalArgumentException("Domain size and number of attribute values must match ("+numClasses+"!="+distribution.length+") for class "+nodes[i].getName()+"!");
					}
					for (int domainIdx = 0; domainIdx<numClasses; domainIdx++) {
						cpf.put(address+diff*domainIdx, new ValueDouble(distribution[domainIdx]));
					}
				}
				if (!incrementCounter(counter, sizes, 1))
					break;
			}
		}
	}
	
	/**
	 * Ensure that in all buckets are some values of the instances.
	 * @param instances the instances to check the buckets for.
	 */
	public void checkSplitPoints(Instances instances) {
		// Fill the buckets
		HashMap<String, int[]> buckets = getBucketDistribution(instances);
		
		for (String attribute: buckets.keySet()) {
			Map<Double, Double> eliminations = new TreeMap<Double, Double>();
			double[] oldSplits = splitPoints.get(attribute).get(attribute);
			int[] attrBuckets = buckets.get(attribute);
			
			int firstSplit = -1;
			int lastSplit = oldSplits.length;
			do {
				// Find next splits...
				int oldFirstSplit = ++firstSplit;
				while (attrBuckets[firstSplit] == 0 && firstSplit < lastSplit)
					firstSplit++;
				int oldLastSplit = --lastSplit;
				while (attrBuckets[lastSplit+1] == 0 && lastSplit >= firstSplit)
					lastSplit--;

				// ... and eliminate the buckets
				if (lastSplit < firstSplit)
					break;
				for (int i=oldFirstSplit; i<firstSplit; i++) {
					eliminations.put(oldSplits[i], oldSplits[firstSplit]);
				}
				for (int i=oldLastSplit; i>lastSplit; i--) {
					eliminations.put(oldSplits[i], oldSplits[lastSplit]);
				}
			} while (true);
			
			eliminateSplitBuckets(attribute, eliminations);
		}
	}
	
	/**
	 * Get a belief network with the structure learned from the classifiers.
	 * @return	a belief network with the structure learned from the classifiers.
	 * @throws Exception if a connection of nodes fails.
	 */
	public BeliefNetworkEx getBeliefNetworkStructure() throws Exception {
		BeliefNetworkEx result = new BeliefNetworkEx();
		learnStructure(result);
		return result;
	}
	
	/**
	 * Learn the structure of the belief network from the classifiers.
	 * @param bnx	the belief network to be learned (has to be empty).
	 * @throws Exception	if a connection of nodes fails.
	 */
	protected void learnStructure(BeliefNetworkEx bnx) throws Exception {
		for (String attributeName: attributes.keySet()) {
			Attribute attribute = attributes.get(attributeName);
			Domain domain;
			if (attribute.isNominal()) {
				domain = getDomainForNominalAttribute(attribute);
			} else {
				if (splitPoints.get(attribute.name()) == null) {
					System.err.println("No split points for attribute "+attribute+" found!");
					domain = getSplitDomain(null);
				} else {
					domain = getSplitDomain(splitPoints.get(attribute.name()).get(attribute.name()));
				}
			}
			bnx.addNode(attributeName, domain);
		}
		
		for (String startAttribute: connections.keySet()) {
			Set<String> endAttributes = connections.get(startAttribute);
			for (String endAttribute: endAttributes) {
				if (bnx.getNode(startAttribute).getDomain() instanceof Discretized) {
					Domain intermediateDomain = getSplitDomain(splitPoints.get(startAttribute).get(endAttribute));
					String intermediateName = startAttribute + "$" + endAttribute;
					bnx.addNode(intermediateName, intermediateDomain, startAttribute);
					bnx.connect(startAttribute, intermediateName);
					bnx.connect(intermediateName, endAttribute);
				} else {
					bnx.connect(startAttribute, endAttribute);
				}
			}
		}
	}
	
	/**
	 * Collect the split points for an attribute and create a new split point entry for the attribute itself.
	 * @param splitPoints	the split points to use.
	 */
	public static void collectSplitPoints(HashMap<String, HashMap<String, double[]>> splitPoints) {
		for (String attribute: splitPoints.keySet()) {
			HashSet<Double> splitPointsForAttribute = new HashSet<Double>();
			for (double[] sp: splitPoints.get(attribute).values()) {
				for (int j=0; j<sp.length; j++) {
					splitPointsForAttribute.add(sp[j]);
				}
			}
			double[] splits = new double[splitPointsForAttribute.size()];
			int j = 0;
			for (double split: splitPointsForAttribute) {
				splits[j++] = split;
			}
			Arrays.sort(splits);
			splitPoints.get(attribute).put(attribute, splits);	// The self-connection contains the combined splits
		}
	}
	
	/**
	 * Get the domain for a nominal weka attribute.
	 * @param attribute	the attribute to get the values from.
	 * @return			a domain containing the values of the attribute.
	 */
	public static Discrete getDomainForNominalAttribute(Attribute attribute) {
		String[] values = new String[attribute.numValues()];
		for (int i=0; i<attribute.numValues(); i++)
			values[i]=attribute.value(i);
		return new Discrete(values);
	}
	
	/**
	 * Get a discretized domain with the specified splits.
	 * @param split	the splits to use for the domain.
	 * @return		a domain with the given splits.
	 */
	public static Discretized getSplitDomain(double[] split) {
		if (split == null)
			return new Discretized();
		Arrays.sort(split);
		return new Discretized(new DiscretizationFilter.Default(split));
	}
}
