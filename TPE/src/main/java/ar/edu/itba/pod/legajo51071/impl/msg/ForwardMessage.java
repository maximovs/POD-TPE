package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo51071.api.Signal;

public class ForwardMessage extends ClusterMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = -3876719154297557134L;
	int id;
	List<Signal> signals;

	public ForwardMessage(List<Signal> signals, int id) {
		this.id = id;
		this.signals = signals;
	}

	public List<Signal> getSignals() {
		return signals;
	}
	
	public int getId() {
		return id;
	}
	
	public ForwardAckMessage generateAck() {
		return new ForwardAckMessage(id);
	}
}
