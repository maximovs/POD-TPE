package ar.edu.itba.pod.legajo51071.impl.msg;

import java.util.List;

import org.jgroups.Address;

import ar.edu.itba.pod.legajo51071.api.Signal;

public class ForwardAckMessage extends ClusterMessage implements AckMessage{
	int id;
	public ForwardAckMessage(int id) {
		this.id = id;
	}

	public int getId() {
		return id;
	}

}
