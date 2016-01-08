package edu.duke.raft;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Timer;

// QUESTIONS:
// How often to send append requests?
// How often to check for append requests?
// Do we automatically increment leader term if a log entry is higher? ( i think yes... is there a case where this is bad?)

public class LeaderMode extends RaftMode {

	final private int HEART_TIMER_ID = 3;
	final private int APPEND_POLL_ID = 4;
	private Timer appendTimer;
	private int POLL_TIME = 30;
	private Timer heartTimer;
	private int[] nextIndex;
	private boolean[] serverLogMatched;
	private int prevLogTerm;
	private int prevLogIndex;
	private ArrayList<LinkedList<Entry>> entryIndex;

	public void go () {
		synchronized (mLock) {

			// Initialize necessary data
			int term = mConfig.getCurrentTerm();
			prevLogTerm = mLog.getLastTerm();
			prevLogIndex = mLog.getLastIndex();
			int numServers = mConfig.getNumServers();
			System.out.println ("S" + mID + "." + term + ": switched to leader mode.");
			
			// Initialize entries
			serverLogMatched = new boolean[numServers + 1];
			nextIndex = new int[numServers + 1];
			entryIndex = new ArrayList<LinkedList<Entry>>();
			for (int i = 0; i < numServers + 1; i++) {
				entryIndex.add(new LinkedList<Entry>());
				nextIndex[i] = prevLogIndex + 1;
			}
			for (int i = 1; i <= numServers; i++) {
				serverLogMatched[i] = false;
				if (i == mID) {
					serverLogMatched[i] = true;
				}
			}

			//FIXME If log entry term is initialized to be higher than our leader term, do we automatically increment the leader term?
			Entry lastEntry = mLog.getEntry(prevLogIndex);
			if (lastEntry.term >= term) {
				System.out.println ("S" + mID + "." + term + ": term incremented to match log -> " + (lastEntry.term + 1));
				mConfig.setCurrentTerm(lastEntry.term + 1, mID);
				term = mConfig.getCurrentTerm();
			}
			
			//XXX First should be heartbeat, if failed response, then decrement
//			lastEntryList.addFirst(lastEntry);
			
			
			// Initialize RaftResponses
			RaftResponses.setTerm(term);
			RaftResponses.clearAppendResponses(term);

			// Send out last entry to everyone
			// Schedule timer to poll appendResponses
			for (int i = 1; i <= numServers; i++) {
				if (!serverLogMatched[i]) {
					Entry[] entryToSend = entryIndex.get(i).toArray(new Entry[entryIndex.get(i).size()]);
					remoteAppendEntries(i, term, mID, prevLogIndex, prevLogTerm, entryToSend, mCommitIndex);
					System.out.println("S" + mID + "." + term + ": sent out append request to S" + i);
				}
			}
			//schedule timer
			appendTimer = scheduleTimer(POLL_TIME, APPEND_POLL_ID);
			heartTimer = scheduleTimer(HEARTBEAT_INTERVAL, HEART_TIMER_ID);


			// Send heartbeat
			//      System.out.println("S" + mID + "." + term + ": sent out heartbeat");
			//      for (int i = 1; i <= mConfig.getNumServers(); i++) {
			//			remoteAppendEntries(i, term, mID, lastIndex, lastTerm, null, mCommitIndex);
			//		}
			//      System.out.println("heartbeat timer set to " + HEARTBEAT_INTERVAL);
			//      heartTimer = scheduleTimer(HEARTBEAT_INTERVAL, HEART_TIMER_ID);

		}
	}

	// @param candidate’s term
	// @param candidate requesting vote
	// @param index of candidate’s last log entry
	// @param term of candidate’s last log entry
	// @return 0, if server votes for candidate; otherwise, server's
	// current term
	public int requestVote (int candidateTerm,
			int candidateID,
			int lastLogIndex,
			int lastLogTerm) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm ();
			int vote = 0;


			System.out.println("S" + mID + "." + term + "(leader): got vote request from " + "S" + candidateID + "." + candidateTerm);
			
			// If greater, vote immediately and switch to followerMode
			// 1. Their term must be greater to recognize them
			// 2. Other's last log term must be >= to vote for them
			// 3. Last Log Index must be >= to vote for them
			if (candidateTerm > term) {
				//Checking that last lost term for the candidate is higher than the server's
				//If higher, vote for candidate
				//else if, check that last log index is higher or equal
				
				if (lastLogTerm < mLog.getLastTerm()) {
					System.out.println("I am on last log term");
					System.out.println("S" + mID + "." + term + ": did not cast log term vote for S" + candidateID + "." + lastLogTerm);
					vote = term;
				} else if (lastLogTerm == mLog.getLastTerm() && lastLogIndex < mLog.getLastIndex()){
					System.out.println("I am on last log index");
					System.out.println("S" + mID + "." + term + ": did not cast log index vote for S" + candidateID + "." + lastLogIndex);
					vote = term;
				} 
				
				// Is candidate qualified?
				if (vote == term) {
					mConfig.setCurrentTerm(candidateTerm, mID);
					term = mConfig.getCurrentTerm();
					vote = term;
					System.out.println("S" + mID + "." + term + ": casted vote for self");
				} else {
					mConfig.setCurrentTerm(candidateTerm, candidateID);
					term = mConfig.getCurrentTerm();
					System.out.println("S" + mID + "." + term + ": casted vote for S" + candidateID + "." + candidateTerm);
					appendTimer.cancel();
					heartTimer.cancel();
					RaftServerImpl.setMode(new FollowerMode());
				}
				return vote;
			}
			return term;
		}
	}


	// @param leader’s term
	// @param current leader
	// @param index of log entry before entries to append
	// @param term of log entry before entries to append
	// @param entries to append (in order of 0 to append.length-1)
	// @param index of highest committed entry
	// @return 0, if server appended entries; otherwise, server's
	// current term
	public int appendEntries (int leaderTerm,
			int leaderID,
			int prevLogIndex,
			int prevLogTerm,
			Entry[] entries,
			int leaderCommit) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm ();
			int result = term;

			// TODO: if receiving an append request from higher term, switch to follower mode
			System.out.println("S" + mID + "." + term + " (leader): append request received");
			if (leaderTerm >= term) {

				// update term if higher
				if (leaderTerm > term) {
					mConfig.setCurrentTerm(leaderTerm, leaderID);			
				}
				// cancel all timers
				appendTimer.cancel();
				heartTimer.cancel();

				//switch to follower
				RaftServerImpl.setMode(new FollowerMode());
			}
			
			// return -1 because otherwise leader will think you did something, when you haven't
			return -1;
		}
	}

	// @param id of the timer that timed out
	public void handleTimeout (int timerID) {
		synchronized (mLock) {

			// If no append responses for a while, send an empty one.
			if (timerID == HEART_TIMER_ID) {
				int term = mConfig.getCurrentTerm();
				for (int i = 1; i <= mConfig.getNumServers(); i++) {
					if(serverLogMatched[i] && i != mID){
						System.out.println("S" + mID + "." + term + ": sent out heartbeat to S" + i);
						remoteAppendEntries(i, term, mID, prevLogIndex, prevLogTerm, entryIndex.get(i).toArray(new Entry[0]), mCommitIndex);
					}
				}
				heartTimer.cancel();
				heartTimer = scheduleTimer(HEARTBEAT_INTERVAL, HEART_TIMER_ID); 

				//polling the append responses
			} else if (timerID == APPEND_POLL_ID) {
				int term = mConfig.getCurrentTerm();
				int[] appendResponses = RaftResponses.getAppendResponses(term);
				if (appendResponses == null) {
					System.err.println("APPEND RESPONSES IS NULL IN LEADER");
					appendTimer.cancel();
					appendTimer = scheduleTimer(POLL_TIME, APPEND_POLL_ID);
					return;
				}
				for (int i = 1; i <= mConfig.getNumServers(); i++) {
					//XXX I don't think this needs to be here.
//					if(!updated[i]){
						// if rejected, decrease appendIndex of the server, set it back to default, add an earlier entry
					
					System.out.println("AR = " + Arrays.toString(appendResponses));
						if (appendResponses[i] > 0) {
							
							serverLogMatched[i] = false;
							// decrease nextIndex of server
							System.out.println("nextIndex[" + i + "] updated to " + (nextIndex[i]-1));
							nextIndex[i] -= 1;

							// update the Entry requestfor that server
							entryIndex.get(i).addFirst(mLog.getEntry(nextIndex[i]));
							System.out.println("S" + mID + "." + term + " (leader): updated entryIndex["+ i + "] = " +  entryIndex.get(i));
							
							
							//set back to default
							RaftResponses.setAppendResponse(i, -1, term);


							// append request with new entries list, one index back.
							// prevTerm
							Entry[] updatedEntries = entryIndex.get(i).toArray(new Entry[entryIndex.get(i).size()]);
							int updatedTerm = nextIndex[i] == 0 ? 0 : mLog.getEntry(nextIndex[i] - 1).term;
							remoteAppendEntries(i, term, mID, nextIndex[i] - 1, updatedTerm, updatedEntries, mCommitIndex);
							System.out.println("S" + mID + "." + term + ": sent out append request to S" + i);
						} else if (appendResponses[i] == 0) {
							// if was successfully updated, update the boolean array, empty their entry list
							serverLogMatched[i] = true;
							nextIndex[i] = prevLogIndex;
							entryIndex.get(i).clear();
						} else {
							// Response is -1, Nothing has happened, server isn't responding maybe, so send request again
							if (!serverLogMatched[i]) {
								Entry[] updatedEntries = entryIndex.get(i).toArray(new Entry[entryIndex.get(i).size()]);
								int updatedTerm = nextIndex[i] == 0 ? 0 : mLog.getEntry(nextIndex[i] - 1).term;
								remoteAppendEntries(i, term, mID, nextIndex[i] - 1, updatedTerm, updatedEntries, mCommitIndex);
								System.out.println("S" + mID + "." + term + ": sent out append request to S" + i);
							}
						}
//					}
				}
				appendTimer.cancel();
				appendTimer = scheduleTimer(POLL_TIME, APPEND_POLL_ID);
			}
		}
	}
}
