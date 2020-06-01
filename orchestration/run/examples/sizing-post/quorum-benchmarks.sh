#!/bin/bash

# growing intensity
bash sizing-mirrored.sh playlists/scale-medium-quorum-no-fanout.json ebs-io1 200
bash sizing-mirrored.sh playlists/scale-medium-quorum-no-fanout.json ebs-gp2 1000
bash sizing-mirrored.sh playlists/scale-medium-quorum-no-fanout.json ebs-st1 7000

# growing intensity with loss of broker
bash sizing-mirrored.sh playlists/scale-medium-quorum-no-fanout-lose-broker.json ebs-gp2 1000

# growing intensity with initial backlog
bash sizing-mirrored.sh playlists/scale-medium-quorum-no-fanout-backlog.json ebs-gp2 1000