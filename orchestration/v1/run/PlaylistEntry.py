class PlaylistEntry:
    def __init__(self):
        self.topology = ""
        self.topology_variables = dict()
        self.policy = ""
        self.policy_variables = dict()  
        self.has_broker_actions = False
        self.broker_action = ""
        self.trigger_type = ""
        self.trigger_at = 0
        self.grace_period_sec = 0
        self.bg_topology = ""
        self.bg_policy = ""
        self.bg_step_seconds = 0
        self.bg_step_repeat = 0
        self.bg_delay_seconds = 0

    def get_topology_variables(self):
        if len(self.topology_variables) == 0:
            return ""

        result = ""
        for key, value in self.topology_variables.items():
            result += f"--tvar.{key} {value} "

        return result

    def get_policy_variables(self):
        if len(self.policy_variables) == 0:
            return ""
        
        result = ""
        for key, value in self.policy_variables.items():
            result += f"--pvar.{key} {value} "

        return result