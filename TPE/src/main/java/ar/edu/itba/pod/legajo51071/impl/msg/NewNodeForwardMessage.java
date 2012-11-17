package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.LinkedList;
import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo51071.api.Signal;

public class NewNodeForwardMessage extends ClusterMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = -3876719154297557134L;
	Address from;
	int id;
	List<Signal> toFw;
	List<Signal> toBu;

	public NewNodeForwardMessage(Address from, List<Signal> toFw, List<Signal> toBu, int id) {
		this.from = from;
		this.id = id;
		this.toFw = toFw;
		this.toBu = toBu;
	}
	
	public Address getFrom() {
		return from;
	}

	public List<Signal> getToFw() {
		return toFw;
	}
	
	public List<Signal> getToBu() {
		return toBu;
	}
	
	public int getId() {
		return id;
	}
	
	public NewNodeForwardAckMessage generateAck() {
		return new NewNodeForwardAckMessage(id);
	}
}
