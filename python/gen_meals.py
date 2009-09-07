from datagen import *

def strBool(x):
    return {0: "false", 1: "true"}[int(x)]
    
class MealGen(ObjectGenerator):
    def __init__(self, world):
        ObjectGenerator.__init__(self, "meal", world.getContainer("event"), {})
        self.world = world

    def _createAttributes(self, obj, **args):
        weekend = args["day"] >= 5
        ObjectGenerator._createAttributes(self, obj, **args)
        obj.setAttr("weekend", strBool(weekend))
        obj.setAttr("mealType", args["type"])
        times = {"breakfast": [AttrDist({"earlyMorning": 0.8, "lateMorning": 0.2}), AttrDist({"lateMorning": 0.7, "noon": 0.2, "earlyAfternoon": 0.1})],
                 "lunch": [AttrDist({"noon": 0.8, "earlyAfternoon": 0.2}), AttrDist({"noon": 0.5, "earlyAfternoon": 0.5})],
                 "dinner": [AttrDist({"lateAfternoon": 0.3, "evening": 0.7}), AttrDist({"evening": 1.0})]}
        self._createAttribute(obj, "time", times[args["type"]][int(weekend)])
            
    def _createLinks(self, event, **args):
        weekend = args["day"] >= 5
        world = self.world
        # choose people who take part
        people = self.world.sampleSet("person")
        for person in people.items():
            person.linkto("takesPartIn", event)
            # objects that person uses
            if args["type"] == "breakfast":
                if RelationGenerator("usedBy", Chooser(world, "cup"), Decider(0.85), True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "teaspoon"), Decider(0.85), True).generate(person, event)
                else:
                    RelationGenerator("usedBy", Chooser(world, "glass"), Decider(0.90), True).generate(person, event)
                if RelationGenerator("usedBy", Chooser(world, "bowl"), Decider(0.90), True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "spoon"), Decider(0.95), True).generate(person, event)
                if RelationGenerator("usedBy", Chooser(world, "plate"), {True: Decider(0.95), False: Decider(0.6)}[weekend], True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "fork"), Decider(0.5), True).generate(person, event)
                    RelationGenerator("usedBy", Chooser(world, "knife"), Decider(0.95), True).generate(person, event)
            elif args["type"] == "lunch":
                RelationGenerator("usedBy", Chooser(world, "glass"), Decider(0.95), True).generate(person, event)
                if RelationGenerator("usedBy", Chooser(world, "cup"), Decider(0.35), True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "teaspoon"), Decider(0.85), True).generate(person, event)
                if RelationGenerator("usedBy", Chooser(world, "fork"), Decider(0.95), True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "knife"), Decider(0.80), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "spoon"), Decider(0.5), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "plate"), Decider(0.99), True).generate(person, event)
            elif args["type"] == "dinner":
                RelationGenerator("usedBy", Chooser(world, "glass"), Decider(0.95), True).generate(person, event)
                if RelationGenerator("usedBy", Chooser(world, "cup"), Decider(0.20), True).generate(person, event) != None:
                    RelationGenerator("usedBy", Chooser(world, "teaspoon"), Decider(0.85), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "fork"), Decider(0.75), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "knife"), Decider(0.95), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "spoon"), Decider(0.5), True).generate(person, event)
                RelationGenerator("usedBy", Chooser(world, "plate"), Decider(0.99), True).generate(person, event)
        world.restoreAllRemovedObjects()


world = World()
# generate places
'''places = ["table", "cupboard"]
placeCont = world.addContainer("place")
for place in places:
    ObjectGenerator('place', placeCont, {'placeName': AttrFixed(place)}).generate()'''
# generate kitchen utensils
utensils  = ["knife", "spoon", "fork", "teaspoon", "cup", "plate", "glass", "bowl"]    
for utensil in utensils:
    world.addContainer(utensil)
    gen = ObjectGenerator('utensil', world.getContainer(utensil), {'utensiltype': AttrFixed(utensil)})
    for i in range(6):            
        gen.generate()
# generate kitchen users
people = ["Steve", "Pete", "Paul", "Sandy"]
world.addContainer("person")
for person in people:
    ObjectGenerator("person", world.getContainer("person"), {"name": AttrFixed(person)}).generate()
# generate events    
events = world.addContainer("event")
mealgen = MealGen(world)
for day in range(7):
    mealgen.generate(day=day % 7, type="breakfast")
    mealgen.generate(day=day % 7, type="lunch")
    mealgen.generate(day=day % 7, type="dinner")
# print all generated objects
world.printAll()
# return the database
db = world.getDatabase()
