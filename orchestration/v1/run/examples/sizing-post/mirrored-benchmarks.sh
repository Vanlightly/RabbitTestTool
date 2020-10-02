#!/bin/bash

# growing intensity
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout.json ebs-io1 200
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout.json ebs-gp2 1000
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout.json ebs-st1 7000

# growing intensity with loss of broker
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout-lose-broker.json ebs-gp2 1000

# consumer slowdown produces backlog
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout-changing-consume-rate.json ebs-gp2 1000

# publishing peak produces backlog
bash sizing-mirrored.sh playlists/sizing/sizing-medium-mirrored-no-fanout-changing-publish-rate.json ebs-gp2 1000