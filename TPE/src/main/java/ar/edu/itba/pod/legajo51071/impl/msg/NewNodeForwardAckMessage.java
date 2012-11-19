package ar.edu.itba.pod.legajo51071.impl.msg;




public class NewNodeForwardAckMessage extends ClusterMessage implements AckMessage{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	int id;
	public NewNodeForwardAckMessage(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

}
