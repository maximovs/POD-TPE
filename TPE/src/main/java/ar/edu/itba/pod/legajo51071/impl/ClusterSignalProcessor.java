package ar.edu.itba.pod.legajo51071.impl;

import java.rmi.RemoteException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import ar.edu.itba.pod.legajo51071.api.NodeStats;
import ar.edu.itba.pod.legajo51071.api.Result;
import ar.edu.itba.pod.legajo51071.api.SPNode;
import ar.edu.itba.pod.legajo51071.api.Signal;
import ar.edu.itba.pod.legajo51071.api.SignalProcessor;

public class ClusterSignalProcessor implements SignalProcessor, SPNode{
	LocalProcessor localProc; 
	private volatile ClusterProcessor clusterProc;
	AtomicBoolean isStandAlone = new AtomicBoolean();
	AtomicInteger receivedSignals  = new AtomicInteger();
	ExecutorService requestsProcessor = Executors.newFixedThreadPool(2);
	

	int cores;
	public ClusterSignalProcessor(int cores, int port) {
		this.cores = cores;
		localProc = new LocalProcessor(cores);
		isStandAlone.set(true);
	}

	@Override
	public void join(String clusterName) throws RemoteException {
		try {
			clusterProc = new ClusterProcessor(this, localProc, clusterName);
			isStandAlone.set(false);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
	}

	@Override
	public void exit() throws RemoteException {
//		try {
//			localProc.exit();
			isStandAlone.set(true);
			if(clusterProc != null) clusterProc.exit();
			clusterProc = null;
			localProc.empty();
//		} catch (InterruptedException e) {
//			e.printStackTrace();
//		}
	}

	@Override
	public NodeStats getStats() throws RemoteException {
		if(clusterProc==null || isStandAlone.get())
			return new NodeStats("Empty", receivedSignals.get(), localProc.size(), 0, false);
		return new NodeStats(clusterProc.getNodeIdName(), receivedSignals.get(), localProc.size(), clusterProc.backupSignals(), clusterProc.isDegraded());
	}

	@Override
	public void add(Signal signal) throws RemoteException {
		final Signal s = signal;
		requestsProcessor.execute(new Runnable() {
			
			@Override
			public void run() {
				if(isStandAlone.get() || clusterProc == null){
					localProc.add(s);
				}else{
					clusterProc.add(s);
				}
				
			}
		});
		

	}

	@Override
	public Result findSimilarTo(Signal signal) throws RemoteException {
		if (signal == null) {
			throw new IllegalArgumentException("Signal cannot be null");
		}
		receivedSignals.incrementAndGet();
		
		LinkedList<Result> results = new LinkedList<>();
		if(clusterProc != null && !isStandAlone.get()){
		clusterProc.findSimilarTo(results, signal);
		}
		
		Result localResults = localProc.findSimilarTo(signal);
		results.add(localResults);
		return findSimilarTo(results,signal);
	}

	private Result findSimilarTo(List<Result> results, Signal signal){
		Result result = new Result(signal);
		for(Result r: results){
			for(Result.Item item:r.items()){
				result = result.include(item);
			}
		}
		return result;
	}

}
