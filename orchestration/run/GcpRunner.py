import subprocess

from Runner import Runner
from printer import console_out


class GcpRunner(Runner):
    def __init__(self):
        super().__init__()

    def run_benchmark(self, unique_conf, common_conf, playlist_entry, policies, run_ordinal):
        status_id = unique_conf.technology + unique_conf.node_number

        first_node_number = int(unique_conf.node_number)
        cluster_size = int(unique_conf.cluster_size)
        hosts = [f"{common_conf.run_tag}-rmq{i}-server.c.{common_conf.gcp_project_id}.internal" for i in
                 range(first_node_number, first_node_number + cluster_size)]
        nodes = ",".join([f"rabbit@{host}" for host in hosts])
        broker_hosts = ",".join([f"{host}:5672" for host in hosts])

        command_args = ["gcloud", "compute", "ssh",
                        f"{common_conf.run_tag}-{unique_conf.config_tag}-loadgen",
                        "--",
                        "docker exec $(docker container ls | awk '/rabbittesttool/ { print $1 }') "
                        f"java -Xms1024m -Xmx8192m -jar rabbittesttool.jar "
                        f"--mode {common_conf.mode} "
                        f"--topology \"./topologies/{playlist_entry.topology}\" "
                        f"--policies \"./policies/{policies}\" "
                        f"--run-id {common_conf.run_id} "
                        f"--run-tag {common_conf.run_tag} "
                        f"--technology {unique_conf.technology} "
                        f"--nodes {nodes} "
                        f"--version {unique_conf.broker_version} "
                        f"--instance {unique_conf.machine_type} "
                        f"--volume {unique_conf.volume} "
                        f"--filesystem {unique_conf.filesystem} "
                        f"--hosting {common_conf.hosting} "
                        f"--tenancy {unique_conf.tenancy} "
                        f"--core-count {unique_conf.core_count} "
                        f"--threads-per-core {unique_conf.threads_per_core} "
                        f"--no-tcp-delay {unique_conf.no_tcp_delay} "
                        f"--config-tag {unique_conf.config_tag} "
                        f"--broker-hosts {broker_hosts} "
                        f"--broker-mgmt-port 15672 "
                        f"--broker-user {common_conf.username} "
                        f"--broker-password {common_conf.password} "
                        f"--postgres-jdbc-url \"{common_conf.postgres_url}\" "
                        f"--postgres-user \"{common_conf.postgres_user}\" "
                        f"--postgres-pwd \"{common_conf.postgres_pwd}\" "
                        f"--override-step-seconds {common_conf.override_step_seconds} "
                        f"--override-step-repeat {common_conf.override_step_repeat} "
                        f"--override-step-msg-limit {common_conf.override_step_msg_limit} "
                        f"--pub-connect-to-node {unique_conf.pub_connect_to_node} "
                        f"--con-connect-to-node {unique_conf.con_connect_to_node} "
                        f"--grace-period-sec {playlist_entry.grace_period_sec} "
                        f"--run-ordinal {run_ordinal} "
                        f"--benchmark-tags {common_conf.tags} "
                        f"{playlist_entry.get_topology_variables()} "
                        f"{playlist_entry.get_policy_variables()}"]

        exit_code = subprocess.call(command_args)

        if exit_code != 0:
            console_out(self.actor, f"Benchmark {unique_conf.node_number} failed")
            self._benchmark_status[status_id] = "failed"
        else:
            self._benchmark_status[status_id] = "success"

    def run_background_load(self, unique_conf, common_conf):
        console_out(self.actor, f"Starting background load for {unique_conf.node_number}")
        status_id = unique_conf.technology + unique_conf.node_number
        self._benchmark_status[status_id] = "started"

        first_node_number = int(unique_conf.node_number)
        cluster_size = int(unique_conf.cluster_size)
        hosts = [f"{common_conf.run_tag}-rmq{i}-server.c.{common_conf.gcp_project_id}.internal" for i in
                 range(first_node_number, first_node_number + cluster_size)]
        nodes = ",".join([f"rabbit@{host}" for host in hosts])
        broker_hosts = ",".join([f"{host}:5672" for host in hosts])

        command_args = ["gcloud", "compute", "ssh",
                        f"{common_conf.run_tag}-{unique_conf.config_tag}-loadgen",
                        "--",
                        "docker exec $(docker container ls | awk '/rabbittesttool/ { print $1 }') "
                        f"java -Xms1024m -Xmx8192m -jar rabbittesttool.jar "
                        f"--mode benchmark "
                        f"--topology \"./topologies/{common_conf.background_topology_file}\" "
                        f"--policies \"./policies/{common_conf.background_policies_file}\" "
                        f"--technology {unique_conf.technology} "
                        f"--nodes {nodes} "
                        f"--version {unique_conf.broker_version} "
                        f"--broker-hosts {broker_hosts} "
                        f"--broker-mgmt-port 15672 "
                        f"--broker-user {common_conf.username} "
                        f"--broker-password {common_conf.password} "
                        f"--broker-vhost benchmark "
                        f"--override-step-seconds {common_conf.background_step_seconds} "
                        f"--override-step-repeat {common_conf.background_step_repeat}"]

        subprocess.call(command_args)