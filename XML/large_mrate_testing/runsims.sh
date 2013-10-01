#!/bin/bash

for m in 0.125 0.25 0.5 1 2 4 8 16 32 64; do
    beast -main beast.evolution.tree.coalescent.StructuredCoalescentMultiTypeTree $m
    mv heights.txt heights$m.txt
done