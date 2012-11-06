import sys
from datagen import *
from random import choice, randint, shuffle
        
similarCourse = True

class Course(Object):
    def __init__(self, world, dep, spec, prof):
        Object.__init__(self, "course")     
        
        self.department = dep
        self.specialization = spec
        self.prof = prof
        
        self["difficulty"] = AttrDist({"easy": 0.7, "hard": 0.3}).generate()
        
        world.addObject(self)
        
class Scientist(Object):
    def __init__(self, world, type, dep, spec):
        Object.__init__(self, "scientist")      
        self.type = type
        self.department = dep
        self.specialization = spec
        self.linkto("likes", self) # every person likes him- or herself
        
        # create the courses taught by professors
        if self.type == "professor":
            numCourses = randint(1,3)
            for i in range(numCourses):
                course = Course(world, self.department, self.specialization, self)
                self.linkto("teaches", course)
                
        world.addObject(self)
        
class Student(Object):
    def __init__(self,world):   
        Object.__init__(self, "student")
        
        self.world = world      
        
        self["intelligence"] = AttrDist({"weak": 0.2, "average": 0.6, "smart": 0.2}).generate()
        
        self.coursesTaken = []
        self.initGradeDists = {
            "weak": {"A": 0.1, "B": 0.15, "C": 0.16, "D": 0.045, "F": 0.07}, 
            "average": {"A": 0.145, "B": 0.23, "C": 0.10, "D": 0.02, "F": 0.005},
            "smart": {"A": 0.4, "B": 0.07, "C": 0.02, "D": 0.00, "F": 0.00} }
        self.gradeDist = self.initGradeDists[self["intelligence"]]
        
        # assign advisors
        numAdvisors = AttrDist({0: 0.3, 1: 0.5, 2: 0.2}).generate()
        self.advisors = world.getContainer("scientist").sampleSet(numAdvisors, numAdvisors) 
        for advisor in self.advisors:
            advisor.linkto("advises", self)
            
        self.takeCourses()
        self.setGrades()
        
        world.addObject(self)
        
    def takeCourses(self):
        for course in self.world.getContainer("course").sampleSet(3, 6):
            self.coursesTaken.append(course)
            self.linkto("takes",course)
        # create "takesSimilarCourse" predicate
        for i in range(len(self.coursesTaken)):
            course = self.coursesTaken[i]   
            for course2 in self.coursesTaken[i+1:]:
                if course.department == course2.department and course.specialization == course2.specialization:
                    self.linkto("takesSimilarCourse",course)
                    self.linkto("takesSimilarCourse",course2)
        
    def setGrades(self):
        notTaken = filter(lambda x: x not in self.coursesTaken, world.getContainer("course"))
        for c in notTaken:
            gradeLink = self.linkto("gotGraded",c)
            gradeLink["grade"] = "None"
        for c in self.coursesTaken:
            localGradeDist = self.gradeDist
            likesAdvisor = False
            similarCourse = False
            for advisor in self.advisors:
                if advisor in c.prof.getPartners("likes"):
                    likesAdvisor = True
            for c2 in self.coursesTaken:
                if c2 in c.getPartners("similar"):
                    similarCourse = True
            if c["difficulty"] == "hard":
                localGradeDist["A"] -= 0.1
                localGradeDist["B"] -= 0.05
                localGradeDist["D"] += 0.10
                localGradeDist["F"] += 0.05
            if likesAdvisor:
                localGradeDist["A"] += 0.25
                localGradeDist["B"] += 0.1
                localGradeDist["D"] -= 0.05
                localGradeDist["F"] -= 0.05
            if similarCourse:
                localGradeDist["A"] += 0.1
                localGradeDist["B"] += 0.05
                localGradeDist["D"] -= 0.05
                localGradeDist["F"] -= 0.05
            gradeLink = self.linkto("gotGraded",c)
            gradeLink["grade"] = AttrDist(localGradeDist).generate()        

        
numStudents = 80

world = World()

# generate professors and assistants      
departments = {
    "Philosophy": ["Metaphysics", "Ethics", "Epistemology"],
    "Computer Science" : ["Artificial Intelligence", "Theoretical CS", "Databases"], 
    "Maths": ["Statistics", "Numerics", "Arithmetics"]
}
for dep in departments:
    for spec in dep:
        numProfs = random.randint(1,3)
        numAdvisors = 4-numProfs
        for i in range(numProfs):
            Scientist(world, 'professor', dep, spec)
        for i in range(numAdvisors):
            Scientist(world, 'assistant', dep, spec)

# create the "likes" relation
scientists = world.getContainer("scientist")
for s in scientists:
    for s2 in scientists.sampleSet(1, len(scientists)):
        s.linkto("likes", s2)

# create course similarities
courses = world.getContainer("course")
for i, course in enumerate(courses):
    for course2 in courses[i+1:]:
        if course.department == course2.department and course.specialization == course2.specialization:
            course.linkto("similar", course2)
            course2.linkto("similar", course)

# create students           
for i in range(numStudents):
    student = Student(world)
    
# create the teacherOfLikesAdvisorOf predicate
courses = world.getContainer("course")
numCourses = len(courses)
for i in range(numCourses):
    prof = courses[i].prof
    for likedProf in prof.getPartners("likes"):
        for stud in likedProf.getPartners("advises"):
            courses[i].linkto("teacherOfLikesAdvisorOf",stud)
    
# create the database
db = world.getDatabase()