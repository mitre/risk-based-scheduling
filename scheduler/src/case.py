class Case:
    def __init__(self, case_dict, points_active, point_mapping, point_ignore):
        """
        Constructor class that instantiates Case.
        """
        self.is_scheduled = False
        self.attribute_list = list(case_dict.keys())
        for att_name, att_value in case_dict.items():
            setattr(self, att_name, att_value)
        if 'attending' not in case_dict:
            self.attending = None
            self.attribute_list.append('attending')
        self.points_active = points_active
        if self.points_active:
            self.setPointValues(point_mapping, point_ignore)
        self.scheduled_day = None
        self.scheduled_dow = None
        self.scheduled_lab = None
        self.scheduled_slot = None

    def getAttributes(self):
        """
        Returns a dictionary of both the case's original attributes
        and calculated point attributes.
        """
        attributes = {}
        for att in self.attribute_list:
            attributes[att] = getattr(self, att)
        if self.points_active:
            for pa in self.point_attrs:
                attributes[pa] = getattr(self, pa)
            attributes['points'] = self.points
        return attributes
    
    def getAttending(self):
        """
        Returns the name of the attending for the case, which may be None.
        """
        return self.attending

    def assignScheduledAttributes(self, day, dow, lab, slot):
        """
        Sets the scheduled attributes for the case.
        """
        self.scheduled_day = day
        self.scheduled_dow = dow
        self.scheduled_lab = lab
        self.scheduled_slot = slot

    def assignAttending(self, attending):
        """
        Sets the attending assigned to the case (the attending's name).
        """
        self.attending = attending

    def setScheduled(self):
        """
        Sets the case as scheduled.
        """
        self.is_scheduled = True

    def checkScheduled(self):
        """
        Checks if the case has been scheduled, returning True if it has, otherwise False.
        """
        return self.is_scheduled

    def setPointValues(self, point_mapping, point_ignore):
        """
        Sets the point values for the case for any active point system attributes in point_mapping.
        Sets all point values to 0 if the case is to be ignored as designated in point_ignore.
        The attributes set will follow the convention `self.[point attribute name]_points`.
        Also sets attribute `self.point_attrs` which is a list of active case point attributes.
        """
        point_attrs = {}
        case_ignored = False
        if len(point_ignore) != 0:
            for pi in point_ignore:
                if getattr(self, pi['attribute']) in pi['values']:
                    case_ignored = True
                    self.points = 0
                    for f in point_mapping:
                        if f['active']:
                            setattr(self, f['name']+'_points', 0)
                            point_attrs[f['name']+'_points'] = f['which']
                    break
        if not case_ignored:
            points = 0
            for f in point_mapping:
                if f['active']:
                    name = f['name']
                    point_attrs[name+'_points'] = f['which']
                    levels_dict = {}
                    threshes = []
                    for l in f['levels']:
                        levels_dict[l['upperbound']] = l['points']
                        threshes.append(l['upperbound'])
                    if len(threshes) != len(set(threshes)):
                        raise Exception(f"Duplicate upperbound in point levels for {name}.")
                    threshes = sorted(threshes, key=lambda x: (x is None, x))
                    attr = f['case_attribute']
                    is_set = False
                    for t in threshes:
                        if t is not None and getattr(self, attr) <= t:
                            setattr(self, name+'_points', levels_dict[t])
                            points += levels_dict[t]
                            is_set = True
                            break
                    if not is_set:
                        setattr(self, name+'_points', levels_dict[None])
                        points += levels_dict[None]
            self.points = points
        self.point_attrs = point_attrs
    
    def getCaseActiveAttrs(self):
        """
        Returns which point attributes are active for the case,
        in a dictionary mapping each attribute to its scope (lab or day).
        """
        return self.point_attrs

    def getCaseAttrPoints(self, attr):
        """
        Returns the point value associated with the case for the specified attribute attr.
        If attr is `points` it will return the case's overall total point value.
        """
        return getattr(self, attr, None)