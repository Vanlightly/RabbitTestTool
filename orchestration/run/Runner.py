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
        raise NotImplementedError

    def run_background_load(self, unique_conf, common_conf):
        raise NotImplementedError
