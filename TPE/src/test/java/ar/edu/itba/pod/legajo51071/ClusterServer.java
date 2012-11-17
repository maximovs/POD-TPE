package ar.edu.itba.pod.legajo51071;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.rmi.AlreadyBoundException;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;

import ar.edu.itba.pod.legajo51071.impl.ClusterSignalProcessor;
import ar.edu.itba.pod.legajo51071.impl.StandaloneSignalProcessor;

/**
 * Simple server that starts an RMI registry and binds the required objects
 */
public class ClusterServer {
	private final int port;

	public ClusterServer(int port) {
		super();
		this.port = port;
	}

	public void start() {
		Registry reg;
		try {
			reg = LocateRegistry.createRegistry(port);
			
			ClusterSignalProcessor impl = new ClusterSignalProcessor(4,9559);
			Remote proxy = UnicastRemoteObject.exportObject(impl, 0);

			// Since the same implementation exports both interfaces, register the same
			// proxy under the two names
			reg.bind("SignalProcessor", proxy);
			reg.bind("SPNode", proxy);
			System.out.println("Server started and listening on port " + port);
			System.out.println("Press <enter> to quit");
			new BufferedReader(new InputStreamReader(System.in)).readLine();
			
		} catch (RemoteException e) {
			System.out.println("Unable to start local server on port " + port);
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Unexpected i/o problem");
			e.printStackTrace();
		} catch (AlreadyBoundException e) {
			System.out.println("Unable to register remote objects. Perhaps another instance is runnign on the same port?");
			e.printStackTrace();
		}
	}


	
	public static void main(String[] args) {
		if (args.length < 1) {
			System.out.println("Command line parameters: SampleServer <port> ");
			return;
		}
		new ClusterServer(Integer.parseInt(args[0])).start();
	}
}
