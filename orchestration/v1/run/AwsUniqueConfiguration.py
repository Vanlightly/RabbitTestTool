from UniqueConfiguration import UniqueConfiguration
from command_args import get_args, get_optional_arg, get_mandatory_arg, get_mandatory_arg_no_print, is_true, get_mandatory_arg_validated, get_optional_arg_validated

class AwsUniqueConfiguration(UniqueConfiguration):
    def __init__(self, args, suffix):
        super().__init__(args, suffix)

        self.generic_unix_url = get_optional_arg(args, "--generic-unix-url", self.suffix, "must-be-using-eks")
        self.instance = get_mandatory_arg(args, "--instance", self.suffix)
        self.volume1_iops_per_gb = get_optional_arg(args, "--volume1-iops-per-gb", self.suffix, "50") # only applicable to io1, else ignored
        self.volume2_iops_per_gb = get_optional_arg(args, "--volume2-iops-per-gb", self.suffix, "50") # only applicable to io1, else ignored
        self.volume3_iops_per_gb = get_optional_arg(args, "--volume3-iops-per-gb", self.suffix, "50") # only applicable to io1, else ignored
        self.volume1_size = get_optional_arg(args, "--volume1-size", self.suffix, "50") 
        self.volume2_size = get_optional_arg(args, "--volume2-size", self.suffix, "0") 
        self.volume3_size = get_optional_arg(args, "--volume3-size", self.suffix, "0") 
        self.volume1_type = get_optional_arg_validated(args, "--volume1-type", self.suffix, ["ebs-io1","ebs-st1","ebs-sc1","ebs-gp2","local-nvme","pd-ssd"], "ebs-gp2") 
        self.volume2_type = get_optional_arg_validated(args, "--volume2-type", self.suffix, ["ebs-io1","ebs-st1","ebs-sc1","ebs-gp2","local-nvme","pd-ssd"], "ebs-gp2") 
        self.volume3_type = get_optional_arg_validated(args, "--volume3-type", self.suffix, ["ebs-io1","ebs-st1","ebs-sc1","ebs-gp2","local-nvme","pd-ssd"], "ebs-gp2") 
        self.volume1_mountpoint = get_optional_arg(args, "--volume1-mountpoint", self.suffix, "/volume1") 
        self.volume2_mountpoint = get_optional_arg(args, "--volume2-mountpoint", self.suffix, "/volume2") 
        self.volume3_mountpoint = get_optional_arg(args, "--volume3-mountpoint", self.suffix, "/volume3") 
        self.data_volume = get_optional_arg(args, "--data-volume", self.suffix, "volume1")   
        self.logs_volume = get_optional_arg(args, "--logs-volume", self.suffix, "volume1")   
        self.quorum_volume = get_optional_arg(args, "--quorum-volume", self.suffix, "volume1") 
        self.wal_volume = get_optional_arg(args, "--wal-volume", self.suffix, "volume1") 
        