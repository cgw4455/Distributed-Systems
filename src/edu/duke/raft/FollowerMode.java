package edu.duke.raft;

import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.ThreadLocalRandom;

public class FollowerMode extends RaftMode {

	// Set Candidate time to random between 150ms-300ms, unless timeout
	// override.
	int randomCandidateTime = mConfig.getTimeoutOverride() == -1 ? ThreadLocalRandom
			.current().nextInt(ELECTION_TIMEOUT_MIN, ELECTION_TIMEOUT_MAX + 1)
			: mConfig.getTimeoutOverride();

	// timer of when to switch to candidate mode
	Timer candidateTimer;
	final int CANDIDATE_TIMER_ID = 0;

	public void go() {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();
			System.out.println("S" + mID + "." + term + ": switched to follower mode.");

			/* CODE STARTS HERE */

			// Set candidate timer
			System.out.println(mID + " candidate timer set for " + randomCandidateTime);
			candidateTimer = scheduleTimer(randomCandidateTime, CANDIDATE_TIMER_ID);

		}
	}

	// @param candidate’s term
	// @param candidate requesting vote
	// @param index of candidate’s last log entry
	// @param term of candidate’s last log entry
	// @return 0, if server votes for candidate; otherwise, server's
	// current term
	public int requestVote(int candidateTerm, int candidateID,
			int lastLogIndex, int lastLogTerm) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();
			int vote = 0;

			// got a vote request
			System.out.println("S" + mID + "." + term
					+ "(follower): got a vote request from S" + candidateID
					+ "." + candidateTerm);
			
			// Recognize vote requests greater than self.
			// 1. Their term must be greater to recognize them
			// 2. Other's last log term must be >= to vote for them
			// 3. Last Log Index must be >= to vote for them
			// 4. If not, vote for self
			if (candidateTerm > term) {
			
				// delete the timer, only for append entries/heartbeat.

				if (lastLogTerm < mLog.getLastTerm()) {
					System.out.println("I am on last log term");
					System.out.println("S" + mID + "." + term + ": did not cast log term vote for S" + candidateID + "." + lastLogTerm);
					vote = term;
				} else if (lastLogTerm == mLog.getLastTerm() && lastLogIndex < mLog.getLastIndex()) {
					System.out.println("I am on last log index");
					System.out.println("S" + mID + "." + term + ": did not cast log index vote for S" + candidateID + "." + lastLogIndex);
					vote = term;
				}

				// if greater, vote immediately, switch to follower
				// if <=, ignore because already voted for myself or out of date.
				if (vote == term) {
					mConfig.setCurrentTerm(candidateTerm, mID);
					term = mConfig.getCurrentTerm();
					vote = term;
					System.out.println("S" + mID + "." + term + ": casted vote for self");
				} else {
					mConfig.setCurrentTerm(candidateTerm, candidateID);
					term = mConfig.getCurrentTerm();
					System.out.println("S" + mID + "." + term + ": casted vote for S" + candidateID + "." + candidateTerm);
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
	public int appendEntries(int leaderTerm, int leaderID, int prevLogIndex, int prevLogTerm, Entry[] entries, int leaderCommit) {
		synchronized (mLock) {
			int term = mConfig.getCurrentTerm();

			System.out.println("S" + mID + "." + term + ": append request received from S" + leaderID + "." + leaderTerm + ", pLT=" + prevLogTerm + " pLI=" + prevLogIndex);

			// if term is higher, increment term
			if (leaderTerm >= term) {
				System.out.println("S" + mID + "." + term + ": candidate timer reset");
				candidateTimer.cancel();
				candidateTimer = scheduleTimer(randomCandidateTime, CANDIDATE_TIMER_ID);

				// if term strictly higher
				if (leaderTerm > term) {
					mConfig.setCurrentTerm(leaderTerm, leaderID);
					term = mConfig.getCurrentTerm();
					System.out.println("S" + mID + "." + term + "(follower) : updated to term " + leaderTerm);
				}

				// Regular append entry log.
				// inductive consistency check
				// 1. if no entry with same index and term, reject append in
				// RaftResponses, give current term
				// 2. if yes entry, insert into the log, return 0 in RResponse
				
				// If very first entry, pLI will be -1, return 0
				// If Entry doesn't exist return mLog.getEntry(mLog.getLastIndex()).term
				// If entry does exist return mLog.getEntry(prevLogIndex).
				int localPrevLogTerm;
				if (prevLogIndex == -1) {
					localPrevLogTerm = 0;
				} else if (prevLogIndex <= mLog.getLastIndex()) {
					localPrevLogTerm = mLog.getEntry(prevLogIndex).term;
				} else {
					localPrevLogTerm = mLog.getLastTerm();
				}
				
				// Print comparisons
				System.out.println("S" + mID + "." + term
						+ " (follower) : remote[" + prevLogTerm + ","
						+ prevLogIndex + "] =? local[" + localPrevLogTerm
						+ "," + mLog.getLastIndex() + "] AND data"
						+ Arrays.toString(entries));
				
				// ACCEPT the append. BOTH prev index and prev term must be equal
				// prevLogIndex just has to be less than the last index b/c no empty requests are allowed
				
				//TODO: What if heartbeat, and follower has an extra entry? He must notify leader somehow right? can this happen?
				if (prevLogIndex <= mLog.getLastIndex() && prevLogTerm == localPrevLogTerm) {
					System.out.println("S" + mID + "." + term + "(follower) : append accepted from S" + leaderID + "." + leaderTerm);
					
					// check if heartbeat, skip insert if so
					if (prevLogIndex != mLog.getLastIndex() || entries.length != 0) {
						System.out.println("S" + mID + "." + term + ": inserted entries");
						mLog.insert(entries, prevLogIndex, prevLogTerm);
					}
					System.out.println("S" + mID + "." + term + "(follower): setting RR.AR to 0");
					return 0;
				} else {
					// REJECT the append. by setting current term
					System.out.println("S" + mID + "." + term + ": append rejected, set RR.AR to " + term);
					return term;
				}

				// ignore request if from lower term
			} else {
				System.out.println("S" + mID + "." + term + ": append request ignored");
			}
			return term;
		}
	}

	// @param id of the timer that timed out
	public void handleTimeout(int timerID) {
		synchronized (mLock) {
			if (timerID == CANDIDATE_TIMER_ID) {
				// Change to candidate mode
				System.out.println("Time ran out on " + mID);
				candidateTimer.cancel();
				RaftServerImpl.setMode(new CandidateMode());
			}
		}
	}
}
