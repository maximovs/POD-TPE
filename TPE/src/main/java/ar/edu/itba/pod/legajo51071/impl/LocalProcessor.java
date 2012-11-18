package ar.edu.itba.pod.legajo51071.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Signal;

public class LocalProcessor {
	ExecutorService ex;
	ConcurrentHashMap<Integer,ConcurrentLinkedQueue<Signal>> signals;
	int workers;
	AtomicInteger addedSignals = new AtomicInteger();
	public LocalProcessor(int workers) {
		this.workers = workers;
		ex = Executors.newFixedThreadPool(workers);
		signals = new ConcurrentHashMap<>();
		for(int i=0 ; i<workers; i++){
			signals.put(i, new ConcurrentLinkedQueue<Signal>());
		}
		
	}
	
	public int size(){
		return addedSignals.get();
	}
	
	public void add(Signal signal){
		signals.get(addedSignals.getAndIncrement()%workers).add(signal);

	}
	
	public void remove(List<Signal> s){
		for(int i = 0; i<s.size(); i++){
			Signal sig = s.get(i);
			for(int j = i%workers; j<(i%workers)+workers;j++){
				if(signals.get(j%workers).remove(sig)){
					addedSignals.decrementAndGet();
					break;
				}
			}
		}
	}
	
	public Result findSimilarTo(Signal signal){
		
		Result result = null;
		LinkedList<ProcessingTask> l = new LinkedList<>();
		for(int i = 0; i < workers; i++){
			l.add(new ProcessingTask(signals.get(i), signal));
		}

		try {
			List<Future<Result>> fResults = ex.invokeAll(l);

			LinkedList<Result> results = new LinkedList<>();
			for(Future<Result> f: fResults){
				results.add(f.get());
			}
			result = findSimilarTo(results, signal);
		} catch (InterruptedException | ExecutionException e) {
			e.printStackTrace();
		}

		return result;
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
	
	public void exit() throws InterruptedException{
		ex.shutdown();
		ex.awaitTermination(5, TimeUnit.SECONDS);
	}

	public void add(List<Signal> signals) {
		for(Signal s: signals){
			add(s);
		}
		
	}

	public void empty() {
		for(ConcurrentLinkedQueue<Signal> s:signals.values()){
			while(!s.isEmpty()){
				s.remove();
				addedSignals.decrementAndGet();
			}
		}
	}

}
