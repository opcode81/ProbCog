import sys
#print sys.path
HAVE_JAVA = 1
try: 
    import java
    from edu.tum.cs import srldb
    import jarray
except:
    #sys.stderr.write("Note: Could not import Java packages - database generation disabled.")
    HAVE_JAVA = 0
#print "java: ",HAVE_JAVA
import random

GUID = 1

# -- some functionality from the random module (unsupported by jython)

def sample(population, k):
    n = len(population)
    if not 0 <= k <= n:
        raise ValueError, "sample larger than population"
    _int = int
    result = [None] * k
    if n < 6 * k:     # if n len list takes less space than a k len dict
        pool = list(population)
        for i in xrange(k):         # invariant:  non-selected at [0,n-i)
            j = _int(random.random() * (n-i))
            result[i] = pool[j]
            pool[j] = pool[n-i-1]   # move non-selected item into vacancy
    else:
        try:
            n > 0 and (population[0], population[n/2], population[n-1])
        except (TypeError, KeyError):   # handle sets and dictionaries
            population = tuple(population)
        selected = {}
        for i in xrange(k):
            j = _int(random.random() * n)
            while j in selected:
                j = _int(random.random() * n)
            result[i] = selected[j] = population[j]
    return result

# and general sampling functionality

def sampleDist(values):
    s = sum(values)
    if s == 0:
        raise Exception("Cannot sample from distribution with 0 mass")
    r = random.uniform(0, s)
    currentSum = 0
    for i,v in enumerate(values):
        currentSum += v
        if r <= currentSum:
            return i

# ---


### *** FUNDAMENTAL RELATIONAL ENTITIES: Objects, Relations, etc. ***

class Object(object):
    ''' represents an object in the world that has attributes and can be related to other objects '''
    
    def __init__(self, objtype, constantName = None, **args):
        global GUID
        self.links = {}
        self.partners = {}
        self.objtype = objtype
        self.attributes = {}
        GUID += 1
        self.guid = GUID
        if constantName is not None:
            if type(constantName) != str:
                raise Exception("Cannot use constant name '%s' (not a string, type is '%s')" % (str(constantName), str(type(constantName))))
        self.constantName = constantName
    
    def __str__(self):
        if self.constantName is not None:
            return self.constantName
        else:
            return "%s%d" % (self.objtype, self.guid)
    
    def setAttr(self, attrName, value):
        # attribute values must be strings or integers
        #if type(value) not in (str, int): raise Exception("Value for attribute %s has unacceptable type: '%s'" % (attrName, str(value)))
        self.attributes[attrName] = value
    
    def getAttr(self, attrName):
        return self.attributes[attrName]
    
    def __getitem__(self, item):
        return self.getAttr(item)
    
    def __setitem__(self, attrName, value):
        self.setAttr(attrName, value)
    
    def printInfo(self):
        '''
            prints information on the object (its name, its attributes and its relations to other objects)
        '''
        alllinks = []
        for lname, lobjs in self.links.items():
            links = []
            for linkobj in lobjs:                
                links.append(str(linkobj))                
            alllinks.append("%s: %s" % (lname, ", ".join(links)))
        print "%s %s %s" % (self.name(), str(self.attributes), str(alllinks))

    def linkto(self, linkName, other, *moreothers):
        '''
            links the object to one or more other objects
            if the same link is already present, it is not added again
            returns the link object that was created
        '''
        others = list(moreothers)
        others.insert(0, other)
        args = [self] + others
        linkobj = Link(linkName, *args)
        # add link to list of links
        if linkName in self.links:
            if not linkobj in self.links[linkName]: # check if same relation already added
                self.links[linkName].append(linkobj)
        else:
            self.links[linkName] = [linkobj]
        # add other(s) to list of partners
        if linkName not in self.partners:
        	self.partners[linkName] = []
        if len(moreothers) == 0:
        	self.partners[linkName].append(other)
        else:
        	self.partners[linkName].append(others)
        # return the link object
        return linkobj
    
    def notlinkto(self, linkName, other, *moreothers):
        lnk = self.linkto(linkName, other, *moreothers)
        lnk.setExists(False)

    def linkfrom(self, linkName, other, *moreothers):
        return other.linkto(linkName, self, *moreothers)

    def getPartners(self, linkName):
        ''' gets the list of partners (in case of a binary relation) or list of lists of partners (in case of a higher-arity relation) for the given link name '''
        return self.partners.get(linkName, [])

    def name(self):
        return "%s%d" % (self.objtype, self.guid)
    
    def hasLink(self, link):
        if not link.relationName in self.links:
            return False
        return link in self.links[link.relationName]
    
class Link(object):
    ''' represents a (named) link between two or more objects in the world '''
    
    def __init__(self, name, *objects):
        self.attributes = {}
        self.relationName = name
        self.objects = objects
        self.exists = True
    
    def setExists(self, exists):
        self.exists = exists
    
    def setAttr(self, attrName, value):
        # attribute values must be strings or integers
        #if type(value) not in (str, int): raise Exception("Value for attribute %s has unacceptable type: '%s'" % (attrName, str(value)))
        self.attributes[attrName] = value
    
    def getAttr(self, attrName):
        return self.attributes[attrName]

    def __getitem__(self, item):
        return self.getAttr(item)
    
    def __setitem__(self, attrName, value):
        self.setAttr(attrName, value)
    
    def __eq__(self, other):
        return self.relationName == other.relationName and self.objects == other.objects
    
    def __str__(self):
        return "%s(%s)" % (self.relationName, ",".join(map(str, self.objects)))

class ConstantAsObject(Object):
    '''
        represents an object that is actually a constant, for creating links that involve constants,
        e.g. in jobOf(Professor, PersonX), Professor could be a constant object to which PersonX is related
        
        Instances of this class are not to be added to worlds explicitly!
    '''
        
    def __init__(self, constantName, world):
        '''
            instantiates a constant that can be used as an object, adding it to the given world if the world does not contain it yet
            If the world does already contain the constant, this object essentially becomes a reference to the previous occurrence
        '''
        Object.__init__(self, "constant", constantName)
        constants = world.getContainer("constant")
        if constants is not None:
            for c in constants:
                if c.constantName == constantName:
                    self.__dict__ = c.__dict__
                    return
        
        world.addObject("constant", self)
        
class ObjectGenerator:
    def __init__(self, objtype, world_or_container, attrgens = None):
        '''
            objtype: a string representing the name of the type of the objects that this generator is to generate
            world_or_container:
                either the world object (if it has a container for objtype, it is used for generated objects; otherwise the container is added) or
                directly a container (ObjectContainer instance) in which to store the generated object
            attrgens: a mapping of attribute names to attribute generator objects (e.g. AttrFixed or AttrDist)
        '''
        if isinstance(world_or_container, World):
            self.container = world_or_container.getContainer(objtype)
            if self.container is None:
                self.container = world_or_container.addContainer(objtype)
        else:
            self.container = world_or_container
        if attrgens is None: attrgens = {}
        self.attrgens = attrgens
        self.objtype = objtype
    
    def setAttrGen(attr, gen):
        self.attrgens[attr] = gen
       
    def generate(self, **kwargs):
        o = Object(self.objtype, **kwargs)
        self._createAttributes(o, **kwargs)
        self._createLinks(o, **kwargs)
        self.container.add(o)
        return o
    
    def _createAttributes(self, obj, **args):
        for attrName,gen in self.attrgens.items():
            self._createAttribute(obj, attrName, gen)

    def _createAttribute(self, obj, attrname, attrgen):
        obj.setAttr(attrname, attrgen.generate())

    def _createLinks(self, obj, **args):
        pass
    
class ObjectContainer:
    ''' represents a collection of objects '''
    
    def __init__(self, objects = None):
        if objects == None:
            self.objects = []
        else:
            self.objects = list(objects)
        self.removedObjects = []
    
    def add(self, object):
        self.objects.append(object)
    
    def sampleSet(self, minObjects = 1, maxObjects = None):
        '''
            samples minObjects to maxObjects from this container (the number is uniformly chosen), returning a new container.
            If maxObjects is None, at most as many objects are chosen as there are in the container
        '''
        if maxObjects == None:
            maxObjects = len(self.objects)
        return ObjectContainer(sample(self.objects, random.randint(minObjects, maxObjects)))

    def items(self):
        return self.objects
        
    def __iter__(self):
        return iter(self.objects)

    def sampleObject(self):
        return random.choice(self.objects)

    def sampleRemoveObject(self):
        o = self.sampleObject()
        self.removedObjects.append(o)
        self.objects.remove(o)
        return o

    def restoreRemovedObjects(self):
        self.objects.extend(self.removedObjects)
        self.removedObjects = []
        
    def __getitem__(self, key):
        return self.objects[key]
    
    def __len__(self):
        return len(self.objects)

    def __str__(self):
        return "ObjectContainer[size=%d]" % len(self)
    
    def __contains__(self, item):
        return item in self.objects
    
    def delete(self, object):
        ''' permanently remove the given object '''
        self.objects.remove(object)
        
    def difference(self, container):
        ''' returns a container with the objects of this container not contained in the given container '''
        return ObjectContainer(filter(lambda x: x not in container, self.objects))
    
    def filter(self, criterion):
        return ObjectContainer(filter(criterion, self.objects))

    def clear(self):
        self.objects = []

class World:
    def __init__(self):
        self.containers = {}
    
    def addContainer(self, className):
        container = ObjectContainer()
        self.containers[className] = container
        return container
    
    def addObject(self, arg1, arg2 = None):        
        ''' If only one arg1 is given, then arg1 is the object to add (and it is added to the container that is named after the object's type);
            If both args are given, then the first is the name of the container and the second is the object;
            The container is added if it does not already exist '''
        if arg2 is None:
            obj = arg1
            className =  obj.objtype
        else:
            className = arg1
            obj = arg2
            if type(className) != str: raise Exception("First argument (arg1) must be a string")
        if not className in self.containers:
            self.containers[className] = ObjectContainer()
        self.containers[className].add(obj)
    
    def getContainer(self, className):
        return self.containers.get(className)
    
    def sampleSet(self, className, minObjects = 1, maxObjects = None):
        return self.containers[className].sampleSet(minObjects, maxObjects)
    
    def printAll(self):
        for containerName, container in self.containers.items():
            print "%s:" % containerName
            for object in container.items():
                print "  ",
                object.printInfo()

    def sampleRemoveObject(self, className):
        ''' samples an object of the given class, removing it from the corresponding container '''
        return self.containers[className].sampleRemoveObject()

    def sampleObject(self, className):
        ''' samples an object from the container that corresponds to the given class '''
        return self.containers[className].sampleObject()

    def restoreAllRemovedObjects(self):
        for container in self.containers.values():
            container.restoreRemovedObjects()

    def restoreRemovedObjects(self, className):
        self.containers[className].restoreRemovedObjects()

    def getDatabase(self):
        ''' for Jython usage: returns an edu.tum.cs.srldb.Database object that corresponds to this world '''
        if HAVE_JAVA == 0:
            sys.stderr.write("getDatabase() not supported; Java classes were not imported!\n")
            return None
        db = srldb.Database(srldb.datadict.AutomaticDataDictionary())
        objects = {}
        links = []
        # go through all object types/containers
        for container in self.containers.values():
            # objects in the container
            for object in container.items():
                if object.guid in objects: # skip objects we previously handled
                    continue
                # if the object is an actual object (not a dummy object representing a constant), instantiate a java version of it
                if type(object) != ConstantAsObject:
                    # instantiate a java object
                    o = srldb.Object(db, object.objtype, object.constantName)
                    # add attributes of the object
                    for attr, value in object.attributes.items():
                        if type(value) == str:
                            value = value.replace(" ", "_")
                        else:
                            value = str(value)
                        o.addAttribute(attr, str(value))
                    o.commit()
                    objects[object.guid] = o
                # collect links of the object
                for linkname, linklist in object.links.items():
                    for linkobj in linklist:
                        args = []
                        for linkedobj in linkobj.objects:
                            if linkedobj is None:
                                raise Exception("None argument in %s%s" % (linkname, str(t)))
                            if type(linkedobj) == str: # link to a constant instead of an object
                                args.append(srldb.ConstantArgument(linkedobj))
                            elif type(linkedobj) == ConstantAsObject:
                                args.append(srldb.ConstantArgument(linkedobj.constantName))
                            else:
                                args.append(linkedobj.guid)
                        links.append((linkobj, args))
        # process links
        for linkobj, args in links:
            # collect arguments
            java_args = []
            for arg in args:
                if type(arg) == int:
                    java_args.append(objects[arg]) # from GUID mapping
                else:
                    java_args.append(arg) # directly
            # construct object
            l = srldb.Link(db, linkobj.relationName, jarray.array(java_args, srldb.IRelationArgument))
            if not linkobj.exists: l.setExists(False)
            # add attributes
            for attr, value in linkobj.attributes.items():
                if type(value) == str:
                    value = value.replace(" ", "_")
                else:
                    value = str(value)
                l.addAttribute(attr, str(value))
            l.commit()
        return db

    def __getitem__(self, key):
        return self.getContainer(key)
    
    def clear(self):
        for c in self.containers.values():
            c.clear()

# *** ATTRIBUTE GENERATION ***

class AttrGen:
    def generate(self, **params):
        raise Exception("abstract")
    
    def getValues(self):
        ''' gets the values that are possibly generated by this '''
        raise Exception("abstract")

class AttrFixed(AttrGen):
    def __init__(self, value):
        self.value = value
    
    def generate(self, **params):
        return self.value
    
    def getValues(self):
        return [self.value]

class AttrDist(AttrGen):
    '''
        attribute value distribution (for sampling from a distribution):
        instantiate as AttrDist({"value1": prob1, "value2": prob2, ...}) (probs are automatically normalized)
        or alternatively using AttrDist(value1=prob1, value2=prob2, ...)
    '''
    
    def __init__(self, distribution=None, **convenient_distribution_specification):
        if distribution is None:
            distribution = {}
        distribution = dict(distribution) # copying for safety reasons
        distribution.update(convenient_distribution_specification)
        self.distribution = distribution
        sum = 0.0
        for item,value in distribution.items():
            sum += value
            self.distribution[item] = sum
        self.sum = sum

    def generate(self, **params):
        r = random.uniform(0, self.sum)
        for item,value in self.distribution.items():
            if r <= value:
                return item        

    def getValues(self):
        ''' gets the values that are possibly generated by this '''
        return self.distribution.keys()

class AttrSet(AttrGen):
    ''' represents a set of attributes/values with an attached probability. We sample each value independently based on the given probability to ultimately generate a (potentially empty) set of values '''
    
    def __init__(self, values2probs = None, **convenient_specification):
        if values2probs is None:
            values2probs = {}
        self.values2probs = dict(values2probs)
        self.values2probs.update(convenient_specification)
    
    def generate(self, **params):
        ''' returns a list of sampled values'''
        ret = []
        for value,prob in self.values2probs.iteritems():
            if(decide(prob)):
                ret.append(value)
        return ret
    
    def remove(self, value):
        if value in self.values2probs:
            del self.values2probs[value]
        
    def getValues(self):
        ''' gets the values that are possibly generated by this generate '''
        return self.values2probs.keys()
    
class AttrSetMin(AttrSet):
    ''' samples values independently, but returns at least a min. number of values;
        if independent sampling results in fewer items, then more items are sampled from the implied distribution '''
    
    def __init__(self, minNumSampledValues, values2probs = None, **convenient_specification):
        AttrSet.__init__(self, values2probs, **convenient_specification)
        self.minValues = minNumSampledValues
    
    def generate(self, **params):
        result = AttrSet.generate(self, **params)
        if len(result) < self.minValues:
            dist = dict(self.values2probs)
            for x in result: del dist[x]
            while len(result) < self.minValues:
                value = AttrDist(dist).generate()
                del dist[value]
                result.append(value)
        return result
            

class AttrCond:
    ''' generates attributes/values if a precondition is met '''
    
    def __init__(self, precond, attrGenPrecondMet, attrGenOtherwise = None):
        ''' precondition: either a DecisionMaker object or a string literal that must previously have been generated (only applicable if used within an AttrGenChain) '''
        self.attrGen = attrGenPrecondMet
        self.attrGenOtherwise = attrGenOtherwise
        self.precond = precond
        
    def generate(self, **params):
        ''' returns the generated attribute(s) or None '''
        if isinstance(self.precond, DecisionMaker):
            precondMet = self.precond.decide()
        else:
            if not isinstance(self.precond, str):
                raise Exception("Invalid precondition: %s", str(self.precond))
            precondMet = self.precond in params['genChain'].attrs
        if precondMet:
            return self.attrGen.generate()
        else:
            if self.attrGenOtherwise is not None:
                return self.attrGenOtherwise.generate()
            else:
                return None
    
    def getValues(self):
        result = self.attrGen.getValues()
        if self.attrGenOtherwise is not None:
            result.extend(self.attrGenOtherwise.getValues())
        return result

class AttrGenChain:
    ''' container for a sequence of attribute generators '''
    
    def __init__(self, *gens):
        self.gens = gens
        
    def getGenerators(self):
        return self.gens
        
    def generate(self, **params):
        ''' returns a list generated attributes '''
        self.attrs = []
        for gen in self.gens:
            result = gen.generate(genChain=self)
            if result is not None:
                if not type(result) == list:
                    result = [result]
                self.attrs.extend(result)
        return self.attrs   
        
        
# **** DECISION MAKING ***

class DecisionMaker:
    def decide(self):
        raise Exception("abstract class")

class Decider(DecisionMaker):
    def __init__(self, prob):
        self.prob = prob

    def decide(self):
        if random.uniform(0.0, 1.0) <= self.prob:
            return 1
        return 0

def decide(p):
    return Decider(p).decide()
    
   
# **** OBJECT SELECTION ***

class Selector:
    def __init__(self, container, removeChosenObject = False):
        if not isinstance(container, ObjectContainer):
            raise Exception("Selector requires the container to be an instance of ObjectContainer, got %s" % str(type(container)))
        self.container = container
        self.removeChosenObject = removeChosenObject

    def _pickOne(self):
        ''' randomly picks an object from this selector's container '''
        try:
            if self.removeChosenObject:
                return self.container.sampleRemoveObject()
            else:
                return self.container.sampleObject()
        except IndexError:
            raise Exception("No (further) objects in container!\n")

    def pick(self):
        ''' picks one or more objects, depending on concrete selector semantics; always returns a list '''
        raise Exception("abstract class")
    
    def pickOne(self):
        ''' performs the pick defined by the selector and returns the first object picked (if any) or None '''
        ret = self.pick()
        if len(ret) > 0:
            return ret[0]
        return None
   
class SelFixed(Selector):
    ''' always selects a particular object '''
    
    def __init__(self, object):
        self.object = object
    
    def pick(self):
        return [self.object]

class SelOne(Selector):
    ''' randomly selects one object from a container '''
    
    def __init__(self, container, removeChosenObject = False):
        Selector.__init__(self, container, removeChosenObject)

    def pick(self):
        ret = []
        ret.append(self._pickOne())
        return ret

    def canPick(self):
        return len(self.container) > 0

class SelCond(SelOne):
    ''' selects one object if a condition is met '''
    
    def __init__(self, decider, container, removeChosenObject = False):
        SelOne.__init__(self, container, removeChosenObject)
        self.decider = decider
        
    def pick(self):
        if self.decider.decide():
            return SelOne.pick(self)
        else:
            return []

class SelProb(SelCond):
    ''' selects one object from a container with a given probability '''
    
    def __init__(self, probability, container, removeChosenObject = False):
        SelCond.__init__(self, Decider(probability), container, removeChosenObject)

class SelOneChain(Selector):
    ''' selects from a chain of Selectors until at least one object could be selected '''
    
    def __init__(self, *selectors):
        self.selectors = selectors
        
    def pick(self):
        ret = []
        for s in self.selectors:
            if s.canPick():
                ret.extend(s.pick())
                if len(ret) > 0:
                    break
        if len(ret) == 0:
            raise Exception("Selector chain could not select an object.")
        return ret

class SelDist(Selector):
    ''' selector that chooses the selector to use from a given distribution over selectors '''
    
    def __init__(self, *distribution):
        ''' the distribution is given as additional parameters alternating between probability values and selectors, e.g. "0.6, selector1, 0.4, selector2";
            the distribution need not be normalized '''
        if len(distribution) % 2 != 0:
            raise Exception("Illegal arguments, must provide alternating probability and selectors")
        self.selectors = []
        self.probabilities = []
        for i in range(len(distribution)/2):
            self.probabilities.append(distribution[i*2])
            self.selectors.append(distribution[i*2+1])
    
    def pick(self):
        i = sampleDist(self.probabilities)
        return self.selectors[i].pick()

		
# legacy stuff

class RelationGenerator:
    def __init__(self, linkName, chooser, decider = None, chosenIsFirstArg = 0):
        self.linkName = linkName
        self.chooser = chooser
        if decider is None: decider = Decider(1.0)
        self.decider = decider
        self.chosenIsFirstArg = chosenIsFirstArg

    def generate(self, arg, *moreargs):
        if self.decider.decide():
            return self._generate(arg, *moreargs)
        return None

    def generateWithProb(self, prob, arg, *moreargs):
        if Decider(prob).decide():
            return self._generate(arg, *moreargs)
        return None

    def _generate(self, arg, *moreargs):
        chosen = self.chooser.choose()
        if self.chosenIsFirstArg:
            chosen.linkto(self.linkName, arg, *moreargs)
        else:
            arg.linkto(self.linkName, chosen, *moreargs)
        return chosen

class Chooser:
    def __init__(self, world, className, removeChosenObject = 1):
        self.className = className
        self.container = world.getContainer(className)
        self.removeChosenObject = removeChosenObject

    def choose(self):
        try:
            if self.removeChosenObject:
                return self.container.sampleRemoveObject()
            else:
                return self.container.sampleObject()
        except IndexError:
            raise Exception("Error: No more objects in class %s\n" % self.className)
