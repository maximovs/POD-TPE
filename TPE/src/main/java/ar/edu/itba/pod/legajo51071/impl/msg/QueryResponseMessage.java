package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;

import ar.edu.itba.pod.legajo51071.api.Result;

public class QueryResponseMessage extends ClusterMessage implements QueryMessage, AckMessage{
	int id;
	Address from;
	Result result;
	public QueryResponseMessage(int id, Address from, Result result) {
		this.id = id;
		this.from = from;
		this.result = result;
	}
	public Address getFrom() {
		return from;
	}
	public Result getResult() {
		return result;
	}
	
	public int getId() {
		return id;
	}
	
}
