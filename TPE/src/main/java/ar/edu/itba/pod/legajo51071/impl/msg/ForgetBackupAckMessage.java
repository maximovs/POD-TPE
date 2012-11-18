package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class ForgetBackupAckMessage extends ClusterMessage implements AckMessage{
	Address from;
	int id;
	public ForgetBackupAckMessage(Address from, int id) {
		this.from = from;
		this.id = id;
	}
	public Address getFrom() {
		return from;
	}
	public int getId() {
		return id;
	}
}
