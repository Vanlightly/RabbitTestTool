import uuid
from CommonConfiguration import CommonConfiguration
from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class GcpCommonConfiguration(CommonConfiguration):

    def __init__(self, args):
        super().__init__(args)

        self.gcp_project_id = get_mandatory_arg(args, "--gcp-project-id", "")
        self.network = get_optional_arg(args, "--network", "", "")
        self.subnet = get_optional_arg(args, "--subnet", "", "")
        self.gcp_postgres_connection_name = get_optional_arg(args, "--gcp-postgres-connection-name", "", "")
        self.loadgen_machine_type = get_mandatory_arg(args, "--loadgen-machine-type", "")
        self.loadgen_container_image = get_mandatory_arg(args, "--loadgen-container-image", "")
        self.hosting = "gcp"
