package ar.edu.itba.pod.legajo51071.impl;

import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;

import ar.edu.itba.pod.legajo51071.api.Result;
import ar.edu.itba.pod.legajo51071.api.Signal;

public class ProcessingTask implements Callable<Result>{
	ConcurrentLinkedQueue<Signal> signals;
	Signal signal;
	
	public ProcessingTask(ConcurrentLinkedQueue<Signal> signals, Signal signal) {
		this.signals = signals;
		this.signal = signal;
	}
	
	@Override
	public Result call() throws Exception {
		if (signal == null) {
			throw new IllegalArgumentException("Signal cannot be null");
		}

		Result result = new Result(signal);
		
		for (Signal cmp : signals) {
			Result.Item item = new Result.Item(cmp, signal.findDeviation(cmp));
			result = result.include(item);
		}
		return result;
	}

}
