class Lab:
    def __init__(self, name, schedule, day_params):
        """
        Constructor class that instantiates Lab.
        """
        self.name = name
        self.schedule = schedule
        self.day_params = day_params


    def getSchedule(self):
        """
        Returns the lab's schedule.
        """
        return self.schedule

    def getDayParams(self):
        """
        Returns the lab's day parameter dictionary.
        """
        return self.day_params