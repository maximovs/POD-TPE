package ar.edu.itba.pod.legajo51071.impl;

import static junit.framework.Assert.assertEquals;

import java.rmi.RemoteException;
import java.util.LinkedList;

import org.junit.Before;
import org.junit.Test;

import ar.edu.itba.pod.legajo51071.api.Result;
import ar.edu.itba.pod.legajo51071.api.Signal;
import ar.edu.itba.pod.legajo51071.impl.StandaloneSignalProcessor;
import ar.edu.itba.pod.legajo51071.signal.source.RandomSource;
import ar.edu.itba.pod.legajo51071.signal.source.Source;

public class LocalProcessorTest {
	private LocalProcessor sut;
	private Source src;

	@Before
	public void setup() {
		 sut = new LocalProcessor(4);
		 src = new RandomSource(1);
	}
	
	@Test 
	public void removesCorrectItems() throws RemoteException {
		LinkedList<Signal> l1 = new LinkedList<>();
		LinkedList<Signal> l2 = new LinkedList<>();
		LinkedList<Signal> l3 = new LinkedList<>();
		for(int i= 0 ; i<3000; i++){
			l1.add(src.next());
		}
		for(int i= 0 ; i<4000; i++){
			l2.add(src.next());
		}
		for(int i= 0 ; i<5000; i++){
			l3.add(src.next());
		}
		
		sut.add(l1);
		sut.add(l2);
		sut.add(l3);
		
		assertEquals(sut.size(), l1.size() + l2.size() + l3.size());
		sut.remove(l2);
		assertEquals(sut.size(),  l1.size() + l3.size());
		sut.remove(l1);
		assertEquals(sut.size(), l3.size());
		sut.remove(l1);
		assertEquals(sut.size(), l3.size());
		sut.remove(l3);
		assertEquals(sut.size(), 0);
	}
	
	@Test
	public void startsEmpty() throws RemoteException {
		Signal s1 = src.next();
		Result res = sut.findSimilarTo(s1);

		assertEquals(res.size(), 0);
	}
	
	@Test
	public void addNewSignal() throws RemoteException {
		Signal s1 = src.next();

		sut.add(s1);
		Result res = sut.findSimilarTo(s1);
		
		assertEquals(res.size(), 1);
		assertEquals(res.find(s1), new Result.Item(s1, 0.0));
	}
	
	@Test 
	public void returnsUpTo10Results() throws RemoteException {
		for (int i = 0; i<50; i++) {
			sut.add(src.next());
		}
		Result res = sut.findSimilarTo(src.next());
		
		assertEquals(res.size(), 10);
	}
	
	@Test 
	public void returnsCorrectDeviation() throws RemoteException {
		Signal s1 = src.next();
		Signal s2 = src.next();
		Signal s3 = src.next();
		
		sut.add(s1);
		sut.add(s2);
		
		Result res = sut.findSimilarTo(s3);
		
		assertEquals(res.size(), 2);
		assertEquals(res.find(s1), new Result.Item(s1, s3.findDeviation(s1)));
		assertEquals(res.find(s2), new Result.Item(s2, s3.findDeviation(s2)));
	}
	

	
}
