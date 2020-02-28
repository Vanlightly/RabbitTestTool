import subprocess
import json
import re
import subprocess
import time

from Deployer import Deployer
from printer import console_out


class GcpDeployer(Deployer):
    def __init__(self):
        super().__init__()

    def update_single(self, unique_conf, common_conf):
        status_id = unique_conf.technology + unique_conf.node_number

        node_name = f"{common_conf.run_tag}-{unique_conf.config_tag}-loadgen"

        console_out(self.actor, f"Updating node {node_name}...")

        command_args = ["gcloud", "compute", "instances", "update-container",
                        node_name,
                        f"--container-image='{common_conf.loadgen_container_image}'"]

        exit_code = subprocess.call(command_args)

        if exit_code != 0:
            console_out(self.actor, f"deploy {node_name} failed with exit code {exit_code}")
            self._deploy_status[status_id] = "failed"
            return

        console_out(self.actor, f"Waiting 1 minute to let the loadgen instance start...")
        time.sleep(60)

        self._deploy_status[status_id] = "success"

    def deploy_single(self, unique_conf, common_conf):
        self.deploy_rabbitmq_cluster(unique_conf, common_conf)

    def deploy_rabbitmq_cluster(self, unique_conf, common_conf):
        if re.match(r'^\d', common_conf.run_tag):
            common_conf.run_tag = "rtt-" + common_conf.run_tag

        status_id = unique_conf.technology + unique_conf.node_number
        self._deploy_status[status_id] = "started"

        first_node_number = int(unique_conf.node_number)
        cluster_size = int(unique_conf.cluster_size)

        try:
            # deploy unclustered nodes
            for node in range(first_node_number, first_node_number + cluster_size):
                node_name = f"{common_conf.run_tag}-rmq{node}-server"
                if self.__node_exists(node_name):
                    console_out(self.actor, f"{node_name} already exists, skipping deploy")
                    continue

                self.__deploy_rmq_node(node_name, common_conf, unique_conf)
                self.__wait_rabbitmq_ready(node_name)
                self.__setup_cadvisor(node_name)

            # form the cluster
            master_node = f"rabbit@{common_conf.run_tag}-rmq{first_node_number}-server.c.{common_conf.gcp_project_id}.internal"
            for node in range(first_node_number + 1, first_node_number + cluster_size):
                node_name = f"{common_conf.run_tag}-rmq{node}-server"
                for command in ["stop_app", "reset", f"join_cluster {master_node}", "start_app"]:
                    command_args = ["gcloud", "compute", "ssh",
                                    node_name,
                                    "--",
                                    "docker exec $(docker container ls | awk '/rabbitmq/ { print $1 }') "
                                    "rabbitmqctl -l " + command]
                    exit_code = subprocess.call(command_args)
                    if exit_code != 0:
                        raise Exception(f"rabbitmqctl {command} on {node_name} failed with exit code {exit_code}")

            # name the cluster
            command_args = ["gcloud", "compute", "ssh",
                            node_name,
                            "--",
                            "docker exec $(docker container ls | awk '/rabbitmq/ { print $1 }') "
                            f"rabbitmqctl -l -q set_cluster_name {common_conf.run_tag}-{unique_conf.config_tag}"]
            exit_code = subprocess.call(command_args)
            if exit_code != 0:
                raise Exception(f"'{command_args[-1]}' on {node_name} failed with exit code {exit_code}")

            # enable prometheus
            for node in range(first_node_number, first_node_number + cluster_size):
                node_name = f"{common_conf.run_tag}-rmq{node}-server"
                command_args = ["gcloud", "compute", "ssh",
                                node_name,
                                "--",
                                "docker exec $(docker container ls | awk '/rabbitmq/ { print $1 }') "
                                "rabbitmq-plugins enable rabbitmq_prometheus"]
                exit_code = subprocess.call(command_args)
                if exit_code != 0:
                    raise Exception(f"'rabbitmq-plugins enable rabbitmq_prometheus' on {node_name} failed with exit code {exit_code}")

            # deploy benchmark
            node_name = f"{common_conf.run_tag}-{unique_conf.config_tag}-loadgen"
            if not self.__node_exists(node_name):
                self.__deploy_loadgen_node(node_name, common_conf)
                self.__setup_cadvisor(node_name)
            else:
                console_out(self.actor, f"{node_name} already exists, skipping deploy")
            self._deploy_status[status_id] = "success"

        except Exception as e:
            console_out(self.actor, e)
            self._deploy_status[status_id] = "failed"

    def teardown(self, technology, node, run_tag, no_destroy):
        if no_destroy:
            console_out(self.actor, "No teardown as --no-destroy set to true")
        else:
            try:
                self.__delete_instance(f"{run_tag}-rmq{node}-server")
            except Exception as e:
                console_out(self.actor, f"{e}, ignoring")

    def teardown_loadgen(self, unique_conf, common_conf, no_destroy):
        if no_destroy:
            console_out(self.actor, "No teardown as --no-destroy set to true")
        else:
            try:
                self.__delete_instance(f"{common_conf.run_tag}-{unique_conf.config_tag}-loadgen")
            except Exception as e:
                console_out(self.actor, f"{e}, ignoring")

    def get_logs(self, common_conf, start_node, end_node):
        # https://cloud.google.com/compute/docs/containers/deploying-containers#viewing_container_logs
        raise NotImplementedError

    def __delete_instance(self, name, attempts = 5):
        command_args = ["gcloud", "compute", "instances", "delete",
                        name,
                        "--quiet"]
        exit_code = subprocess.call(command_args)
        if exit_code == 0:
            return
        if attempts - 1 == 0:
            raise Exception(f"teardown of {name} failed after 5 attempts")
        console_out(self.actor, "teardown failed, will retry in 1 minute")
        time.sleep(60)
        self.__delete_instance(name, attempts - 1)

    def __node_exists(self, node_name):
        search_args = ["gcloud", "compute", "instances", "list",
                       f"--filter=name:{node_name}",
                       "--format=json"]
        output = subprocess.run(search_args, capture_output=True)

        if output.returncode != 0:
            raise Exception(f"check for existing {node_name} failed with exit code {output.returncode}")

        return json.loads(output.stdout) != []

    def __wait_rabbitmq_ready(self, node_name, attempts = 10):
        ping_args = ["gcloud", "compute", "ssh",
                     node_name,
                     "--",
                     "docker exec $(docker container ls | awk '/rabbitmq/ { print $1 }') rabbitmq-diagnostics ping -q"]
        result = subprocess.run(ping_args)
        if result.returncode == 0:
            return
        if attempts - 1 == 0:
            raise Exception(f"timed out waiting for {node_name} to be ready")

        console_out(self.actor, f"{node_name} did not respond to ping, will retry in 30s")
        time.sleep(30)
        self.__wait_rabbitmq_ready(node_name, attempts - 1)

    def __deploy_rmq_node(self, node_name, common_conf, unique_conf):
        console_out(self.actor, f"Deploying node {node_name}...")

        disk_name = f"{node_name}-persistent"

        create_disk_args = f"name={disk_name},"
        f"size={unique_conf.volume_size}GB,"
        f"type={unique_conf.volume},"
        f"auto-delete=yes"

        labels = ",".join([f"deployment=rtt-benchmark",
                           f"rtt-role=broker",
                           f"rtt-run-tag={common_conf.run_tag}",
                           f"rtt-config-tag={unique_conf.config_tag}"])

        command_args = ["gcloud", "compute", "instances", "create-with-container",
                        node_name,
                        f"--boot-disk-type=pd-ssd",
                        f"--labels={labels}",
                        f"--tags=rtt-benchmark",
                        f"--container-stdin",
                        f"--container-tty",
                        f"--machine-type={unique_conf.machine_type}",
                        f"--create-disk={create_disk_args}",
                        f"--container-mount-disk=name={disk_name},mount-path=/var/lib/rabbitmq",
                        f"--container-env=RABBITMQ_ERLANG_COOKIE={common_conf.run_tag}",
                        f"--container-env=RABBITMQ_DEFAULT_USER={common_conf.username}",
                        f"--container-env=RABBITMQ_DEFAULT_PASS={common_conf.password}",
                        f"--container-env=RABBITMQ_NODENAME=rabbit@{node_name}.c.{common_conf.gcp_project_id}.internal",
                        f"--container-env=RABBITMQ_USE_LONGNAME=true",
                        f"--container-image={unique_conf.container_image}"]

        if unique_conf.container_env:
            command_args.append(f"--container-env={unique_conf.container_env}")

        if common_conf.network:
            command_args.append(f"--network={common_conf.network}")
        if common_conf.subnet:
            command_args.append(f"--subnet={common_conf.subnet}")

        exit_code = subprocess.call(command_args)

        if exit_code != 0:
            raise Exception(f"deploy {node_name} failed with exit code {exit_code}")

    def __setup_cadvisor(self, node_name):
        command_args = ["gcloud", "compute", "ssh",
                        node_name,
                        "--",
                        "docker run "
                        "--volume=/:/rootfs:ro "
                        "--volume=/var/run:/var/run:ro "
                        "--volume=/sys:/sys:ro "
                        "--volume=/var/lib/docker/:/var/lib/docker:ro "
                        "--volume=/dev/disk/:/dev/disk:ro "
                        "--publish=8080:8080 "
                        "--detach=true "
                        "--name=cadvisor "
                        "gcr.io/google-containers/cadvisor:latest"]
        exit_code = subprocess.call(command_args)
        if exit_code != 0:
            raise Exception(f"prometheus node_exporter on {node_name} failed with exit code {exit_code}")

    def __deploy_loadgen_node(self, node_name, common_conf):
        console_out(self.actor, f"Deploying node {node_name}...")

        default_scopes = ["https://www.googleapis.com/auth/devstorage.read_only",
                          "https://www.googleapis.com/auth/logging.write",
                          "https://www.googleapis.com/auth/monitoring.write",
                          "https://www.googleapis.com/auth/pubsub",
                          "https://www.googleapis.com/auth/service.management.readonly",
                          "https://www.googleapis.com/auth/servicecontrol",
                          "https://www.googleapis.com/auth/trace.append"]

        sql_proxy_scopes = ["https://www.googleapis.com/auth/sqlservice.admin",
                            "https://www.googleapis.com/auth/devstorage.read_write"]

        scopes = ",".join(default_scopes + sql_proxy_scopes)

        labels = ",".join([f"deployment=rtt-benchmark",
                           f"rtt-role=loadgen",
                           f"rtt-run-tag={common_conf.run_tag}"])

        command_args = ["gcloud", "compute", "instances", "create-with-container",
                        node_name,
                        f"--boot-disk-type=pd-ssd",
                        f"--labels={labels}",
                        f"--tags=rtt-benchmark",
                        f"--container-stdin",
                        f"--container-tty",
                        f"--scopes={scopes}",
                        f"--machine-type={common_conf.loadgen_machine_type}",
                        f"--container-image={common_conf.loadgen_container_image}"]

        if common_conf.network:
            command_args.append(f"--network={common_conf.network}")
        if common_conf.subnet:
            command_args.append(f"--subnet={common_conf.subnet}")

        exit_code = subprocess.call(command_args)

        if exit_code != 0:
            raise Exception(f"deploy {node_name} failed with exit code {exit_code}")

        console_out(self.actor, f"Waiting 1 minute to let the loadgen instance start...")
        time.sleep(60)

        if common_conf.gcp_postgres_connection_name:
            console_out(self.actor, f"Starting cloud_sql_proxy on the loadgen instance...")
            proxy_args = ["gcloud", "compute", "ssh",
                          node_name,
                          "--",
                          "docker run -d "
                          "-p 127.0.0.1:5432:5432 "
                          "gcr.io/cloudsql-docker/gce-proxy:1.16 "
                          "/cloud_sql_proxy "
                          f"-instances={common_conf.gcp_postgres_connection_name}=tcp:0.0.0.0:5432"]
            subprocess.call(proxy_args)
            if exit_code != 0:
                raise Exception(f"cloud_sql_proxy on {node_name} failed with exit code {exit_code}")
