#!/bin/bash

# growing intensity
bash sizing-mirrored.sh playlists/sizing/sizing-medium-classic-no-fanout.json ebs-io1 200
bash sizing-mirrored.sh playlists/sizing/sizing-medium-classic-no-fanout.json ebs-gp2 1000
bash sizing-mirrored.sh playlists/sizing/sizing-medium-classic-no-fanout.json ebs-st1 7000
