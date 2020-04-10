from UniqueConfiguration import UniqueConfiguration
from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class AwsUniqueConfiguration(UniqueConfiguration):
    def __init__(self, args, suffix):
        super().__init__(args, suffix)

        self.generic_unix_url = get_mandatory_arg(args, "--generic-unix-url", self.suffix)
        self.instance = get_mandatory_arg(args, "--instance", self.suffix)
        self.volume = get_mandatory_arg_validated(args, "--volume-type", self.suffix, ["ebs-io1","ebs-st1","ebs-gp2","local-nvme"])
