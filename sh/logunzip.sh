#!/bin/bash
for i in `ls`; do cat $i | gunzip > ${i%.gz}; done

