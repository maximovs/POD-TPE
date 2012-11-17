package ar.edu.itba.pod.legajo51071.impl;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;

import ar.edu.itba.pod.legajo51071.SampleServer;
import ar.edu.itba.pod.legajo51071.api.SPNode;
import ar.edu.itba.pod.legajo51071.api.SignalProcessor;

/**
 * Example showing how to use SideBySideTesting.
 * To run, this class needs a server that has exported the API
 * and has setup an RMI registry on port 20000.
 * @see SampleServer
 */
public class SideBySideExampleTester extends SideBySideTester {

	@Override
	protected SignalProcessor init() throws Exception {
		Registry reg = LocateRegistry.getRegistry(20000);
		SignalProcessor	sp = (SignalProcessor) reg.lookup("SignalProcessor");
		SPNode node = (SPNode) reg.lookup("SPNode");
		node.exit();
		return sp;
	}

}
