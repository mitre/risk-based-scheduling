class Scheduler:
    def __init__(self, board, case_list, algorithm):
        """
        Constructor class that instantiates Scheduler.
        """
        self.board = board
        self.case_list = case_list
        self.algorithm = algorithm


    def getBoard(self):
        """
        Returns the schedule's board. 
        """
        return self.board

    def populateBoard(self):
        """
        Schedules cases from case_list onto the board.
        """
        return self.algorithm.scheduleCases(self.board, self.case_list)

        

    