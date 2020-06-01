#!/bin/bash

# growing intensity
bash sizing-mirrored.sh playlists/scale-medium-classic-no-fanout.json ebs-io1 200
bash sizing-mirrored.sh playlists/scale-medium-classic-no-fanout.json ebs-gp2 1000
bash sizing-mirrored.sh playlists/scale-medium-classic-no-fanout.json ebs-st1 7000

# growing intensity with loss of broker
# didn't run this test as it is not relevant

# growing intensity with initial backlog
bash sizing-mirrored.sh playlists/scale-medium-classic-no-fanout-backlog.json ebs-gp2 1000