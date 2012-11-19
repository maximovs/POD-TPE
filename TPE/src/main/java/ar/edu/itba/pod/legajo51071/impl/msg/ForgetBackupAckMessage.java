package ar.edu.itba.pod.legajo51071.impl.msg;


import org.jgroups.Address;


public class ForgetBackupAckMessage extends ClusterMessage implements AckMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
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
