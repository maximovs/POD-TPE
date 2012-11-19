package ar.edu.itba.pod.legajo51071.impl;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;

import com.google.common.collect.Lists;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Signal;

public class Query {
	private static final AtomicInteger idGen = new AtomicInteger();
	
	private final int id;
	@GuardedBy("this") private final CountDownLatch waiting;
	private final AtomicBoolean errors;
	private final ConcurrentLinkedQueue<Result> results;
	private final Signal signal;
	
	public Query(int others, Signal signal) {
		id = getNextId();
		this.signal = signal;
		waiting = new CountDownLatch(others);
		errors = new AtomicBoolean();
		results = new ConcurrentLinkedQueue<>();
	}
	
	private static int getNextId(){
		return idGen.getAndIncrement();
	}
	
	public void errorDetected(){
		errors.set(true);
		while(waiting.getCount()>0){
			waiting.countDown();
		}
	}
	
	public boolean hasErrors(){
		return errors.get();
	}
	
	public int getId(){
		return id;
	}
	
	public Signal getSignal(){
		return signal;
	}
	
	public void addResult(Result res){
		results.add(res);
		waiting.countDown();
//		System.out.println("se hace countdown");
	}
	
	public List<Result> getResults(){
		return Lists.newArrayList(results);
	}
	
	public void await() throws InterruptedException{
		waiting.await();
	}
}
