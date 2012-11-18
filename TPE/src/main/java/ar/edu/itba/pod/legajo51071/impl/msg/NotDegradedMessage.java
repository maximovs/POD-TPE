package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class NotDegradedMessage extends ClusterMessage implements SyncMessage{
	Address from;
	int id;
	public NotDegradedMessage(Address from) {
		this.from = from;
	}
	public Address getFrom() {
		return from;
	}
}
