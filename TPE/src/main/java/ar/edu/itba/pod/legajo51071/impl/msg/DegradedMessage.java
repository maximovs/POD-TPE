package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;


public class DegradedMessage extends ClusterMessage implements SyncMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Address from;
	int id;
	public DegradedMessage(Address from) {
		this.from = from;
	}
	public Address getFrom() {
		return from;
	}
}
