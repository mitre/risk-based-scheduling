import numpy as np

class Day:
    def __init__(self, date, weekdayConfig, slots):
        """
        Constructor class that instantiates Day.
        """
        self.date = date
        self.weekdayConfig = weekdayConfig
        self.labs = weekdayConfig.getLabs()
        self.slots = slots
        self.setPointLimits(weekdayConfig.getPointLimits())
        self.points = 0

    def isScheduleDay(self, start_date, end_date):
        """
        Returns whether a day falls in the schedule period.
        """
        return ((self.date-start_date).days >= 0 and (end_date - self.date).days >= 0)
    
    def setPointLimits(self, point_limits):
        """
        Set various point limits on a weekday (overall and/or individual attributes).
        """
        for key, value in point_limits.items():
            setattr(self, key+'_point_limit', value)
    
    def getLabs(self):
        """
        Returns the labs which are operating on the day.
        """
        return self.labs

    def getDate(self):
        """
        Returns the day's date.
        """
        return self.date

    def getSimDay(self, start_date):
        """
        Returns the day's simulation day number.
        """
        return (self.date-start_date).days

    def getWeekdayName(self):
        """
        Returns the day's weekday name.
        """
        return self.weekdayConfig.name

    def getWeekdayNumber(self):
        """
        Returns the day's weekday number,
        with Monday being 0 and Sunday being 6.
        """
        weekday_names = ["Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday"]
        return weekday_names.index(self.getWeekdayName())

    def getSimWeek(self, start_date):
        """
        Returns the day's simulation week number.
        """
        day = self.getSimDay(start_date)
        return np.floor(day/7)

    def checkPointLevel(self, case, point_ignore):
        """
        Checks if the given case can be placed on this day without violating daily point limit (avg/slot * total slots).
        If it can, updates the points tracker for the day and returns True. Otherwise, adds no points and returns False.
        If case is ignored per point_ignore, then returns True.
        """
        case_points = case.getCaseAttrPoints('points')
        case_attrs = case.getAttributes()
        if len(point_ignore) != 0:
            for pi in point_ignore:
                if case_attrs[pi['attribute']] in pi['values']:
                    return True
        if self.points + case_points > getattr(self, 'overall_point_limit'):
            return False
        else:
            self.points += case_points
            return True
        
    def checkSplitPointLevels(self, lab, case, point_ignore):
        """
        Checks if the given case can be placed on this day without violating any active point attribute limits.
        Returns True if it can, False otherwise.
        If case is ignored per point_ignore, then returns True.
        """
        case_attrs = case.getAttributes()
        if len(point_ignore) != 0:
            for pi in point_ignore:
                if case_attrs[pi['attribute']] in pi['values']:
                    return True
        active_attrs = case.getCaseActiveAttrs()
        for att, which in active_attrs.items():
            this_att_ok = self.checkAttributePoints(lab, case, att, which)
            if not this_att_ok:
                return False
        return True # no attribute's points found to violate
    
    def checkAttributePoints(self, lab, case, att, which):
        """
        Checks if the given case could be placed on this day, potentially in this lab, 
        without violating the point limit corresponding the the passed attribute att.
        Returns True if it could (limit not violated) and False if it could not (limit violated).
        """
        current_total = 0
        att_pts = case.getCaseAttrPoints(att)
        for s in self.slots:
            c = s.getCase()
            if c is not None:
                if which == 'day':
                    current_total += c.getCaseAttrPoints(att)
                elif which == 'lab' and s.getLab() == lab:
                    current_total += c.getCaseAttrPoints(att)
        if current_total + att_pts > getattr(self, att[:-1]+'_limit'):
            return False
        return True

    def checkDurationPoints(self, lab, case):
        """
        Checks if the given case could be placed in the lab on this day without violating time duration point limit.
        Returns True if it could, False otherwise.
        """
        case_duration = case.getCaseAttrPoints('duration')
        lab_duration = 0
        for s in self.slots:
            c = s.getCase()
            if c is not None and s.getLab() == lab:
                lab_duration += c.getCaseAttrPoints('duration')
        if lab_duration + case_duration > getattr(self, 'duration_point_limit'):
            return False
        else:
            return True
        
    def checkRiskPoint(self, case):
        """
        Checks if the given case could be placed on this day without violating risk point limit. 
        Returns True if it could, False otherwise.
        """
        case_risk = case.getCaseAttrPoints('risk')
        today_risk = 0
        for s in self.slots:
            c = s.getCase()
            if c is not None:
                today_risk += c.getCaseAttrPoints('risk')
        if today_risk + case_risk > getattr(self, 'risk_point_limit'):
            return False
        else:
            return True
        
    def getFreePoints(self):
        """
        Returns the day's number of remaining points under its overall limit.
        """
        return getattr(self, 'overall_point_limit') - self.points