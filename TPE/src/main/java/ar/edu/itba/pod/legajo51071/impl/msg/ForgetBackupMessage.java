package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.api.Signal;

public class ForgetBackupMessage extends ClusterMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	int id;
	Address from;
	List<Signal> signals;
	public ForgetBackupMessage(int id, Address from, List<Signal> signals) {
		this.id = id;
		this.from = from;
		this.signals = signals;
	}
	public Address getFrom() {
		return from;
	}
	public List<Signal> getSignals() {
		return signals;
	}
	
	public int getId() {
		return id;
	}
	
	public ForgetBackupAckMessage generateAck(Address from) {
		return new ForgetBackupAckMessage(from , id);
	}
}
