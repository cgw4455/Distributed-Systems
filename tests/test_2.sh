#!/bin/bash


##################################
#  TEST 2 : Delete Extra Entries  #
###################################

# 1 should become leader.
# log should be { (1 1) (2 2) (2 2) (7 7) (7 7) }

DIR=$( cd $( dirname $0 ) && pwd )
PAR_DIR=$(dirname $DIR)
SERVER_LOG_DIR=$PAR_DIR/serverlogs
TEST_DIR=$PAR_DIR/tests

# echo ELECTION_TIMEOUT_OVERRIDE=1200 >> $SERVER_LOG_DIR/1.config
# echo ELECTION_TIMEOUT_OVERRIDE=600 >> $SERVER_LOG_DIR/2.config
# echo ELECTION_TIMEOUT_OVERRIDE=300 >> $SERVER_LOG_DIR/3.config
echo "2 2
2 2
7 7
7 7
7 7" >> $SERVER_LOG_DIR/1.log

echo "2 2
2 2
2 2
3 3
3 3
3 3
3 3" >> $SERVER_LOG_DIR/2.log

