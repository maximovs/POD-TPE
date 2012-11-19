package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;


public class NotDegradedMessage extends ClusterMessage implements SyncMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Address from;
	int id;
	public NotDegradedMessage(Address from) {
		this.from = from;
	}
	public Address getFrom() {
		return from;
	}
}
