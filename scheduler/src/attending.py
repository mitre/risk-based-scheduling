class Attending:
    def __init__(self, name, procedures, schedule):
        """
        Constructor class that instantiates Attending.
        """
        self.name = name
        self.procedures = procedures
        self.schedule = schedule

    def getName(self):
        """
        Returns the name of the attending.
        """
        return self.name

    def getProcedures(self):
        """
        Returns the procedures that the attending can perform.
        """
        return self.procedures

    def getSchedule(self):
        """
        Returns the attending's lab preference ranking.
        """
        return self.schedule