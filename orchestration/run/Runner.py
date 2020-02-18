import sys
import subprocess
import threading
import time
import uuid
import os.path
from random import randint
from collections import namedtuple
from printer import console_out

class Runner:
    def __init__(self):
        self._benchmark_status = dict()
        self.actor = "RUNNER"

    def get_benchmark_statuses(self):
        return self._benchmark_status

    def get_benchmark_status(self, status_id):
        return self._benchmark_status[status_id]
    
    def run_benchmark(self, unique_conf, common_conf, playlist_entry, policies, run_ordinal):
        raise NotImplementedError

    def run_background_load_across_runs(self, configurations, common_conf):
        bg_threads = list()

        for config_tag in configurations:
            unique_conf_list = configurations[config_tag]
            
            for p in range(common_conf.parallel_count):
                unique_conf = unique_conf_list[p]
                t1 = threading.Thread(target=self.run_background_load, args=(unique_conf, common_conf,))
                bg_threads.append(t1)

        for bt in bg_threads:
            bt.start()

        time.sleep(10)
        console_out(self.actor, f"Delaying start of benchmark by {common_conf.background_delay} seconds")
        time.sleep(common_conf.background_delay)

    def run_background_load(self, unique_conf, common_conf):
        raise NotImplementedError
