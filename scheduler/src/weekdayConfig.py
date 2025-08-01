class WeekdayConfig:
    def __init__(self, name, labs, attendings, point_limits):
        """
        Constructor class that instantiates WeekdayConfig.
        """
        self.name = name
        self.labs = labs
        self.attendings = attendings
        self.point_limits = point_limits

    def getPointLimits(self):
        """
        Returns the point limit dictionary for the weekday.
        """
        return self.point_limits

    def getLabs(self):
        """
        Returns the labs open on the weekday.
        """
        return self.labs

    def getAttendings(self):
        """
        Returns the attendings working on the weekday.
        """
        return self.attendings