package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Signal;

public class QueryRequestMessage extends ClusterMessage implements QueryMessage{
	int id;
	Address from;
	Signal signal;
	public QueryRequestMessage(int id, Address from, Signal signal) {
		this.id = id;
		this.from = from;
		this.signal = signal;
	}
	public Address getFrom() {
		return from;
	}
	public Signal getSignal() {
		return signal;
	}
	
	public int getId() {
		return id;
	}
	
	public QueryResponseMessage generateResponse(Address from, Result result) {
		return new QueryResponseMessage(id, from, result);
	}
}
