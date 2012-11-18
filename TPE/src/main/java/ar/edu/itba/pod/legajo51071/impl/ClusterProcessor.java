package ar.edu.itba.pod.legajo51071.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;


import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ReceiverAdapter;
import org.jgroups.View;

import com.google.common.collect.Lists;

import ar.edu.itba.pod.api.Result;
import ar.edu.itba.pod.api.Signal;
import ar.edu.itba.pod.legajo51071.impl.msg.AckMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.BackupAckMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.BackupMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ClusterMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.DegradedMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForgetBackupAckMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForgetBackupMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForwardAckMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForwardMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.NewNodeForwardAckMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.NewNodeForwardMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.NotDegradedMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.QueryMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.QueryRequestMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.QueryResponseMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.SyncMessage;

public class ClusterProcessor extends ReceiverAdapter {
	private JChannel channel;
	private String clusterName;
	private volatile View group = null;
	private volatile ConcurrentLinkedQueue<Address> others;
	final ClusterBalancer balancer;
	final ConcurrentHashMap<Address, ConcurrentLinkedQueue<Signal>> backups = new ConcurrentHashMap<>();
	final ConcurrentLinkedQueue<Signal> toShare = new ConcurrentLinkedQueue<>();
	final AtomicInteger backupSignals = new AtomicInteger();
	final ClusterSignalProcessor csp;
	final LocalProcessor local;



	private final CountDownLatch ready = new CountDownLatch(1); 
	private AtomicInteger newNodeMsgsRcvd = new AtomicInteger();
	private final AtomicBoolean isNew = new AtomicBoolean();
	private final AtomicBoolean startedReceiving = new AtomicBoolean();

	private volatile Random rnd = new Random(System.currentTimeMillis());

	private final AtomicBoolean degraded = new AtomicBoolean();
	public final AtomicBoolean addingNode = new AtomicBoolean();

	private final ConcurrentHashMap<Integer,Query> querys = new ConcurrentHashMap<>();

	ExecutorService processMessages = Executors.newFixedThreadPool(1);
	ExecutorService processAcks = Executors.newFixedThreadPool(1);
	ExecutorService processSyncs = Executors.newFixedThreadPool(1);
	ExecutorService processQuerys = Executors.newFixedThreadPool(1);

	ExecutorService inOrderViewChanges = Executors.newFixedThreadPool(1);


	public ClusterProcessor(ClusterSignalProcessor csp, LocalProcessor local, String clusterName) throws Exception {
		this.clusterName = clusterName;
		balancer = new ClusterBalancer(this);
		this.local = local;
		this.csp = csp;
		this.start();
	}

	private void start() throws Exception {

		channel=new JChannel(); // use the default config, udp.xml
		channel.setReceiver(this);
		channel.setDiscardOwnMessages(true);
		channel.connect(clusterName);

	}

	public void viewAccepted(final View new_view) {
		final ClusterProcessor self = this;
		inOrderViewChanges.execute(new Runnable() {
			@Override
			public void run() {
				if(group == null){
					group = new_view;
					if(group.size()!=1){
						balancer.addNewNode();
						ConcurrentLinkedQueue<Address> others = new ConcurrentLinkedQueue<>();
						for(Address a:group.getMembers()){
							if(!a.equals(getNodeId())) others.add(a);
						}
						self.others = others;
						System.out.println("I am the new node");
						newNodeMsgsRcvd.addAndGet(others.size());
						isNew.set(true);
						//						viewChangedDegraded();
						//						degradedStarted();
						//						balancer.incrementLocalDegradedCount();
						ready.countDown();
						balancer.timerEnabledOn();
						//						localDegradedFinished();
					}else if(group.size()==1){
						System.out.println("I am the first");
						ready.countDown();
					}
				}else{

					if(group.size()<new_view.size()){
						ConcurrentLinkedQueue<Address> newMembers = new ConcurrentLinkedQueue<>();
						for(Address a: new_view.getMembers()){
							if(!group.containsMember(a))	newMembers.add(a);
						}
						System.out.println("new nodes: " + newMembers);
						balance(newMembers.poll(), new_view.size());
						group = new_view;
						ConcurrentLinkedQueue<Address> others = new ConcurrentLinkedQueue<>();
						for(Address a:group.getMembers()){
							if(!a.equals(getNodeId())) others.add(a);
						}
						self.others = others;
					}
					if(group.size()>new_view.size()){
						Address left = null;
						int k = 0;
						for(Address m: group.getMembers()){
							if(!new_view.containsMember(m)){
								left=m;
								k++;
							}
						}
						if(k>1) System.out.println("Signals were lost :(");
						System.out.println("left node: " + left);
						group = new_view;
						ConcurrentLinkedQueue<Address> others = new ConcurrentLinkedQueue<>();
						for(Address a:group.getMembers()){
							if(!a.equals(getNodeId())) others.add(a);
						}
						self.others = others;

						storeAndBackup(left);
					}

					//					viewChangedDegraded();


					balancer.decrementLocalDegradedCount();
					//					balancer.decreaseLocalDegradedCount();
					balancer.timerEnabledOn();
					if(group.size()==1)	balancer.timerEnabledOff();
				}

				System.out.println("** view: " + new_view);

			}
		});



	}


	private void storeAndBackup(Address left) {
		balancer.incrementLocalDegradedCount();
		degradedStarted();
		balancer.nodeLeft(left);
		System.out.println("store and backup" + left);

	}

	public void receive(Message msg) {

		System.out.println("se recibe de: " + msg.getSrc() + ": " + msg.getObject());
		final Address src = msg.getSrc();
		final ClusterMessage m = (ClusterMessage) msg.getObject();
		if(m instanceof SyncMessage){
			processSyncs.execute(new Runnable() {

				@Override
				public void run() {
					try {
						ready.await();
					} catch (InterruptedException e1) {
						System.out.println("node was not ready to receive messages");
						e1.printStackTrace();
					}

					if(m instanceof NotDegradedMessage){
						balancer.decreaseDegradedCount();
					}else if(m instanceof DegradedMessage){
						degradedStarted();
					}
				}
			});
		}else if(m instanceof AckMessage){
			processAcks.execute(new Runnable() {

				@Override
				public void run() {

					try {
						ready.await();
					} catch (InterruptedException e1) {
						System.out.println("node was not ready to receive messages");
						e1.printStackTrace();
					}

					if(m instanceof ForwardAckMessage){
						balancer.forwardedSignals(((ForwardAckMessage) m).getId());
					}else if(m instanceof BackupAckMessage){
						BackupAckMessage message = (BackupAckMessage) m;
						balancer.backedupSignals(message.getFrom(), message.getId());
					}else if(m instanceof NewNodeForwardAckMessage){
						balancer.newNodeForwardSignalsReceived(src,((NewNodeForwardAckMessage) m).getId());
					}else if(m instanceof ForgetBackupAckMessage){
						balancer.requestForgetReceived(src, ((ForgetBackupAckMessage)m).getId());
					}else if(m instanceof QueryResponseMessage){
						QueryResponseMessage message = (QueryResponseMessage) m;
						addResult(message.getId(), message.getResult());
						System.out.println("id query recibida: " + message.getId());
					}

				}
			});
		}else if(m instanceof QueryMessage){
			processQuerys.execute(new Runnable() {

				@Override
				public void run() {
					QueryRequestMessage message = (QueryRequestMessage) m;
					Result r = local.findSimilarTo(message.getSignal());
					try {
						send(message.generateResponse(getNodeId(), r),message.getFrom());
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			});
		}else{
			processMessages.execute(new Runnable() {

				@Override
				public void run() {
					try {
						ready.await();
					} catch (InterruptedException e1) {
						System.out.println("node was not ready to receive messages");
						e1.printStackTrace();
					}

					if(m instanceof ForwardMessage){
						local.add(((ForwardMessage) m).getSignals());
						//						csp.receivedSignals.addAndGet(((ForwardMessage) m).getSignals().size());
						balancer.share(((ForwardMessage) m).getSignals());
						try {
							send(((ForwardMessage) m).generateAck(),src);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}else if(m instanceof BackupMessage){
						BackupMessage message = (BackupMessage) m;
						try {
							balancer.backupSignals(message.getFrom(), message.getSignals());
							send(message.generateAck(getNodeId()), src);
						} catch (Exception e) {
							e.printStackTrace();
						}
					}else if(m instanceof NewNodeForwardMessage){
						if(startedReceiving.compareAndSet(false, true)){
							balancer.incrementLocalDegradedCount();
						}
						NewNodeForwardMessage aux = (NewNodeForwardMessage) m;
						try {
							balancer.backupSignals(aux.getFrom(), aux.getToBu());
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						local.add(aux.getToFw());
						balancer.share(aux.getToFw());
						try {
							send(aux.generateAck(),aux.getFrom());
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						System.out.println(newNodeMsgsRcvd.get());
						if(newNodeMsgsRcvd.decrementAndGet()==0){
							setOld();

							//							balancer.decreaseLocalDegradedCount();
							balancer.decrementLocalDegradedCount();
						}
					}else if(m instanceof ForgetBackupMessage){
						ForgetBackupMessage f = (ForgetBackupMessage) m;
						balancer.forgetRequested(f.getFrom(), f.getSignals());
						try {
							send(f.generateAck(getNodeId()),f.getFrom());
						} catch (Exception e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}

				}
			});
		}

	}

	public void add(Signal s){
		if(rnd.nextFloat()<1.01/group.size()){
			local.add(s);
			balancer.share(s);
		}else{
			balancer.forward(s);
		}
	}


	public void exit(){
		channel.disconnect();
		channel.close();
	}

	private void send(Message msg) throws Exception{
		System.out.println("se envía a:" + msg.getDest() + "cont: " + msg.getObject());
		channel.send(msg);
	}

	public void send(ClusterMessage message, Address to) throws Exception{
		Message msg = new Message(to, message);
		send(msg);

	}

	public void sendDegradedFinished(Address a) throws Exception{
		Message msg = new Message(a, new NotDegradedMessage(getNodeId()));
		send(msg);
	}

	public void sendDegradedStarted(Address a) throws Exception{
		Message msg = new Message(a, new DegradedMessage(getNodeId()));
		send(msg);
	}

	public void sendQueryRequest(List<Address> tos, Query q) throws Exception{
		for(Address to:tos){
			System.out.println("id query enviada:" + q.getId());
			Message msg = new Message(to, new QueryRequestMessage(q.getId(), getNodeId(),q.getSignal() ));
			send(msg);
		}
	}


	public void balance(Address address, int size){
		System.out.println("agregando nodo:" + address);
		balancer.addNewNode();
		balancer.incrementLocalDegradedCount();
		degradedStarted();
		try {
			send(balancer.newNodeForwardSignals(size),address);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public int backupSignals(){
		return balancer.getBackups();
	}

	public String getNodeIdName(){
		return getNodeId().toString();
	}

	//	public void viewChangedDegraded(){
	//		this.degraded.set(true);
	//		balancer.setDegradedCount(group.size());
	//	}

	public void degradedStarted(){
		System.out.println("tamaño: " + group.size());
		if(balancer.isAlreadyDegraded()) return;
		System.out.println("se degrada en un grupo de: " + group.size());
		this.setDegraded(true);
		balancer.setDegradedCount(group.size());
		try {
			for(Address a: group){
				sendDegradedStarted(a);
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public void degradedFinished(){
		this.setDegraded(false);
	}

	public void localDegradedFinished(){
		try {
			for(Address a: group){
				sendDegradedFinished(a);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public boolean isDegraded(){
		synchronized(degraded){
			return degraded.get();
		}
	}
	
	public boolean systemDeg(){
		return isNew() || (isDegraded() && others!=null) || addingNode.get() ;
	}
	
	public Address getOther(){
		if(others==null) return null;
		float aux = rnd.nextFloat()*others.size();
		int k = 1;
		Address ret = null;
		for(Address a: others){
			if(k++>aux){
				ret = a;
				break;
			}
		}
		if(others.size()>0 && ret == null){
			return getOther();
		}else{
			return ret;
		}
	}

	public void remove(List<Signal> signals){
		local.remove(signals);
	}

	public boolean isNew(){
		return isNew.get();
	}

	public void setOld(){
		isNew.set(false);
	}

	public List<Address> getOthers(){
		if(others==null) return null;
		return Lists.newLinkedList(others);
	}

	public Address getNodeId() {
		return channel.getAddress();
	}

	public List<Result> findSimilarTo(Signal signal) {
		LinkedList<Result> results = new LinkedList<>();
		boolean errors = true;
		while(errors){
			try {
				while(isDegraded() && getOthers()!=null && getOthers().size()>0){
//					synchronized(degraded){
					degraded.wait();
//					}

				}
				List<Address> to = getOthers();
				if(to == null) return results;
				Query q = new Query(to.size(), signal);
				putQuery(q);
				sendQueryRequest(to, q);
				System.out.println("id query creada:" + q.getId());
				q.await();
				removeQuery(q.getId());
				if(q.hasErrors()){
					errors = true;
				}else{
					results.addAll(q.getResults());
					errors = false;
				}
			} catch (Exception e) {

			}
		}
		return results;

	}

	public void putQuery(Query q){
		synchronized (querys) {
			querys.put(q.getId(),q);
		}
	}
	public Query removeQuery(int id){
		synchronized (querys) {
			return querys.remove(id);
		}
	}

	public void invalidateQuerys(){
		synchronized (querys) {
			List<Integer> l = new LinkedList<>();
			for(Query q: querys.values()){
				q.errorDetected();
				l.add(q.getId());
			}
			for(int id: l){
				querys.remove(id);
			}
		}
	}

	public void addResult(int id, Result r){
		synchronized (querys) {
			Query q = querys.get(id);
			if(q!=null)	q.addResult(r);
		}
	}
	
	public void setDegraded(boolean value){
		synchronized(degraded){
		degraded.set(value);
		if(value == true){
			invalidateQuerys();
		}else{
			degraded.notifyAll();
		}
		}
	}
}
