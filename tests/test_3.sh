#!/bin/bash


###########################
#  TEST 3 : 5 servers  #
###########################


DIR=$( cd $( dirname $0 ) && pwd )
PAR_DIR=$(dirname $DIR)
SERVER_LOG_DIR=$PAR_DIR/serverlogs
TEST_DIR=$PAR_DIR/tests

echo "2 2
2 2
3 4
3 5
4 2" >> $SERVER_LOG_DIR/1.log

echo "2 2
2 4
6 5" >> $SERVER_LOG_DIR/2.log

echo "7 7" >> $SERVER_LOG_DIR/3.log


