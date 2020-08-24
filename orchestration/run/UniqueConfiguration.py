from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class UniqueConfiguration:
    def __init__(self, args, suffix):
        self.suffix = suffix
        self.config_tag = get_mandatory_arg(args, "--config-tag", self.suffix)
        self.technology = get_mandatory_arg_validated(args, "--technology", self.suffix, ["rabbitmq"])
        self.cluster_size = int(get_optional_arg(args, "--cluster-size", self.suffix, "1"))
        self.broker_version = get_mandatory_arg(args, "--version", self.suffix)
        self.volume_size = get_optional_arg(args, "--volume-size", self.suffix, "50") # for GCP deployment only
        self.filesystem = get_mandatory_arg_validated(args, "--filesystem", self.suffix, ["ext4", "xfs"])
        self.tenancy = get_mandatory_arg_validated(args, "--tenancy", self.suffix, ["default","dedicated"])
        self.core_count = get_mandatory_arg(args, "--core-count", self.suffix)
        self.threads_per_core = get_mandatory_arg(args, "--threads-per-core", self.suffix)
        self.memory_gb = get_mandatory_arg(args, "--memory-gb", self.suffix)
        self.vars_file = get_optional_arg(args, "--vars-file", self.suffix, f".variables/{self.technology}-generic-vars.yml")
        self.no_tcp_delay = get_optional_arg(args, "--no-tcp-delay", self.suffix, "true")
        self.policies_file = get_optional_arg(args, "--policies-file", self.suffix, "none")
        self.pub_connect_to_node = get_optional_arg_validated(args, "--pub-connect-to-node", self.suffix, ["roundrobin", "local", "non-local", "random"], "roundrobin")
        self.con_connect_to_node = get_optional_arg_validated(args, "--con-connect-to-node", self.suffix, ["roundrobin", "local", "non-local", "random"], "roundrobin")
        self.deployment = get_optional_arg_validated(args, "--deployment", self.suffix, ["ec2", "eks", "gke"], "ec2")
        self.node_number = -1
                
    def set_node_number(self, node_number):
        self.node_number = node_number