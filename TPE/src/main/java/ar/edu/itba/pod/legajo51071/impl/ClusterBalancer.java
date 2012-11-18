package ar.edu.itba.pod.legajo51071.impl;

import java.util.LinkedList;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.concurrent.GuardedBy;

import org.jgroups.Address;

import com.google.common.collect.Lists;

import ar.edu.itba.pod.legajo51071.api.Signal;
import ar.edu.itba.pod.legajo51071.impl.msg.BackupMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForgetBackupMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.ForwardMessage;
import ar.edu.itba.pod.legajo51071.impl.msg.NewNodeForwardMessage;

public class ClusterBalancer {
	private static final int MAX_TO_SHARE = 500;
	private static final int MAX_ATTEMPTS = 3;
	final ClusterProcessor clusterProc;
	private ConcurrentHashMap<Address, ConcurrentLinkedQueue<Signal>> backedUpTo = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Address, ConcurrentLinkedQueue<Signal>> backups = new ConcurrentHashMap<>();
	
	@GuardedBy("this")	private CountDownLatch degradedCount;
	@GuardedBy("this")	private Thread syncWaiter;
	@GuardedBy("this")	private AtomicBoolean notDegradedSet = new AtomicBoolean(false);
	@GuardedBy("this")	private AtomicInteger localDegradedCount = new AtomicInteger();
	
	private final Semaphore addingNewNode = new Semaphore(1);
	
	private ConcurrentHashMap<Integer, ConcurrentHashMap<Address,List<Signal>>> toRemoveBackedUpToFws = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, ConcurrentHashMap<Address,List<Signal>>> toRemoveBackedUpToBus = new ConcurrentHashMap<>();
	private ConcurrentHashMap<Integer, ConcurrentHashMap<Address,List<Signal>>> toForget = new ConcurrentHashMap<>();
	private AtomicInteger toRemoveBackedUpToId = new AtomicInteger();
	
	private AtomicInteger backupsCount = new AtomicInteger();
	private AtomicInteger backuptosCount = new AtomicInteger();
	private AtomicInteger shareId = new AtomicInteger();
	private ConcurrentLinkedQueue<Signal> toShare = new ConcurrentLinkedQueue<>();
	private ConcurrentHashMap<Integer, List<Signal>> shares = new ConcurrentHashMap<>();
	//forwards
	private AtomicInteger forwardId = new AtomicInteger();
	private ConcurrentLinkedQueue<Signal> toForward = new ConcurrentLinkedQueue<>();
	private ConcurrentHashMap<Integer, List<Signal>> forwards = new ConcurrentHashMap<>();
	private Timer timer = new Timer();
	
	private AtomicBoolean timerEnabled = new AtomicBoolean();

	public ClusterBalancer(ClusterProcessor clusterProce) {
		this.clusterProc = clusterProce;
		final ClusterBalancer balancer = this;
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
//				System.out.println("falses: " + (clusterProc.isNew()?"is new ":"") + (clusterProc.isDegraded()?"":"clusterProc not degraded") + (toForward.isEmpty() && toShare.isEmpty() && forwards.isEmpty() && shares.isEmpty()?"":"Not everything Empty")  + (toRemoveBackedUpToBus.isEmpty()?"":"toRemoveBus not empty ") + (toRemoveBackedUpToFws.isEmpty()?"":"toRemoveFws: not empty ") + (notDegradedSet.get()?"notDegradedWasSet ":"") + (localDegradedCount.get()!=0?"localDegCount: " + localDegradedCount.get():""));
				System.out.println(localDegradedCount + "deg countdown" + (degradedCount!=null?degradedCount.getCount():""));
				if(!balancer.updateDegradedStatus()){
					if(timerEnabled.get()){
						if(clusterProc.isNew()) return;
						manageForwards();
						manageBackups();
					}
				}

			}
		}, 0, 5000);
		timer.schedule(new TimerTask() {

			@Override
			public void run() {
				System.out.println("dio: " + (!clusterProc.isNew() && clusterProc.isDegraded() && toForward.isEmpty() && toShare.isEmpty() && forwards.isEmpty() && shares.isEmpty() && toRemoveBackedUpToBus.isEmpty() && toRemoveBackedUpToFws.isEmpty() && localDegradedCount.equals(0) && notDegradedSet.equals(false)));
				System.out.println("is new: " + clusterProc.isNew() + ", degraded: " + clusterProc.isDegraded() + ", toRemoveBus: " + (toRemoveBackedUpToBus.isEmpty()?"empty":"not empty") + ", toRemoveFws: " + (toRemoveBackedUpToFws.isEmpty()?"empty":"not empty") + "notDegradedSet: " + notDegradedSet.get() + " localDegCount: " + localDegradedCount.get());
				System.out.println("falses: " + (clusterProc.isNew()?"is new ":"") + (clusterProc.isDegraded()?"":"clusterProc not degraded") + (toForward.isEmpty() && toShare.isEmpty() && forwards.isEmpty() && shares.isEmpty()?"":"Not everything Empty")  + (toRemoveBackedUpToBus.isEmpty()?"":"toRemoveBus not empty ") + (toRemoveBackedUpToFws.isEmpty()?"":"toRemoveFws: not empty ") + (notDegradedSet.get()?"notDegradedWasSet ":"") + (localDegradedCount.get()!=0?"localDegCount: " + localDegradedCount.get():""));
				System.out.println("backups de: " + backups.keySet() + "en total: " + backupsCount);
				System.out.println("backuped to:" + backedUpTo.keySet() + "en total: " + backuptosCount);
//				System.out.println("total de señales procesadas: " + clusterProc.csp.receivedSignals);
				System.out.println("to forward: " + toForward.size());
				System.out.println("forwarded: " + forwards.size());
				System.out.println("toShare: " + toShare.size());
//				System.out.println("get other:" + clusterProc.getOther() + "me: " + clusterProc.getNodeId());
			}
		}, 25000,25000);
	}


	private synchronized boolean updateDegradedStatus(){
		if((!clusterProc.isNew()) && clusterProc.isDegraded() && toForward.isEmpty() && toShare.isEmpty() && forwards.isEmpty() && shares.isEmpty() && toRemoveBackedUpToBus.isEmpty() && toRemoveBackedUpToFws.isEmpty() && localDegradedCount.get()==0 && notDegradedSet.compareAndSet(false, true)){
			decreaseLocalDegradedCount();
			System.out.println("se disminuye la cuenta");
			return true;
		}else{
			return false;
		}
	}
	
	private void manageForwards(){
		if(clusterProc.getOthers() == null || clusterProc.getOthers().size() == 0) return;
		List<Signal> toForwardCopy = Lists.newLinkedList(toForward);
		int from = 0;
		int others = clusterProc.getOthers().size();
		int ammount = toForwardCopy.size()%others!=0?toForwardCopy.size()/others+1:toForwardCopy.size()/others;
		ammount = ammount>MAX_TO_SHARE?MAX_TO_SHARE:ammount;
		while(from<toForwardCopy.size()){
			int to = from + ammount;
			to = to>toForwardCopy.size()?toForwardCopy.size():to;
			int id = forwardId.getAndIncrement();
			int attempt=0;
			boolean sent=false;
			while(attempt++<3 && !sent){
				try {
					forwardSignals(clusterProc.getOther(),Lists.newLinkedList(toForwardCopy.subList(from, to)),id);
					sent=true;
				} catch (Exception e) {
					List<Signal> l = forwards.get(id);
					toForward.addAll(l);
					forwards.remove(id);
				}
			}
			if(!sent) System.out.println("puede que un nodo se haya caído");
			from = to;
		}
	}

	private void forwardSignals(Address to, List<Signal> signals, int id) throws Exception{
		ForwardMessage msg = new ForwardMessage(signals,id);
		forwards.put(id, signals);
		toForward.removeAll(signals);
		clusterProc.send(msg, to);
		incrementLocalDegradedCount();
	}

	public void forwardedSignals(int id){
		int fs = forwards.remove(id).size();
		decrementLocalDegradedCount();
		//		System.out.println("forwarded :" + fs);
	}

	private void manageBackups(){
		if(clusterProc.getOthers() == null || clusterProc.getOthers().size() == 0) return;
		List<Signal> toShareCopy = Lists.newLinkedList(toShare);
		int from = 0;
		int others = clusterProc.getOthers().size();
		int ammount = toShareCopy.size()%others!=0?toShareCopy.size()/others+1:toShareCopy.size()/others;
		ammount = ammount>MAX_TO_SHARE?MAX_TO_SHARE:ammount;
		while(from<toShareCopy.size()){
			int to = from + ammount;
			to = to>toShareCopy.size()?toShareCopy.size():to;
			//			System.out.println("se envian para backup: " + (to - from));
			int id = shareId.getAndIncrement();
			int attempt=0;
			boolean sent=false;
			while(attempt++<3 && !sent){
				try {
					backupSignalsRequest(clusterProc.getNodeId(),clusterProc.getOther(),Lists.newLinkedList(toShareCopy.subList(from, to)),id);
					sent=true;
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
					toShare.addAll(shares.get(id));
					shares.remove(id);
				}
			}
			if(!sent) System.out.println("puede que un nodo se haya caído");
			from = to;
		}
	}

	private void backupSignalsRequest(Address from, Address to, List<Signal> signals, int id) throws Exception{
		
		BackupMessage msg = new BackupMessage(id, from, signals);
		shares.put(id, signals);
		toShare.removeAll(signals);
		clusterProc.send(msg, to);
		incrementLocalDegradedCount();
	}

	public void backedupSignals(Address to, int id){
		List<Signal> signs = shares.remove(id);
		backuptosCount.addAndGet(signs.size());
		backedUpTo.putIfAbsent(to, new ConcurrentLinkedQueue<Signal>());
		backedUpTo.get(to).addAll(signs);
		decrementLocalDegradedCount();
	}

	public void backupSignals(Address from, List<Signal> l) throws Exception{
		incrementLocalDegradedCount();
		backups.putIfAbsent(from, new ConcurrentLinkedQueue<Signal>());
		backups.get(from).addAll(l);
		backupsCount.addAndGet(l.size());
		decrementLocalDegradedCount();
	}
	
	
	/**	Makes two lists of signals to send to the new node in order to balance the amount they have.
	 * It takes the signals from the ones BackedUpTo: 1/(size of the group)
	 * */
	public synchronized NewNodeForwardMessage newNodeForwardSignals(int size){
		incrementLocalDegradedCount();
//		HashMap<Address,ConcurrentLinkedQueue<Signal>> backedUpToCopy = new HashMap<>(backedUpTo);
		if(backedUpTo.size()==0 && !timerEnabled.get()){
			List<Signal> f = Lists.newLinkedList(toShare).subList(0, toShare.size()/2);
			f = new LinkedList<>(f);
			toShare.removeAll(f);
			localRemove(f);
			LinkedList<Signal> aux = new LinkedList<>();
			timerEnabledOn();
			return new NewNodeForwardMessage(clusterProc.getNodeId(),f, aux, -1);
		}
		ConcurrentHashMap<Address,List<Signal>> toFw = new ConcurrentHashMap<>();
		ConcurrentHashMap<Address,List<Signal>> toBu = new ConcurrentHashMap<>();
		LinkedList<Signal> Fw = new LinkedList<>();
		LinkedList<Signal> Bu = new LinkedList<>();
		double pct = 0.99/size;
		int id = toRemoveBackedUpToId.getAndIncrement();
		for(Address a:backedUpTo.keySet()){
			LinkedList<Signal> toFwL = new LinkedList<>();
			LinkedList<Signal> toBuL = new LinkedList<>();
			//obtengo las señales que tengo bueadas en a
			List<Signal> l = Lists.newArrayList(backedUpTo.get(a));
			int ammount =  (int) (l.size()*pct);
			toFwL.addAll(l.subList(0,ammount));
			toBuL.addAll(l.subList(ammount, 2*ammount));
			//borro las señales que saco de a
			backedUpTo.get(a).removeAll(toBuL);
			backedUpTo.get(a).removeAll(toFwL);
			backuptosCount.addAndGet(- toBuL.size() - toFwL.size());
			//las agregi a un "mapa" con las que tengo que borrar en a
			toFw.put(a, toFwL);
			toBu.put(a, toBuL);
			//las agrego junto con todas a las que tengo que borrar.
			Fw.addAll(toFwL);
			Bu.addAll(toBuL);
		}
		toRemoveBackedUpToFws.put(id, toFw);
		for(Address a: toRemoveBackedUpToFws.get(id).keySet()){
			System.out.println(a + "tiene en fws: " + toRemoveBackedUpToFws.get(id).get(a).size());
		}
		toRemoveBackedUpToBus.put(id, toBu);
		for(Address a: toRemoveBackedUpToBus.get(id).keySet()){
			System.out.println(a + "tiene en bus: " + toRemoveBackedUpToBus.get(id).get(a).size());
		}
		return new NewNodeForwardMessage(clusterProc.getNodeId(),Fw, Bu, id);
	}
	
	public void newNodeForwardSignalsReceived(Address node, int id) {
		if(id==-1) {
			decrementLocalDegradedCount();
			return;
		}
		for(Address a: toRemoveBackedUpToBus.get(id).keySet()){
			List<Signal> aux = toRemoveBackedUpToBus.get(id).remove(a);
//			backuptosCount.addAndGet(aux.size());
			backedUpTo.putIfAbsent(node, new ConcurrentLinkedQueue<Signal>());
			backedUpTo.get(node).addAll(aux);
			backuptosCount.addAndGet(aux.size());
			aux.addAll(toRemoveBackedUpToFws.get(id).get(a));
			toForget.putIfAbsent(id, new ConcurrentHashMap<Address, List<Signal>>());
			toForget.get(id).put(a, aux);
			localRemove(toRemoveBackedUpToFws.get(id).remove(a));
			requestForget(a, id, aux);
		}
		toRemoveBackedUpToBus.remove(id);
		toRemoveBackedUpToFws.remove(id);
		decrementLocalDegradedCount();
	}
	
	private void localRemove(List<Signal> signals) {
		incrementLocalDegradedCount();
		clusterProc.remove(signals);
		decrementLocalDegradedCount();
	}
	
	private void requestForget(Address a, int id, List<Signal> signals){
		System.out.println("se pide a " + a + "que olvide " + signals.size() + "señales"); 
		ForgetBackupMessage msg = new ForgetBackupMessage(id, clusterProc.getNodeId(), signals);
		try {
			clusterProc.send(msg, a);
			incrementLocalDegradedCount();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void forgetRequested(Address a, List<Signal> signals){
		incrementLocalDegradedCount();
		backups.get(a).removeAll(signals);
		backupsCount.addAndGet(-signals.size());
		decrementLocalDegradedCount();
	}
	
	public void requestForgetReceived(Address a, int id){
		toForget.get(id).remove(a);
		if(toForget.get(id).isEmpty()){
			toForget.remove(id);
		}
		decrementLocalDegradedCount();
	}
	
	public synchronized void setDegradedCount(int size) {
		final ClusterBalancer cb = this; 
		this.degradedCount = new CountDownLatch(size);
		if(syncWaiter!=null){
			syncWaiter.interrupt();
			try {
				syncWaiter.join();
				syncWaiter = null;
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		syncWaiter = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					cb.degradedCount.await();
					cb.degradedFinished();
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					System.out.println("Se agregó o quitó un nodo en modo degradado");
				}
			}
		});
		syncWaiter.start();
		notDegradedSet.set(false);
	}
	
	public synchronized void decreaseDegradedCount() {
		this.degradedCount.countDown();
		
	}
	
	public synchronized void decreaseLocalDegradedCount() {
		this.decreaseDegradedCount();
		clusterProc.localDegradedFinished();
	}
	
	public void degradedFinished(){
		clusterProc.degradedFinished();
		newNodeAdded();
		notDegradedSet.set(false);
	}

	public void forward(Signal s){
		toForward.add(s);
		setLocalDegraded();
	}


	public void share(Signal s){
		toShare.add(s);
		setLocalDegraded();
	}

	public void share(List<Signal> s){
		toShare.addAll(s);
		setLocalDegraded();
	}

	public int getBackups(){
		return backupsCount.get();
	}
	
	private void setLocalDegraded(){
		if(!isAlreadyDegraded()){
			clusterProc.degradedStarted();
		}
	}
	
	public synchronized void decrementLocalDegradedCount(){
		System.out.println("se decrementa");
		if(localDegradedCount.decrementAndGet() ==0){
//			degradedFinished();
		};
	}
	
	public synchronized void incrementLocalDegradedCount(){
		System.out.println("se incrementa");
		localDegradedCount.incrementAndGet();
		notDegradedSet.set(false);
		setLocalDegraded();
	}
	
	public void timerEnabledOn(){
		timerEnabled.set(true);
	}
	
	public boolean isAlreadyDegraded(){
//		System.out.println("id deg: " + clusterProc.isDegraded()  + ", !notDegSet " + !notDegradedSet.get());
		return clusterProc.isDegraded() && !notDegradedSet.get() && sameCountDown(); 
	}
	
	private boolean sameCountDown(){
		return clusterProc.getOthers()==null?true:clusterProc.getOthers().size()+1 == degradedCount.getCount();
	}
	
	public void addNewNode(){
		try {
			addingNewNode.acquire();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public void newNodeAdded(){
		if(addingNewNode.tryAcquire()==false){
//			decrementLocalDegradedCount();
		};
		addingNewNode.release();
	}
	
	public void nodeLeft(Address left) {
		
	}
}
