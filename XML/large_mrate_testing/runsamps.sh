#!/bin/bash

for m in 0.125 0.25 0.5 1 2 4 8 16 32 64; do
    sed 's/MRATE/'$m'/g' < large_mrate_test.template >large_mrate_test_$m.xml
    beast large_mrate_test_$m.xml
done
