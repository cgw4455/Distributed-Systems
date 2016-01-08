package edu.duke.raft;
import java.util.Arrays;
import java.util.Timer;
import java.util.concurrent.ThreadLocalRandom;

public class CandidateMode extends RaftMode {

	final static int ELECTION_TIMER_ID = 1;
	final static int VOTE_TIMER_ID = 2;
	Timer electionTimer;
	Timer voteTimer;
	int randomElectionTime = mConfig.getTimeoutOverride() == -1 ?
			ThreadLocalRandom.current().nextInt(ELECTION_TIMEOUT_MIN, ELECTION_TIMEOUT_MAX + 1)
			: mConfig.getTimeoutOverride();
			int voteTime = 10;


			public void go () {
				synchronized (mLock) {
					int term = mConfig.getCurrentTerm() + 1;
					// Increment current term  
					System.out.println ("S" + 
							mID + 
							"." + 
							term + 
							": switched to candidate mode.");

					//start of election. Vote for self and ask others to vote too 
					startElection();

				}
			}

			private void startElection() {
				// send a vote request remoteRequestVote
				// vote for yourself
				// Read the responses in Raft Responses every so often with a timer
				// If majority, then you become leader (setMode Leader)
				// If not. and timer runs out. election fails, and you start new election. (increment term, request all votes)

				mConfig.setCurrentTerm(mConfig.getCurrentTerm() + 1, mID);
				int term = mConfig.getCurrentTerm();
				System.out.println(mID + ": starts election #" + term);      
				RaftResponses.setTerm(term);
				RaftResponses.clearVotes(term);
				RaftResponses.setVote(mID, 0, term);
				for (int i = 1; i <= mConfig.getNumServers(); i++) {
					if (i != mID) {
						System.out.println ("S" + 
								mID + 
								"." + 
								term + 
								": vote request sent out");
						remoteRequestVote(i, term, mID, mLog.getLastIndex(), mLog.getLastTerm());
					}
				}
				//      System.out.println(mID + " election timer set for " + randomElectionTime);
				//      System.out.println(mID + " vote timer set for " + voteTime);
				electionTimer = scheduleTimer(randomElectionTime, ELECTION_TIMER_ID);
				voteTimer = scheduleTimer(voteTime, VOTE_TIMER_ID);
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

					System.out.println("S" + mID + "." + term + "(candidate): got vote request from " + "S" + candidateID + "." + candidateTerm);
					
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
							electionTimer.cancel();
							voteTimer.cancel();
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

					// If you get append request (higher term), while election running, set term, "cancel the timer/election" and become follower.
					// update term if higher or equal, because this means leader has already won your election.
					System.out.println("S" + mID + "." + term + " (candidate): heartbeat received from S" + leaderID + "." + leaderTerm);
					if (leaderTerm >= term) {
						mConfig.setCurrentTerm(leaderTerm, leaderID);			
						// cancel election and vote timer
						electionTimer.cancel();
						voteTimer.cancel();

						//switch to follower
						RaftServerImpl.setMode(new FollowerMode());
						
						// return -1 because we will make changes on the next request in follower mode.
						return -1;
					}
					System.out.println("S" + mID + "." + term + " (candidate): heartbeat ignored from S" + leaderID + "." + leaderTerm);
					// return -1, b/c we don't want leader to think append did or didn't succeed. act as if nothing happended
					return -1;
				}
			}

			// @param id of the timer that timed out
			public void handleTimeout (int timerID) {
				synchronized (mLock) {
					// The election is taking too long
					if (timerID == ELECTION_TIMER_ID) {
						System.out.println(mID + ": election timer runs out");
						voteTimer.cancel();
						electionTimer.cancel();
						startElection();

						//Time to collect votes, if split, or not enough
					} else if (timerID == VOTE_TIMER_ID) {
						int term = mConfig.getCurrentTerm();
						int numServers = mConfig.getNumServers();
						int[] votes = RaftResponses.getVotes(term);
						if (votes == null) {
							//TODO: What if it comes back null?
						} else {
							int count = 0;
							for (int vote : votes) {
								count = vote == 0 ? count + 1 : count; 
							}
							System.out.println("S" + mID + "." + term + ": VOTES = " + Arrays.toString(votes) + " TOTAL= " + count);

							// enough votes to be leader (strict majority)
							if (count > numServers/2.0) {
								voteTimer.cancel();
								voteTimer.purge();
								electionTimer.cancel();
								electionTimer.purge();
								System.out.println(mID + " wins election!");
								RaftResponses.clearVotes(term);
								RaftServerImpl.setMode(new LeaderMode());
								return;
							}
						}
//						System.out.println("S" + mID + "." + term + "(candidate): voteTimer reset");
						voteTimer.cancel();
						voteTimer = scheduleTimer(voteTime, VOTE_TIMER_ID);
					}
				}
				return;
			}
}
