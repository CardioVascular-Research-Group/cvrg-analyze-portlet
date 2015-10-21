package edu.jhu.cvrg.waveform.utility;

import java.io.Serializable;
import java.util.Collection;

public class ThreadController extends Thread implements Serializable{

	private static final long serialVersionUID = 348093133316110634L;
	
	private static int threadPoolSize = 50;
	private static int threadPoolSleepTime = 500;
	
	private static ThreadGroup group = new ThreadGroup("AnalysisGlobalGroup");
	
	private Collection<? extends Thread> threads;
	
	public ThreadController(Collection<? extends Thread> threadCollection) {
		threads = threadCollection;
	}
	
	@Override
	public void run() {
		try{
			if(threads != null && !threads.isEmpty()){
				for (Thread t : threads) {
					t.start();
					
					while (group.activeCount() >= threadPoolSize) {
						ThreadController.sleep(threadPoolSleepTime);
					}
				}
			}
		
		}catch (Exception e) {
			System.out.println("#### ThreadController Error #### "+ e.getMessage());
		}
	}
	
	public int getThreadCount(){
		return threads != null ? threads.size() : 0;
	}

	public Collection<? extends Thread> getThreads() {
		return threads;
	}

	public static ThreadGroup createSubGroup(String groupName){
		return new ThreadGroup(group, groupName);
	}
}
