#!/bin/bash

rm -f MultiTypeTree.zip
for j in  lib/*.jar dist/MultiTypeTree.jar \
    ../beastii/lib/*.jar ../beastii/dist/beastii.jar \
    ../MASTER/lib/*.jar ../MASTER/dist/MASTER.jar \
    ../beast2/lib/*.jar ../beast2/dist/beast2.jar; do

    zip -j MultiTypeTree.zip $j
done

