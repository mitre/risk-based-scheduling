import numpy as np

class Slot:
    def __init__(self, date, lab, attendings, case=None):
        """
        Constructor class that instantiates Slot.
        """
        self.date = date
        self.lab = lab
        self.attendings = attendings
        self.case = case
    
    def getLab(self):
        """
        Return the slot's lab name.
        """
        return self.lab.name
    
    def getAttendings(self):
        """
        Return a list of the slot's attending objects.
        """
        return self.attendings
    
    def getCase(self):
        """
        Return the slot's case, which may be None.
        """
        return self.case

    def getWeekday(self):
        """
        Return the slot's weekday.
        """
        return self.date.weekday()

    def getSimDay(self, start_date):
        """
        Return the slot's simulation day number.
        """
        return start_date-self.date

    def getSimWeek(self, start_date):
        """
        Return the slot's simulation week number.
        """
        day = self.getSimDay(start_date)
        return np.floor(day/7)

    def scheduleCase(self, case, attending_obj=None):
        """
        Assigns the given case to this slot, with the specified attending.
        """
        self.case = case
        dow = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"][self.date.weekday()]
        case.assignScheduledAttributes(self.date, dow, self.lab.name, self)
        if case.getAttending() is None and attending_obj is not None:
            case.assignAttending(attending_obj.getName())