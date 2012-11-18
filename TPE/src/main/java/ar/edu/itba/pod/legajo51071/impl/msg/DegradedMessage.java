package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class DegradedMessage extends ClusterMessage implements SyncMessage{
	Address from;
	int id;
	public DegradedMessage(Address from) {
		this.from = from;
	}
	public Address getFrom() {
		return from;
	}
}
