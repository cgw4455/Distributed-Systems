#!/bin/bash

DIR=$( cd $( dirname $0 ) && pwd )
PAR_DIR=$(dirname $DIR)
SERVER_LOG_DIR=$PAR_DIR/serverlogs
TEST_DIR=$PAR_DIR/tests

cd $PAR_DIR

for f in 1.log 1.config 2.log 2.config 3.log 3.config 4.log 4.config server.output
do
    if [ -f $SERVER_LOG_DIR/$f ]; then
        rm $SERVER_LOG_DIR/$f
    fi
done

cd $SERVER_LOG_DIR

for i in {1..5}
do
    cp init.log $i.log
    cp init.config $i.config
done

cd $TEST_DIR

if [ -z "$1" ]
then
    echo "USAGE ERROR: expected \"mytest testnum\""
    exit 1
fi

./test_$1.sh

cd $PAR_DIR

bin/runtest.sh 10 tests/crazytest.txt 2> /dev/null

exit 0

