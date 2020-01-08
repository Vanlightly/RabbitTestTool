#!/usr/bin/env python

import sys
import subprocess
import os
import time
import uuid

from command_args import get_args, get_mandatory_arg

def run_benchmark(topology, technology, version):
    subprocess.call(["bash", "run-simple-local-benchmark.sh", topology, technology, version, ])

args = get_args(sys.argv)
topologies_root = get_mandatory_arg(args, "--topologies-root")
playlist_file = get_mandatory_arg(args, "--playlist-file")
technology = get_mandatory_arg(args, "--technology")
version = get_mandatory_arg(args, "--version")
gap_seconds = int(get_mandatory_arg(args, "--gap-seconds"))

pl_file = open(playlist_file, "r")


for line in pl_file:
    topology = line.replace("\n", "")
    
    run_benchmark(topologies_root + "/" + topology, technology, version)
    
    print(f"Finished {topology}")
    time.sleep(gap_seconds)