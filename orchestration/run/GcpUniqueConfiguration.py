from UniqueConfiguration import UniqueConfiguration
from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class GcpUniqueConfiguration(UniqueConfiguration):
    def __init__(self, args, suffix):
        super().__init__(args, suffix)

        self.container_image = get_mandatory_arg(args, "--container-image", self.suffix)
        self.container_env = get_optional_arg(args, "--container-env", self.suffix, "")
        self.machine_type = get_mandatory_arg(args, "--machine-type", self.suffix)
        self.volume = get_mandatory_arg_validated(args, "--volume", self.suffix, ["pd-ssd", "standard"])
