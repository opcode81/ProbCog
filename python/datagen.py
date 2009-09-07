import sys
#print sys.path
HAVE_JAVA = 1
try: 
    import java
    from edu.tum.cs import srldb
    import jarray
except:
    sys.stderr.write("Error: Could not import Java packages! Database generation disabled.")
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

# ---

class Object(object):
    ''' represents an object in the world that has attributes and can be related to other objects '''
    
    def __init__(self, objtype, constantName = None, **args):
        global GUID
        self.links = {}
        self.objtype = objtype
        self.attributes = {}
        GUID += 1
        self.guid = GUID
        if constantName is not None:
            if type(constantName) != str:
                raise Exception("Cannot use constant name '%s' (not a string)" % str(constantName))
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
        links = []
        for lname, lobjs in self.links.items():
            links = []
            for linkobj in lobjs:
                links.append(str(linkobj))
            links.append("%s: %s" % (lname, ", ".join(links)))
        print "%s %s %s" % (self.name(), str(self.attributes), str(links))

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
        if linkName in self.links:
            if not linkobj in self.links[linkName]: # check if same relation already added
                self.links[linkName].append(linkobj)
        else:
            self.links[linkName] = [linkobj]
        return linkobj

    def linkfrom(self, linkName, other, *moreothers):
        return other.linkto(linkName, self, *moreothers)

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
    def __init__(self, objtype, container, attrgens = None):
        '''
            objtype: a string representing the name of the type of the objects that this generator is to generate
            container: a container (ObjectContainer) object in which to store the generated object
            attrgens: a mapping of attribute names to attribute generator objects (e.g. AttrFixed or AttrDist)
        '''
        self.container = container
        if attrgens is None: attrgens = {}
        self.attrgens = attrgens
        self.objtype = objtype

    def generate(self, **args):
        o = Object(self.objtype, **args)
        self._createAttributes(o, **args)
        self._createLinks(o, **args)
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
    def __init__(self, objects = None):
        if objects == None:
            objects = []
        self.objects = objects
        self.removedObjects = []
    
    def add(self, object):
        self.objects.append(object)
    
    def sampleSet(self, minObjects = 1, maxObjects = None):
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

class World:
    def __init__(self):
        self.containers = {}
    
    def addContainer(self, className):
        container = ObjectContainer()
        self.containers[className] = container
        return container
    
    def addObject(self, className, object):
        if not className in self.containers:
            self.containers[className] = ObjectContainer()
        self.containers[className].add(object)
    
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
    

class AttrFixed:
    def __init__(self, value):
        self.value = value
    
    def generate(self):
        return self.value

# attribute value distribution: instantiate as AttrDist({"value": prob, "value2": prob2}) (probs are automatically normalized)
class AttrDist:
    def __init__(self, distribution):
        self.distribution = dict(distribution)
        sum = 0
        for item,value in distribution.items():
            sum += value
            self.distribution[item] = sum
        self.sum = sum

    def generate(self):
        r = random.uniform(0, self.sum)
        for item,value in self.distribution.items():
            if r <= value:
                return item        

class Decider:
    def __init__(self, prob):
        self.prob = prob

    def decide(self):
        if random.uniform(0.0, 1.0) <= self.prob:
            return 1
        return 0

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
