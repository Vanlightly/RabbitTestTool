import subprocess

from BrokerActions import BrokerActions
from printer import console_out


class GcpBrokerActions(BrokerActions):
    def __init__(self, deployer):
        super().__init__(deployer)

    def restart_broker(self, technology, node, common_conf):
        status_id = technology + node

        node_name = f"{common_conf.run_tag}-rmq{node}-server"

        command_args = ["gcloud", "compute", "ssh",
                        node_name,
                        "--",
                        "docker exec $(docker container ls | awk '/rabbitmq/ { print $1 }') rabbitmqctl -l stop_app"]
        result = subprocess.run(command_args)

        if result.returncode != 0:
            console_out(self.actor, f"Restart (1/2) of broker on node {node} failed with exit code {result.returncode}")
            self._action_status[status_id] = "failed"
            return

        command_args = ["gcloud", "compute", "ssh",
                        node_name,
                        "--",
                        "docker restart --time 30 $(docker container ls | awk '/rabbitmq/ { print $1 }')"]
        result = subprocess.run(command_args)

        if result.returncode != 0:
            console_out(self.actor, f"Restart (2/2) of broker on node {node} failed with exit code {result.returncode}")
            self._action_status[status_id] = "failed"
            return

        self._action_status[status_id] = "success"
