#!/bin/bash


####################
#  TEST 1 : BASIC  #
####################

# 3 should become leader
# log should be { (1 1), (3 1) }

DIR=$( cd $( dirname $0 ) && pwd )
PAR_DIR=$(dirname $DIR)
SERVER_LOG_DIR=$PAR_DIR/serverlogs
TEST_DIR=$PAR_DIR/tests

echo ELECTION_TIMEOUT_OVERRIDE=1200 >> $SERVER_LOG_DIR/1.config
echo ELECTION_TIMEOUT_OVERRIDE=600 >> $SERVER_LOG_DIR/2.config
echo ELECTION_TIMEOUT_OVERRIDE=300 >> $SERVER_LOG_DIR/3.config
echo 3 1 >> $SERVER_LOG_DIR/3.log
