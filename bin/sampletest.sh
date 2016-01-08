#!/bin/bash

DIR=$( cd $( dirname $0 ) && pwd )
PAR_DIR=$(dirname $DIR)
SERVER_LOG_DIR=$PAR_DIR/serverlogs

for f in 1.log 1.config 2.log 2.config 3.log 3.config server.output
do
    rm $SERVER_LOG_DIR/$f
done

cd $SERVER_LOG_DIR

for i in {1..3}
do
    cp init.log $i.log
    cp init.config $i.config
done

echo ELECTION_TIMEOUT_OVERRIDE=1200 >> $SERVER_LOG_DIR/1.config
echo ELECTION_TIMEOUT_OVERRIDE=600 >> $SERVER_LOG_DIR/2.config
echo ELECTION_TIMEOUT_OVERRIDE=300 >> $SERVER_LOG_DIR/3.config

cd $PAR_DIR

bin/runtest.sh 10 tests/sampletest.txt 2> /dev/null
