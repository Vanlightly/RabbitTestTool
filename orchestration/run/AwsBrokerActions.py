import sys
import io
import subprocess
import threading
import time
import uuid
import os.path
import requests
import json
from random import randint
from BrokerActions import BrokerActions
from UniqueConfiguration import UniqueConfiguration
from CommonConfiguration import CommonConfiguration
from printer import console_out

class AwsBrokerActions(BrokerActions):
    def __init__(self, deployer):
        super().__init__(deployer)

    def restart_broker(self, technology, node, common_conf):
        status_id = technology + node
        exit_code = subprocess.call(["bash", "restart-broker.sh",
                                     common_conf.key_pair,
                                     node,
                                     common_conf.run_tag,
                                     technology])
        
        if exit_code != 0:
            console_out(self.actor, f"Restart of broker on node {node} failed with exit code {exit_code}")
            self._action_status[status_id] = "failed"   
        else:
            self._action_status[status_id] = "success"
