import datetime
from abc import ABC, abstractmethod
import numpy as np
from slot import Slot
DAYS_PER_WEEK = 7

np.random.seed(44)

class Algorithm(ABC):
    def __init__(self, algo_name):
        """
        Constructor class for Attending interface.
        """
        self.algo_name = algo_name
        super().__init__()

    @abstractmethod
    def scheduleCases(self, board, case_list):
        """
        Abstract method for scheduling cases.
        """
        pass


class Randomly(Algorithm):
    """
    Overrides Algorithm abstract method.
    Schedules cases in a fully random order into an available slot, adding one randomly if necessary, using the config-specified attribute to choose where.
    Schedules with a defined arrival rate and time window.
    Cases must have a `procedure` attribute on which to check if a case can be done in a certain slot.
    """
    def scheduleCases(self, board, case_list):
        time_window = board.case_arrivals['time_window'] * DAYS_PER_WEEK
        lead_days = board.case_arrivals['lead_weeks'] * DAYS_PER_WEEK
        for k in range(len(case_list)):
            next_case = case_list[k]
            time_window_start = max(next_case.arrival_scheduler_day, next_case.arrival_scheduler_day + lead_days)
            d = 0 # counter of how many days checked
            shuffled_days = np.arange(time_window)
            np.random.shuffle(shuffled_days)
            
            while d < time_window and not next_case.checkScheduled():
                j = shuffled_days[d] # select next day from shuffled available days
                if time_window_start + j >= len(board.days):
                    d += 1
                else:
                    this_day = board.days[time_window_start+j]
                    slot_order = np.arange(len(this_day.slots))
                    np.random.shuffle(slot_order)
                    for i in slot_order:
                        slot = this_day.slots[i]
                        if slot.getCase() is None:
                            if next_case.getAttending() is None:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in slot.lab.getDayParams()[this_day.getWeekdayName()]['procedures']:
                                        slot.scheduleCase(next_case, possible_attend_match)
                                        next_case.setScheduled()
                                        print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+possible_attend_match.getName()+'.')
                                        if slot.date >= board.start_date and slot.date <= board.end_date:
                                            board.addDayOfWeekScheduling(slot, 1)
                                        print(next_case.getAttributes())
                                        break
                            else:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.getAttending() == possible_attend_match.getName():
                                        slot.scheduleCase(next_case, possible_attend_match)
                                        next_case.setScheduled()
                                        print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' with requested attending, '+possible_attend_match.getName()+'.')
                                        if slot.date >= board.start_date and slot.date <= board.end_date:
                                            board.addDayOfWeekScheduling(slot, 1)
                                        print(next_case.getAttributes())
                                        break  
                        if next_case.checkScheduled():
                            break                              
                    d += 1

            if not next_case.checkScheduled():
                d = 0
                np.random.shuffle(shuffled_days)
                while d < time_window and not next_case.checkScheduled():
                    j = shuffled_days[d] # select next day from shuffled available days
                    if time_window_start + j >= len(board.days):
                        d += 1
                    else:
                        this_day = board.days[time_window_start+j]
                        slot_order = np.arange(len(this_day.slots))
                        np.random.shuffle(slot_order)
                        for i in slot_order:
                            slot = this_day.slots[i]
                            for possible_attend_match in slot.getAttendings():
                                if not next_case.checkScheduled() and (
                                        next_case.getAttending() == possible_attend_match.getName() or (
                                        next_case.getAttending() is None and next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in slot.lab.getDayParams()[this_day.getWeekdayName()]['procedures'])):
                                    # a filled slot could have worked, so add slot to this lab and schedule there
                                    new_slot = Slot(board.board_start_date + datetime.timedelta(days=int(time_window_start+j)), slot.lab, [possible_attend_match], 0)
                                    board.days[time_window_start+j].slots.append(new_slot)
                                    new_slot.scheduleCase(next_case, possible_attend_match)
                                    next_case.setScheduled()
                                    print('Added slot to schedule case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+possible_attend_match.getName()+'.')
                                    print(next_case.getAttributes())
                                    if new_slot.date >= board.start_date and new_slot.date <= board.end_date:
                                        board.addToCountMetricAttr('added_slot_scheduled_cases', 1)
                                        board.addDayOfWeekScheduling(new_slot, 1)
                                    break
                            if next_case.checkScheduled():
                                break
                        if not next_case.checkScheduled():
                            d += 1
                if not next_case.checkScheduled():
                    print('Case ' +str(k)+' still not scheduled.') # no eligible labs at all within time window
                    print(next_case.getAttributes())
                    board.addToCountMetricAttr('unscheduled_overall_cases', 1)
        return board
        

class Points(Algorithm):
    """
    Overrides Algorithm abstract method.
    Schedules cases in random order with daily point caps.
    Schedules with a defined arrival rate and time window.
    Cases must have a `procedure` attribute on which to check if a case can be done in a certain slot.
    """
    def scheduleCases(self, board, case_list):
        time_window = board.case_arrivals['time_window'] * DAYS_PER_WEEK
        lead_days = board.case_arrivals['lead_weeks'] * DAYS_PER_WEEK
        for k in range(len(case_list)):
            next_case = case_list[k]
            time_window_start = max(next_case.arrival_scheduler_day, next_case.arrival_scheduler_day + lead_days)
            d = 0 # counter of how many days checked
            shuffled_days = np.arange(time_window)
            np.random.shuffle(shuffled_days)
            backup_options = {}
            
            while d < time_window and not next_case.checkScheduled():
                j = shuffled_days[d] # select next day from shuffled available days
                if time_window_start + j >= len(board.days):
                    d += 1
                else:
                    this_day = board.days[time_window_start+j]
                    slot_order = np.arange(len(this_day.slots))
                    np.random.shuffle(slot_order)
                    for i in slot_order:
                        slot = this_day.slots[i]
                        if slot.getCase() is None:
                            if next_case.getAttending() is None:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in slot.lab.getDayParams()[this_day.getWeekdayName()]['procedures']:
                                        if this_day.checkPointLevel(next_case, board.getPointIgnore()):
                                            slot.scheduleCase(next_case, possible_attend_match)
                                            next_case.setScheduled()
                                            print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+possible_attend_match.getName()+'.')
                                            if slot.date >= board.start_date and slot.date <= board.end_date:
                                                board.addDayOfWeekScheduling(slot, 1)
                                            print(next_case.getAttributes())
                                        else:
                                            backup_options[time_window_start+j] = (slot, possible_attend_match)
                                    if next_case.checkScheduled():
                                        break
                            else:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.getAttending() == possible_attend_match.getName():
                                        if this_day.checkPointLevel(next_case, board.getPointIgnore()):
                                            slot.scheduleCase(next_case, possible_attend_match)
                                            next_case.setScheduled()
                                            print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' with requested attending, '+possible_attend_match.getName()+'.')
                                            if slot.date >= board.start_date and slot.date <= board.end_date:
                                                board.addDayOfWeekScheduling(slot, 1)
                                            print(next_case.getAttributes())
                                        else:
                                            backup_options[time_window_start+j] = (slot, possible_attend_match)
                                    if next_case.checkScheduled():
                                        break
                        if next_case.checkScheduled():
                            break
                    d += 1

            if not next_case.checkScheduled():
                available_days = list(backup_options.keys())
                available_days.sort()

                if len(available_days) == 0: # no available slots to put the case regardless of points, look for best day on which to add a slot
                    new_days = np.arange(time_window)
                    best_new_day = None
                    best_new_lab = None
                    best_new_points = None
                    best_new_attend = None
                    for n in new_days:
                        today_num = time_window_start+n
                        today = board.days[today_num]
                        best_labs_on_day = {} # map from eligible labs found to the attending and total points in that lab (on that day)
                        for s in range(len(today.slots)):
                            check_slot = today.slots[s]
                            if next_case.getAttending() is None:
                                for possible_attend_match in check_slot.getAttendings():
                                    if next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in check_slot.lab.getDayParams()[today.getWeekdayName()]['procedures']:
                                        # there is an eligible slot (type & attending) in this lab on this day, so add this day/lab pair as an option and append points from its slot
                                        if check_slot.lab in best_labs_on_day:
                                            best_labs_on_day[check_slot.lab]['points'] += check_slot.getCase().getCaseAttrPoints('points')
                                        else:
                                            point_attend_dict = {}
                                            point_attend_dict['attending'] = possible_attend_match
                                            point_attend_dict['points'] = check_slot.getCase().getCaseAttrPoints('points')
                                            best_labs_on_day[check_slot.lab] = point_attend_dict
                                        break
                            else:
                                for possible_attend_match in check_slot.getAttendings():
                                    if next_case.getAttending() == possible_attend_match.getName():
                                        if check_slot.lab in best_labs_on_day:
                                            best_labs_on_day[check_slot.lab]['points'] += check_slot.getCase().getCaseAttrPoints('points')
                                        else:
                                            point_attend_dict = {}
                                            point_attend_dict['attending'] = possible_attend_match
                                            point_attend_dict['points'] = check_slot.getCase().getCaseAttrPoints('points')
                                            best_labs_on_day[check_slot.lab] = point_attend_dict
                                        break
                            if next_case.checkScheduled():
                                break
                        if len(best_labs_on_day) != 0:
                            # selects the lab where there are the fewest total points as the best, regardless of number of cases
                            current_best_lab = list(best_labs_on_day.keys())[0]
                            current_best_points = best_labs_on_day[current_best_lab]['points']
                            current_best_attend = best_labs_on_day[current_best_lab]['attending']
                            for bl in best_labs_on_day:
                                if best_labs_on_day[bl]['points'] < current_best_points:
                                    current_best_points = best_labs_on_day[bl]['points']
                                    current_best_lab = bl
                                    current_best_attend = best_labs_on_day[bl]['attending']
                            if best_new_points is None or current_best_points < best_new_points:
                                best_new_points = current_best_points
                                best_new_lab = current_best_lab
                                best_new_day = today_num
                                best_new_attend = current_best_attend
                    if best_new_day is not None and best_new_points is not None and best_new_attend is not None and best_new_lab is not None:
                        best_new_date = board.board_start_date + datetime.timedelta(days=int(best_new_day))
                        new_slot = Slot(best_new_date, best_new_lab, [best_new_attend], 0)
                        board.days[best_new_day].slots.append(new_slot)
                        new_slot.scheduleCase(next_case, best_new_attend)
                        next_case.setScheduled()
                        print('Added slot to schedule case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+best_new_attend.getName()+'.')
                        print(next_case.getAttributes())
                        if new_slot.date >= board.start_date and new_slot.date <= board.end_date:
                            board.addToCountMetricAttr('added_slot_scheduled_cases', 1)
                            board.addDayOfWeekScheduling(new_slot, 1)
                    else:
                        print('Case ' +str(k)+' still not scheduled.') # no eligible labs at all within time window
                        print(next_case.getAttributes())
                        board.addToCountMetricAttr('unscheduled_overall_cases', 1)

                else: # schedule to earliest best matching slot, i.e. which has most free points under limit
                    best_day = available_days[0]
                    best_points = board.days[best_day].getFreePoints()
                    for ad in available_days:
                        check_day = board.days[ad]
                        new_points = check_day.getFreePoints()
                        if new_points > best_points:
                            best_day = ad
                            best_points = new_points
                    slot = backup_options[best_day][0]
                    attend = backup_options[best_day][1]
                    slot.scheduleCase(next_case, attend)
                    next_case.setScheduled()
                    print('Forcibly scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+attend.getName()+'.')
                    print(next_case.getAttributes())
                    if slot.date >= board.start_date and slot.date <= board.end_date:
                        board.addToCountMetricAttr('total_forcibly_scheduled_cases', 1)
                        board.addDayOfWeekScheduling(slot, 1)
        return board
    

class PointsSplit(Algorithm):
    """
    Overrides Algorithm abstract method.
    Schedules cases in random order with config-specified day- and lab-based point caps for different case point attributes.
    Schedules with a defined arrival rate and time window.
    Cases must have a `procedure` attribute on which to check if a case can be done in a certain slot.
    """
    def scheduleCases(self, board, case_list):
        time_window = board.case_arrivals['time_window'] * DAYS_PER_WEEK
        lead_days = board.case_arrivals['lead_weeks'] * DAYS_PER_WEEK
        for k in range(len(case_list)):
            next_case = case_list[k]
            time_window_start = max(next_case.arrival_scheduler_day, next_case.arrival_scheduler_day + lead_days)
            d = 0 # counter of how many days checked
            shuffled_days = np.arange(time_window)
            np.random.shuffle(shuffled_days)
            backup_options = {'over_all': {}}
            for ra in board.point_imp_order:
                backup_options[ra] = {}
            
            while d < time_window and not next_case.checkScheduled():
                j = shuffled_days[d] # select next day from shuffled available days
                if time_window_start + j >= len(board.days):
                    d += 1
                else:
                    this_day = board.days[time_window_start+j]
                    slot_order = np.arange(len(this_day.slots))
                    np.random.shuffle(slot_order)
                    for i in slot_order:
                        slot = this_day.slots[i]
                        if slot.getCase() is None:
                            if next_case.getAttending() is None:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in slot.lab.getDayParams()[this_day.getWeekdayName()]['procedures']:
                                        if this_day.checkSplitPointLevels(slot.getLab(), next_case, board.getPointIgnore()):
                                            slot.scheduleCase(next_case, possible_attend_match)
                                            next_case.setScheduled()
                                            print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+possible_attend_match.getName()+'.')
                                            if slot.date >= board.start_date and slot.date <= board.end_date:
                                                board.addDayOfWeekScheduling(slot, 1)
                                            print(next_case.getAttributes())
                                        else: # at least one point limit violated, compile days where each ranked attribute wouldn't be violated
                                            slot_sorted = False
                                            for ranked_att in board.point_imp_order:
                                                if this_day.checkAttributePoints(slot.getLab(), next_case, ranked_att+'_points', next_case.getCaseActiveAttrs()[ranked_att+'_points']) and not slot_sorted:
                                                    backup_options[ranked_att][time_window_start+j] = (slot, possible_attend_match)
                                                    slot_sorted = True
                                            if not slot_sorted:
                                                backup_options['over_all'][time_window_start+j] = (slot, possible_attend_match)
                                    if next_case.checkScheduled():
                                        break
                            else:
                                for possible_attend_match in slot.getAttendings():
                                    if next_case.getAttending() == possible_attend_match.getName():
                                        if this_day.checkSplitPointLevels(slot.getLab(), next_case, board.getPointIgnore()):
                                            slot.scheduleCase(next_case, possible_attend_match)
                                            next_case.setScheduled()
                                            print('Scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' with requested attending, '+possible_attend_match.getName()+'.')
                                            if slot.date >= board.start_date and slot.date <= board.end_date:
                                                board.addDayOfWeekScheduling(slot, 1)
                                            print(next_case.getAttributes())
                                        else: # at least one point limit violated, compile days where each ranked attribute wouldn't be violated
                                            slot_sorted = False
                                            for ranked_att in board.point_imp_order:
                                                if this_day.checkAttributePoints(slot.getLab(), next_case, ranked_att+'_points', next_case.getCaseActiveAttrs()[ranked_att+'_points']) and not slot_sorted:
                                                    backup_options[ranked_att][time_window_start+j] = (slot, possible_attend_match)
                                                    slot_sorted = True
                                            if not slot_sorted:
                                                backup_options['over_all'][time_window_start+j] = (slot, possible_attend_match)
                                    if next_case.checkScheduled():
                                        break
                        if next_case.checkScheduled():
                            break
                    d += 1

            if not next_case.checkScheduled():
                total_open_slots = 0
                backup_option_days = {}
                for cat, days in backup_options.items():
                    total_open_slots += len(days)
                    cat_days = list(days.keys())
                    cat_days.sort()
                    backup_option_days[cat] = cat_days

                if total_open_slots == 0:
                    # no available slots to put the case regardless of points, look for best day on which to add a slot
                    if board.added_slot_attr == 'overall':
                        added_slot_attr = 'points'
                    else:
                        added_slot_attr = board.added_slot_attr + '_points'
                    new_days = np.arange(time_window)
                    best_new_day = None
                    best_new_lab = None
                    best_new_points = None
                    best_new_attend = None
                    for n in new_days:
                        today_num = time_window_start+n
                        today = board.days[today_num]
                        best_labs_on_day = {} # map from eligible labs found to the attending and total points in that lab (on that day)
                        for s in range(len(today.slots)):
                            check_slot = today.slots[s]
                            if next_case.getAttending() is None:
                                for possible_attend_match in check_slot.getAttendings():
                                    if next_case.procedure in possible_attend_match.getProcedures() and next_case.procedure in check_slot.lab.getDayParams()[today.getWeekdayName()]['procedures']:
                                        # there is an eligible slot (type & attending) in this lab on this day, so add this day/lab pair as an option and append specified points from its slot
                                        if check_slot.lab in best_labs_on_day:
                                            best_labs_on_day[check_slot.lab]['points'] += check_slot.getCase().getCaseAttrPoints(added_slot_attr)
                                        else:
                                            point_attend_dict = {}
                                            point_attend_dict['attending'] = possible_attend_match
                                            point_attend_dict['points'] = check_slot.getCase().getCaseAttrPoints(added_slot_attr)
                                            best_labs_on_day[check_slot.lab] = point_attend_dict
                                        break
                            else:
                                for possible_attend_match in check_slot.getAttendings():
                                    if next_case.getAttending() == possible_attend_match.getName():
                                        if check_slot.lab in best_labs_on_day:
                                            best_labs_on_day[check_slot.lab]['points'] += check_slot.getCase().getCaseAttrPoints(added_slot_attr)
                                        else:
                                            point_attend_dict = {}
                                            point_attend_dict['attending'] = possible_attend_match
                                            point_attend_dict['points'] = check_slot.getCase().getCaseAttrPoints(added_slot_attr)
                                            best_labs_on_day[check_slot.lab] = point_attend_dict
                                        break
                            if next_case.checkScheduled():
                                break
                        if len(best_labs_on_day) != 0:
                            # selects the lab where there are the fewest total points of the specified attribute (or overall) as the best, regardless of number of cases
                            current_best_lab = list(best_labs_on_day.keys())[0]
                            current_best_points = best_labs_on_day[current_best_lab]['points']
                            current_best_attend = best_labs_on_day[current_best_lab]['attending']
                            for bl in best_labs_on_day:
                                if best_labs_on_day[bl]['points'] < current_best_points:
                                    current_best_points = best_labs_on_day[bl]['points']
                                    current_best_lab = bl
                                    current_best_attend = best_labs_on_day[bl]['attending']
                            if best_new_points is None or current_best_points < best_new_points:
                                best_new_points = current_best_points
                                best_new_lab = current_best_lab
                                best_new_day = today_num
                                best_new_attend = current_best_attend
                    if best_new_day is not None and best_new_points is not None and best_new_attend is not None and best_new_lab is not None:
                        best_new_date = board.board_start_date + datetime.timedelta(days=int(best_new_day))
                        new_slot = Slot(best_new_date, best_new_lab, [best_new_attend], 0)
                        board.days[best_new_day].slots.append(new_slot)
                        new_slot.scheduleCase(next_case, best_new_attend)
                        next_case.setScheduled()
                        print('Added slot to schedule case '+str(k)+' in '+check_slot.getLab()+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+best_new_attend.getName()+'.')
                        print(next_case.getAttributes())
                        if new_slot.date >= board.start_date and new_slot.date <= board.end_date:
                            board.addToCountMetricAttr('added_slot_scheduled_cases', 1)
                            board.addDayOfWeekScheduling(new_slot, 1)
                    else:
                        print('Case ' +str(k)+' still not scheduled.') # no eligible labs at all within time window
                        print(next_case.getAttributes())
                        board.addToCountMetricAttr('unscheduled_overall_cases', 1)
                else:
                    for ranked_att in board.point_imp_order:
                        if len(backup_options[ranked_att]) > 0 and not next_case.checkScheduled():
                            first_day = backup_option_days[ranked_att][0]
                            slot = backup_options[ranked_att][first_day][0]
                            attend = backup_options[ranked_att][first_day][1]
                            will_be_violated_attrs = []
                            for aa in board.active_attrs:
                                if not board.days[first_day].checkAttributePoints(slot.getLab(), next_case, aa+'_points', next_case.getCaseActiveAttrs()[aa+'_points']):
                                    will_be_violated_attrs.append(aa)
                            slot.scheduleCase(next_case, attend)
                            next_case.setScheduled()
                            print('Forcibly scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+attend.getName()+'.')
                            print(next_case.getAttributes())
                            if slot.date >= board.start_date and slot.date <= board.end_date:
                                board.addDayOfWeekScheduling(slot, 1)
                                for violated_cat in will_be_violated_attrs:
                                    board.addToCountMetricAttr('forcibly_scheduled_over_'+violated_cat+'_cases', 1)
                                board.addToCountMetricAttr('total_forcibly_scheduled_cases', 1)
                    if not next_case.checkScheduled():
                        first_day = backup_option_days['over_all'][0]
                        slot = backup_options['over_all'][first_day][0]
                        attend = backup_options['over_all'][first_day][1]
                        slot.scheduleCase(next_case, attend)
                        next_case.setScheduled()
                        print('Forcibly scheduled case '+str(k)+' on '+board.dow_list[slot.date.weekday()]+' '+str(slot.date)+' and assigned attending, '+attend.getName()+'.')
                        print(next_case.getAttributes())
                        if slot.date >= board.start_date and slot.date <= board.end_date:
                            board.addDayOfWeekScheduling(slot, 1)
                            board.addToCountMetricAttr('forcibly_scheduled_over_all_cases', 1)
                            board.addToCountMetricAttr('total_forcibly_scheduled_cases', 1)
        return board