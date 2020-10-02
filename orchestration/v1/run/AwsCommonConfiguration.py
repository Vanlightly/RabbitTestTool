import uuid
from CommonConfiguration import CommonConfiguration
from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class AwsCommonConfiguration(CommonConfiguration):

    def __init__(self, args):
        super().__init__(args)

        self.ami = get_mandatory_arg(args, "--ami", "")
        self.arm_ami = get_mandatory_arg(args, "--arm-ami", "")
        self.broker_sg = get_mandatory_arg(args, "--broker-sg", "")
        self.loadgen_sg = get_mandatory_arg(args, "--loadgen-sg", "")
        self.loadgen_instance = get_mandatory_arg(args, "--loadgen-instance", "")
        self.subnet = get_mandatory_arg(args, "--subnet", "")
        self.key_pair = get_mandatory_arg(args, "--keypair", "")
        self.hosting = "aws"
