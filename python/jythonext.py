
# very basic set implementation that works on hashable objects; only has the functionality required by PyMLNs
class set(object):
    def __init__(self, l = None):
        self.d = {}
        if l is None:
            return
        if type(l) != list:
            raise Exception("Unhandled set initializer type %s" % str(type(l)))
        for key in l:
            self.add(key)
    
    def add(self, item):
        try:
            self.d[item] = 1
        except:
            raise Exception("Cannot add %s to set" % (str(key)))
    
    def __contains__(self, item):
        return item in self.d

    def __iter__(self):
        return self.d.__iter__()

    def difference(self, other):
        d = dict(self.d)
        for x in other:
            if x in d:
                del d[x]
        return set(d.keys())
    
    def __str__(self):
        return "set(%s)" % str(self.d.keys())

    def __len__(self):
        return len(self.d)
    
    def issuperset(self, other):
        for o in other:
            if o not in self.d:
                return False
        return True

    def issubset(self, other):
        return other.issuperset(self)
        