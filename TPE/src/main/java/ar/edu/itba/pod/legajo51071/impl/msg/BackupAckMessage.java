package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;


public class BackupAckMessage extends ClusterMessage implements AckMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	Address from;
	int id;
	public BackupAckMessage(Address from, int id) {
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
