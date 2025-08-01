from slot import Slot
from day import Day
import numpy as np
import datetime
import json
import os
import mlflow
DAYS_PER_WEEK = 7

class Board:
    def __init__(self, start_date, end_date, case_arrivals, weekday_configs, labs_open, point_ignore, active_attrs, point_imp_order, added_slot_attr):
        """
        Constructor class that instantiates Board.
        """
        self.start_date = start_date
        self.end_date = end_date
        self.case_arrivals = case_arrivals
        self.weekday_configs = weekday_configs
        self.labs_open = labs_open
        self.point_ignore = point_ignore
        self.active_attrs = active_attrs
        self.point_imp_order = point_imp_order
        self.added_slot_attr = added_slot_attr
        self.scheduling_counts_dict = {'unscheduled_overall_cases': 0, 'added_slot_scheduled_cases': 0, 'total_forcibly_scheduled_cases': 0}
        for cat in active_attrs:
            self.scheduling_counts_dict['forcibly_scheduled_over_'+cat+'_cases'] = 0
        if len(active_attrs) > 1:
            self.scheduling_counts_dict['forcibly_scheduled_over_all_cases'] = 0
        
        self.warmup_days = self.case_arrivals['warmup_weeks'] * DAYS_PER_WEEK
        self.board_start_date = self.start_date - datetime.timedelta(self.warmup_days)
        board_end_date = self.end_date + datetime.timedelta((self.case_arrivals['time_window']) * DAYS_PER_WEEK)
        n_days = (board_end_date - self.board_start_date).days + 1
        self.dow_list = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
        self.sched_dist = dict(zip(self.dow_list, list(np.zeros(7))))

        board = np.empty(n_days, dtype=Day)

        for i in range(n_days):
            date = self.board_start_date+datetime.timedelta(days=i)
            dow_key = date.weekday()
            dow = self.dow_list[dow_key]
            weekdayConfig = self.weekday_configs[dow_key]
            slots = []
            board[i] = Day(date,weekdayConfig,slots)
            available_attendings = weekdayConfig.getAttendings()
            for lab in weekdayConfig.labs:
                lab_attending_names = lab.getDayParams()[dow]['attendings']
                attending_matches = []
                for attending_obj in available_attendings:
                    if attending_obj.name in lab_attending_names:
                        attending_matches.append(attending_obj)
                for j in range(lab.getDayParams()[dow]['n_daily_cases']):
                    slot = Slot(date, lab, attending_matches)
                    board[i].slots.append(slot)
        self.days = board

    def exportScheduleJSON(self, json_file, schedule_name, i):
        """
        Exports a json of the schedule for use in the Risk-Based Simulation.
        Also logs the schedule to the mlflow child run.
        """
        json_dir = os.path.join('json schedule files', schedule_name+'_json_files')
        if not os.path.exists(json_dir):
            os.makedirs(json_dir)
        lab_map = dict(zip(self.labs_open, [int(i) for i in np.arange(0, len(self.labs_open))]))
        schedule = []
        for day in self.days:
            if day.isScheduleDay(self.start_date, self.end_date):
                for slot in day.slots:
                    if slot.getCase() is not None:
                        slot_dict = {'day':int(day.getSimDay(self.start_date)),
                                     'lab':lab_map[slot.lab.name]}
                        slot_dict.update(slot.getCase().getAttributes())
                        schedule.append(slot_dict)
        mlflow.log_dict(schedule, json_file)
        fname, fext = os.path.splitext(json_file)
        with open(os.path.join(json_dir, fname+'_child_'+str(i)+fext),'w') as f:
            json.dump(schedule,f)

    def getCountMetrics(self):
        """
        Returns a dictionary of all scheduling counts (excluding the counts for each day of the week).
        """
        return self.scheduling_counts_dict

    def addToCountMetricAttr(self, cat, amt):
        """
        Adds amt to the count of category cat, which corresponds to either an active point attribute or a standard count category.
        """
        if cat in self.scheduling_counts_dict:
            prior = self.scheduling_counts_dict[cat]
            self.scheduling_counts_dict[cat] = prior + amt
        else:
            raise Exception("Tried to add to a scheduling count that doesn't exist.")
        
    def addDayOfWeekScheduling(self, slot, amt):
        """
        Adds amt number of cases scheduled to some day of the week.
        """
        self.sched_dist[self.dow_list[slot.getWeekday()]] = self.sched_dist[self.dow_list[slot.getWeekday()]] + amt

    def getScheduleDist(self, dow):
        """
        Returns the number of cases scheduled to the day of the week passed,
        which should always be spelled out and capitalized.
        """
        return self.sched_dist[dow]
    
    def getPointIgnore(self):
        """
        Returns the list of procedure types to ignore for point system scheduling.
        """
        return self.point_ignore
    
    def reorderCasesWithinDays(self, what):
        """
        Reorders cases, based on the passed parameter, which have already been scheduled on the board,
        alternating between decreasing order and increasing order in each successive lab
        """
        for d in range(len(self.days)):
            today = self.days[d]
            for n, l in enumerate(today.getLabs()):
                slots = []
                cases = []
                for s in today.slots:
                    if s.getLab() == l.name and s.getCase() is not None:
                        slots.append(s)
                        cases.append(s.getCase())
                if len(slots) > 1:
                    if n%2 == 0:
                        sorted_cases = sorted(cases, key=lambda x: x.getAttributes()[what], reverse=True) # descending
                    else: # n%2 == 1
                        sorted_cases = sorted(cases, key=lambda x: x.getAttributes()[what]) # ascending
                    for i, slot in enumerate(slots):
                        slot.scheduleCase(sorted_cases[i])